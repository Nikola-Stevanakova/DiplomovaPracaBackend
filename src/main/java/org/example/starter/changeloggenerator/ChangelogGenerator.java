package org.example.starter.changeloggenerator;

import com.netgrif.application.engine.elastic.service.ElasticCaseService;
import org.example.starter.databaseconnector.domain.DatabaseSchema;
import org.example.starter.databaseconnector.DatabaseService;
import org.example.starter.xmlparser.XmlParser;
import org.example.starter.xmlparser.domain.DataField;
import org.example.starter.xmlparser.domain.DocumentProcess;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChangelogGenerator {

    public static final HashMap<String, String> DATA_TYPES_MAP = new HashMap<>() {{
        put("number", "integer");
        put("text", "varchar(50)");
        put("enumeration", "varchar(50)");
        put("enumeration_map", "varchar(50)");
        put("multichoice", "varchar(255)");
        put("multichoice_map", "varchar(255)");
        put("boolean", "boolean");
        put("date", "date");
        put("dateTime", "timestamp");
        put("file", "varchar(50)");
        put("fileList", "varchar(255)");
        put("user", "varchar(50)");
        put("userList", "varchar(255)");
        put("i18n", "varchar(255)");
        put("taskRef", "varchar(255)");
        put("caseRef", "uuid");
    }};

    public static final HashMap<String, String> CONSTRAINTS_PRIMARY_KEY_ATTRIBUTES = new HashMap<>() {{
        put("nullable", "false");
        put("primaryKey", "true");
    }};

    private final DatabaseService databaseService = new DatabaseService();
    private final XmlParser xmlParser = new XmlParser();
    private final ElasticCaseService elasticCaseService;

    public ChangelogGenerator(ElasticCaseService elasticCaseService) {
        this.elasticCaseService = elasticCaseService;
    }

    /**
     * The method gets and compares the database schema and xml files and runs a function to generate the changeset.
     * The method returns true if the changeset has been generated successfully, otherwise it returns false.
     */
    public boolean generateChangelogFile() {
        List<DocumentProcess> documentProcessList = xmlParser.parseXmlFiles();
        List<DatabaseSchema> databaseSchemaList = databaseService.getDatabaseSchema();
        return checkFilesAndCompareDatabaseSchemaAndProcesses(databaseSchemaList, documentProcessList);
    }

    /**
     * The method checks if the necessary files exist to write the changes.
     * If they exist, the method generates and writes the changeset and returns true, otherwise it returns false.
     *
     * @param databaseSchemaList  list of database tables
     * @param documentProcessList list of processes
     */
    public boolean checkFilesAndCompareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList) {
        if (!checkChangelogFilesContent()) {
            System.out.println("Nepodarilo sa vytvoriť changelog");
            return false;
        }

        return compareDatabaseSchemaAndProcesses(databaseSchemaList, documentProcessList);
    }

    private boolean compareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList){
        try {
            String changesetFileNamePath = "src/main/resources/db/changeset/changelog_changeset.xml";
            File changesetFile = new File(changesetFileNamePath);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document changelogDocument = docBuilder.parse(changesetFile);
            changelogDocument.getDocumentElement().normalize();

//            najdi changelog
            NodeList changelogList = changelogDocument.getElementsByTagName("databaseChangeLog");
            Element changelogElement = (Element) changelogList.item(0);

//            najdi changeset
            Element createTableChangeset = createChangeSetElement(changelogDocument, changelogElement);
            Element updateTableChangeset = createChangeSetElement(changelogDocument, changelogElement);
            Element removeTableChangeset = createChangeSetElement(changelogDocument, changelogElement);

//          zoberiem tabulky z databazy a idem prechadzat procesy a vytvarat tabulky, ktore este neexistuju
            List<String> tableNames = databaseSchemaList.stream().filter(databaseSchema ->
                    !databaseSchema.getTableName().equals("databasechangelog") && !databaseSchema.getTableName().equals("databasechangeloglock")).map(DatabaseSchema::getTableName).collect(Collectors.toList());

            List<String> checkedTables = new ArrayList<>();
            for (DocumentProcess documentProcess : documentProcessList) {
                String documentProcessId = documentProcess.getId();
                if (tableNames.contains(documentProcessId)) {
                    // prejst datove fieldy
                    HashMap<String, String> dataMapDatabaseSchema = databaseSchemaList.stream()
                            .filter(processSchema -> processSchema.getTableName().equals(documentProcessId))
                            .findAny()
                            .map(DatabaseSchema::getColumnNameType)
                            .orElse(new HashMap<>());

//                assertThat(dataList).containsExactlyInAnyOrderElementsOf();
                    HashMap<String, String> dataMapProcess= (HashMap<String, String>) documentProcess.getDataList().stream().filter(dataField -> !dataField.getTagName().equals("button")).collect(Collectors.toMap(DataField::getId, DataField::getTagName));

                    List<String> checkedCols = new ArrayList<>();
                    for(Map.Entry<String, String> data: dataMapProcess.entrySet()) {
//                        skontrolujeme ci to nie je caseref
                        if(data.getValue().equals("caseRef")) {
                            String[] dataSplit = data.getKey().split("_");
                            String secondDocRelation = "_" + dataSplit[dataSplit.length-1];     //ids
                            String secondDocProcessId = data.getKey().substring(0,data.getKey().lastIndexOf(secondDocRelation));    //vehicle

                            List<DataField> secondRelationDataFieldList = documentProcessList.stream().filter(secondDoc ->
                                    secondDoc.getId().equals(secondDocProcessId)).map(DocumentProcess::getDataList).findFirst().get();

                            String firstDocRelationFull = secondRelationDataFieldList.stream().filter(dataField -> dataField.getId().equals(documentProcessId + "_id") || dataField.getId().equals(documentProcessId + "_ids")).findFirst().get().getId();
                            String[] firstRelationSplit = firstDocRelationFull.split("_");
                            String firstDocRelation = "_" + firstRelationSplit[firstRelationSplit.length-1];

                            if(firstDocRelation.equals("_ids") && secondDocRelation.equals("_ids")) {
                                if(!tableNames.contains(documentProcessId+"_"+secondDocProcessId) && !tableNames.contains(secondDocProcessId+"_"+documentProcessId)) {
                                    generateCreateJoiningTableElement(changelogDocument, createTableChangeset, documentProcessId, secondDocProcessId);
                                    checkedTables.add(documentProcessId+"_"+secondDocProcessId);
                                    checkedTables.add(secondDocProcessId+"_"+documentProcessId);
                                }
                            } else if(firstDocRelation.equals("_ids") && secondDocRelation.equals("_id")) {
                                generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, data.getKey(), data.getValue());
                                checkedCols.add(data.getKey());
//                            } else if(firstDocRelation.equals("_id") && secondDocRelation.equals("_ids")) {

                            } else if(firstDocRelation.equals("_id") && secondDocRelation.equals("_id")){
                                generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, data.getKey(), data.getValue());
                                checkedCols.add(data.getKey());
                            }
                        } else {
                            if(dataMapDatabaseSchema.containsKey(data.getKey())){
                                if(!dataMapDatabaseSchema.get(data.getKey()).equals(DATA_TYPES_MAP.get(data.getValue()))){
                                    generateDropColumn(changelogDocument, updateTableChangeset, documentProcessId, data.getKey());
                                    generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, data.getKey(), data.getValue());
                                }
                            } else {
                                generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, data.getKey(), data.getValue());
                            }
                            checkedCols.add(data.getKey());
                        }
                    }
//                    dropcols pre vsetky ktore zostali v databaze
                    for(Map.Entry<String, String> data: dataMapDatabaseSchema.entrySet()) {
                        if(!checkedCols.contains(data.getKey())){
                            generateDropColumn(changelogDocument, updateTableChangeset, documentProcessId, data.getKey());
                        }
                    }
                } else {
                    List<String> joinTables = generateCreateTableElement(changelogDocument, createTableChangeset, documentProcess, documentProcessList, checkedTables);
                    if(!joinTables.isEmpty()){
                        checkedTables.addAll(joinTables);
                    }
                }
                checkedTables.add(documentProcessId);
            }

//            todo: kontrola vztahov

            for (DatabaseSchema databaseSchema : databaseSchemaList) {
                if (!checkedTables.contains(databaseSchema.getTableName())) {
                    generateDropTable(changelogDocument, removeTableChangeset, databaseSchema.getTableName());
                }
            }

            writeContentToXmlFile(changelogDocument, changesetFileNamePath);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void generateDropColumn(Document changelogDocument, Element updateTableChangeset, String documentProcessId, String dataName) {
        Element dropColumnElement = changelogDocument.createElement("dropColumn");
        dropColumnElement.setAttribute("schemaName", "public");
        dropColumnElement.setAttribute("tableName", documentProcessId);

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", dataName);
        }};
        Element columnElement = createElement(changelogDocument,"column", columnAttributes);
        dropColumnElement.appendChild(columnElement);

        updateTableChangeset.appendChild(dropColumnElement);
    }

    private void generateAddColumns(Document changelogDocument, Element updateTableChangeset, String documentProcessId, String dataName, String dataType) {
        Element addColumnElement = changelogDocument.createElement("addColumn");
        addColumnElement.setAttribute("schemaName", "public");
        addColumnElement.setAttribute("tableName", documentProcessId);

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", dataName);
//              TODO: put("type", dataType);
            put("type", DATA_TYPES_MAP.get(dataType));
        }};
        Element columnElement = createElement(changelogDocument,"column", columnAttributes);
        addColumnElement.appendChild(columnElement);

        updateTableChangeset.appendChild(addColumnElement);
    }

    private void generateDropTable(Document changelogDocument, Element removeTableChangeset, String databaseSchemaTableName) {
        Element dropTableElement = changelogDocument.createElement("dropTable");
        dropTableElement.setAttribute("schemaName", "public");
        dropTableElement.setAttribute("tableName", databaseSchemaTableName);

        removeTableChangeset.appendChild(dropTableElement);
    }

    private List<String> generateCreateTableElement(Document changelogDocument, Element changeSetElement, DocumentProcess documentProcess, List<DocumentProcess> documentProcessList, List<String> checkedTables) {
        Element createTableElement = changelogDocument.createElement("createTable");
        createTableElement.setAttribute("tableName", documentProcess.getId());

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", "id");
            put("type", "uuid");
        }};
        Element columnIdElement = createElement(changelogDocument,"column", columnAttributes);
        columnIdElement.appendChild(createElement(changelogDocument,"constraints", CONSTRAINTS_PRIMARY_KEY_ATTRIBUTES));

        createTableElement.appendChild(columnIdElement);

        List<String> joinTables = new ArrayList<>();
        for(DataField dataField : documentProcess.getDataList()) {
            if (!dataField.getTagName().equals("button")) {
                if(dataField.getTagName().equals("caseRef")) {
                    String[] dataSplit = dataField.getId().split("_");
                    String secondDocRelation = "_" + dataSplit[dataSplit.length-1];     //ids
                    String secondDocProcessId = dataField.getId().substring(0,dataField.getId().lastIndexOf(secondDocRelation));    //vehicle

                    List<DataField> secondRelationDataFieldList = documentProcessList.stream().filter(secondDoc ->
                            secondDoc.getId().equals(secondDocProcessId)).map(DocumentProcess::getDataList).findFirst().get();

                    String firstDocRelationFull = secondRelationDataFieldList.stream().filter(df-> df.getId().equals(documentProcess.getId() + "_id") || df.getId().equals(documentProcess.getId() + "_ids")).findFirst().get().getId();
                    String[] firstRelationSplit = firstDocRelationFull.split("_");
                    String firstDocRelation = "_" + firstRelationSplit[firstRelationSplit.length-1];

                    if(firstDocRelation.equals("_ids") && secondDocRelation.equals("_ids")) {
                        if(!checkedTables.contains(documentProcess.getId()+"_"+secondDocProcessId) && !checkedTables.contains(secondDocProcessId+"_"+documentProcess.getId())) {
                            generateCreateJoiningTableElement(changelogDocument, changeSetElement, documentProcess.getId(), secondDocProcessId);
                            joinTables.add(documentProcess.getId()+"_"+secondDocProcessId);
                            joinTables.add(secondDocProcessId+"_"+documentProcess.getId());
                        }
                    } else if(firstDocRelation.equals("_ids") && secondDocRelation.equals("_id")) {
                        HashMap<String, String> attributes = new HashMap<>(){{
                            put("name", dataField.getId());
//                TODO: funkcia ktora vrati type na zaklade tagname
                            put("type", DATA_TYPES_MAP.get(dataField.getTagName()));
                        }};
                        createTableElement.appendChild(createElement(changelogDocument,"column", attributes));
//                    } else if(firstDocRelation.equals("_id") && secondDocRelation.equals("_ids")) {
                    } else if(firstDocRelation.equals("_id") && secondDocRelation.equals("_id")){
                        HashMap<String, String> attributes = new HashMap<>(){{
                            put("name", dataField.getId());
//                TODO: funkcia ktora vrati type na zaklade tagname
                            put("type", DATA_TYPES_MAP.get(dataField.getTagName()));
                        }};
                        createTableElement.appendChild(createElement(changelogDocument,"column", attributes));
                    }
                } else {
                    HashMap<String, String> attributes = new HashMap<>(){{
                        put("name", dataField.getId());
//                TODO: funkcia ktora vrati type na zaklade tagname
                        put("type", DATA_TYPES_MAP.get(dataField.getTagName()));
                    }};
                    createTableElement.appendChild(createElement(changelogDocument,"column", attributes));

                }
            }
        }

        changeSetElement.appendChild(createTableElement);
        return joinTables;
    }

    private void generateCreateJoiningTableElement(Document changelogDocument, Element changeSetElement, String firstProcessId, String secondProcessId ) {
        Element createTableElement = changelogDocument.createElement("createTable");
        createTableElement.setAttribute("tableName", firstProcessId+"_"+secondProcessId);

        Element columnFirstIdElement = createElement(changelogDocument,"column", new HashMap<>() {{
            put("name", firstProcessId+"_id");
            put("type", "uuid");
        }});
        Element columnSecondIdElement = createElement(changelogDocument,"column", new HashMap<>() {{
            put("name", secondProcessId+"_id");
            put("type", "uuid");
        }});

        createTableElement.appendChild(columnFirstIdElement);
        createTableElement.appendChild(columnSecondIdElement);

        changeSetElement.appendChild(createTableElement);
    }

    /**
     * The method creates and adds new changeSet elements to the changelog file, with each new element having a unique ID.
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     * @param changelogElement reference to the root element of the changelog where the new include element is added
     */
    private Element createChangeSetElement(Document changelogDocument, Element changelogElement) {
        String newId = generateId();

        Element changeSetElement = changelogDocument.createElement("changeSet");
        changeSetElement.setAttribute("id", newId);
        changeSetElement.setAttribute("author", "admin");
        changelogElement.appendChild(changeSetElement);
        return changeSetElement;
    }

    /**
     * Method to generate the uniq ID for changeset.
     */
    private String generateId(){
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    private Element createElement(Document changelogDocument, String tagName, HashMap<String, String> attributes) {
        Element element = changelogDocument.createElement(tagName);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            element.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return element;
    }

    /**
     * The method checks the contents of the two XML files and returns true if the contents are correct.
     */
    private boolean checkChangelogFilesContent() {
        // TODO: properties
        String changeLogFileNamePath = "src/main/resources/db/changelog.xml";
        String changesetFileNamePath = "src/main/resources/db/changeset/changelog_changeset.xml";

        boolean changeLogFileResult = checkFileContent(changeLogFileNamePath, true);

        if (changeLogFileResult) {
            // TODO: properties
            return checkFileContent(changesetFileNamePath, false);
        }
        return false;
    }

    /**
     * The method is used to ensure that the given file exists and its contents are correct.
     * The method returns true if the file exists and its contents were successfully checked.
     * If it returns false, there was a problem creating the file or an error checking its contents.
     *
     * @param filePath          path to the file
     * @param mainChangeLogFile argument determines whether it is the main changelog file or a changelog file that contains a set of changes
     */
    private boolean checkFileContent(String filePath, boolean mainChangeLogFile) {
        File newFile = new File(filePath);

        if (!newFile.exists()) {
            try {
                boolean created = newFile.createNewFile();

                if (created) {
                    return checkContentOfChangelogFile(newFile, filePath, mainChangeLogFile);
                } else {
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            return checkContentOfChangelogFile(newFile, filePath, mainChangeLogFile);
        }
        return false;
    }

    /**
     * This method ensures that the contents of the changelog file are initialized correctly depending on whether the file
     * is the main changelog file or not. Returns true if updated successfully, false otherwise.
     *
     * @param changelogFile     file with set of changes
     * @param filePath          path to file with set of changes
     * @param mainChangeLogFile argument determines whether it is the main changelog file or a changelog file that contains a set of changes
     */
    private boolean checkContentOfChangelogFile(File changelogFile, String filePath, boolean mainChangeLogFile) {
        if (mainChangeLogFile && changelogFile.length() > 0) {
            return updateRootElement(changelogFile, filePath);
        }
        return createNewRootElement(filePath, mainChangeLogFile);
    }

    /**
     * The method checks whether there is a changelog element in the file and subsequently checks the existence of include elements.
     * If there is no include element of the file "changelogs/changelog_changeset.xml", it will add it. Finally, it saves the changes back to the file.
     * The method returns true if the file was modified successfully.
     *
     * @param changelogFile     file with set of changes
     * @param filePath          path to file with set of changes
     */
    private boolean updateRootElement(File changelogFile, String filePath) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document changelogDocument = docBuilder.parse(changelogFile);
            changelogDocument.getDocumentElement().normalize();

            NodeList changelogList = changelogDocument.getElementsByTagName("databaseChangeLog");
            if (changelogList.getLength() == 0) {
                return createNewRootElement(filePath, true);
            }

            Element changelogElement = (Element) changelogList.item(0);

            NodeList includeList = changelogElement.getElementsByTagName("include");
            if (includeList.getLength() == 0) {
                createIncludeElement(changelogDocument, changelogElement);
                writeContentToXmlFile(changelogDocument, filePath);
                return true;
            }

            boolean changelogChangesetIncluded = false;

            for (int i = 0; i < includeList.getLength(); i++) {
                Node includeNode = includeList.item(i);
                if (includeNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element includeElement = (Element) includeNode;
                    String fileAttribute = includeElement.getAttribute("file");

                    if (fileAttribute.equals("changeset/changelog_changeset.xml")) {
                        changelogChangesetIncluded = true;
                        break;
                    }
                }
            }

            if (!changelogChangesetIncluded) {
                createIncludeElement(changelogDocument, changelogElement);
                writeContentToXmlFile(changelogDocument, filePath);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * The method initializes a new changelog file and adds an include element if it is the main changelog file.
     * If it is a changeset file, it adds a changeset element and generates a unique id for it.
     *
     * @param filePath              path to file with set of changes
     * @param mainChangeLogFile     argument determines whether it is the main changelog file or a changelog file that contains a set of changes
     */
    private boolean createNewRootElement(String filePath, boolean mainChangeLogFile) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document changelogDocument = docBuilder.newDocument();
            Element rootElement = changelogDocument.createElement("databaseChangeLog");
            rootElement.setAttribute("xmlns", "http://www.liquibase.org/xml/ns/dbchangelog");
            rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
            rootElement.setAttribute("xsi:schemaLocation", "http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd");

            if (mainChangeLogFile) {
                createIncludeElement(changelogDocument, rootElement);
            }
            changelogDocument.appendChild(rootElement);
            writeContentToXmlFile(changelogDocument, filePath);

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * The method creates and adds an include element to an existing changelog document.
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     * @param changelogElement reference to the root element of the changelog where the new include element is added
     */
    private void createIncludeElement(Document changelogDocument, Element changelogElement) {
        Element newIncludeElement = changelogDocument.createElement("include");
        newIncludeElement.setAttribute("file", "changeset/changelog_changeset.xml");
        newIncludeElement.setAttribute("relativeToChangelogFile", "true");
        changelogElement.appendChild(newIncludeElement);
    }

    /**
     * The method transforms the object with a set of changes into xml format and saves it to the file.
     *
     * @param changelogDocument the object containing the set of changes that need to be made
     * @param filePath          path to the file containing the set of changes
     */
    private static void writeContentToXmlFile(Document changelogDocument, String filePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");   //ukoncovaci tag
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(changelogDocument);
            FileWriter writer = new FileWriter(filePath);
            StreamResult result = new StreamResult(writer);

            transformer.transform(source, result);

            System.out.println("Generated changelog XML.");
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
