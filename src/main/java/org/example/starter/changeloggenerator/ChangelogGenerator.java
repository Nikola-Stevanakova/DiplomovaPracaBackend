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
        put("number", "numeric");
        put("text", "varchar(255)");
        put("enumeration", "varchar(255)");
        put("enumeration_map", "varchar(255)");
        put("multichoice", "varchar(255) ARRAY");
        put("multichoice_map", "varchar(255) ARRAY");
        put("boolean", "boolean");
        put("date", "date");
        put("dateTime", "timestamp");
        put("file", "varchar(255)");
        put("fileList", "varchar(255) ARRAY");
        put("user", "varchar(255)");
        put("userList", "varchar(255) ARRAY");
        put("i18n", "varchar(50)");
        put("taskRef", "varchar(255) ARRAY");
        put("caseRef", "char(24)");
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

    /**
     * The method
     */
    private boolean compareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList) {
        try {
            String changesetFileNamePath = "src/main/resources/db/changeset/changelog_changeset.xml";
            File changesetFile = new File(changesetFileNamePath);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            Document changelogDocument = docBuilder.parse(changesetFile);
            changelogDocument.getDocumentElement().normalize();

            NodeList changelogList = changelogDocument.getElementsByTagName("databaseChangeLog");
            Element changelogElement = (Element) changelogList.item(0);

            Element createTableChangeset = createChangeSetElement(changelogDocument, changelogElement);
            Element updateTableChangeset = createChangeSetElement(changelogDocument, changelogElement);
            Element removeTableChangeset = createChangeSetElement(changelogDocument, changelogElement);
            Element addForeignKeysTableChangeset = createChangeSetElement(changelogDocument, changelogElement);

            List<String> tableNames = databaseSchemaList.stream().filter(databaseSchema ->
                    !databaseSchema.getTableName().equals("databasechangelog") && !databaseSchema.getTableName().equals("databasechangeloglock")).map(DatabaseSchema::getTableName).collect(Collectors.toList());

            for (DocumentProcess documentProcess : documentProcessList) {
                String documentProcessId = documentProcess.getId();
                if (tableNames.contains(documentProcessId)) {
                    HashMap<String, String> dataMapDatabaseSchema = databaseSchemaList.stream()
                            .filter(processSchema -> processSchema.getTableName().equals(documentProcessId))
                            .findAny()
                            .map(DatabaseSchema::getColumnNameType)
                            .orElse(new HashMap<>());

                    List<DataField> processDataFieldList = documentProcess.getDataList().stream().filter(dataField -> !dataField.getTagName().equals("button")).collect(Collectors.toList());

                    List<String> checkedCols = new ArrayList<>(List.of("id"));
                    for (DataField processDataField : processDataFieldList) {
                        String columnName = processDataField.getId();  // data.getKey();
                        String columnType = processDataField.getTagName(); // data.getValue();
                        List<String> allowedNets = processDataField.getAllowedNets();

                        if (columnType.equals("caseRef")) {
                            String[] dataSplit = columnName.split("_");
                            String processInRelationRelationType = "_" + dataSplit[dataSplit.length - 1];     //_ids/id

                            for (String allowedNet : allowedNets) {
                                List<DataField> processInRelationDataFieldList = documentProcessList.stream().filter(processInRelation ->
                                        processInRelation.getId().equals(allowedNet)).map(DocumentProcess::getDataList).findFirst().get();
                                String relationTypeFromAllowedNetDataFieldId = processInRelationDataFieldList.stream().filter(df -> df.getAllowedNets() != null && df.getAllowedNets().contains(documentProcessId)).findFirst().map(DataField::getId).get();
                                String[] relationTypeFromAllowedNetSplit = relationTypeFromAllowedNetDataFieldId.split("_");
                                String relationTypeFromAllowedNetProcess = "_" + relationTypeFromAllowedNetSplit[relationTypeFromAllowedNetSplit.length - 1];

                                if (relationTypeFromAllowedNetProcess.equals("_ids") && processInRelationRelationType.equals("_ids")) {
                                    if (!tableNames.contains(documentProcessId + "_" + allowedNet) && !tableNames.contains(allowedNet + "_" + documentProcessId)) {
                                        generateCreateJoiningTableElement(changelogDocument, createTableChangeset, documentProcessId, allowedNet);
                                        generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId + "_" + allowedNet, documentProcessId);
                                        generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId + "_" + allowedNet, allowedNet);
                                        tableNames.add(documentProcessId + "_" + allowedNet);
                                        tableNames.add(allowedNet + "_" + documentProcessId);
                                    }
                                } else if (processInRelationRelationType.equals("_id")) {
//                                    foreign key
                                    if (!dataMapDatabaseSchema.containsKey(allowedNet + "_id")) {
                                        generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, allowedNet + "_id", columnType);
                                        generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId, allowedNet);
                                    }
                                    checkedCols.add(allowedNet + "_id");
                                }
                            }
                        } else {
                            if (dataMapDatabaseSchema.containsKey(columnName)) {
                                if (!DATA_TYPES_MAP.get(columnType).contains(dataMapDatabaseSchema.get(columnName))) {
                                    generateDropColumn(changelogDocument, updateTableChangeset, documentProcessId, columnName);
                                    generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, columnName, columnType);
                                }
                            } else {
                                generateAddColumns(changelogDocument, updateTableChangeset, documentProcessId, columnName, columnType);
                            }
                            checkedCols.add(columnName);
                        }
                    }

                    for (Map.Entry<String, String> data : dataMapDatabaseSchema.entrySet()) {
                        if (!checkedCols.contains(data.getKey())) {
                            generateDropColumn(changelogDocument, updateTableChangeset, documentProcessId, data.getKey());
                        }
                    }

                } else {
                    List<String> joinTables = generateCreateTableElement(changelogDocument, createTableChangeset, addForeignKeysTableChangeset, documentProcess, documentProcessList, tableNames);
                    if (!joinTables.isEmpty()) {
                        tableNames.addAll(joinTables);
                    }
                }
                tableNames.add(documentProcessId);
            }
            for (DatabaseSchema databaseSchema : databaseSchemaList) {
                if (!tableNames.contains(databaseSchema.getTableName())) {
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

    /**
     * The method
     */
    private void generateDropColumn(Document changelogDocument, Element updateTableChangeset, String documentProcessId, String dataName) {
        Element dropColumnElement = changelogDocument.createElement("dropColumn");
        dropColumnElement.setAttribute("schemaName", "public");
        dropColumnElement.setAttribute("tableName", documentProcessId);

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", dataName);
        }};
        Element columnElement = createElement(changelogDocument, "column", columnAttributes);
        dropColumnElement.appendChild(columnElement);

        updateTableChangeset.appendChild(dropColumnElement);
    }

    /**
     * The method
     */
    private void generateAddColumns(Document changelogDocument, Element updateTableChangeset, String documentProcessId, String dataName, String dataType) {
        Element addColumnElement = changelogDocument.createElement("addColumn");
        addColumnElement.setAttribute("schemaName", "public");
        addColumnElement.setAttribute("tableName", documentProcessId);

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", dataName);
            put("type", DATA_TYPES_MAP.get(dataType));
        }};
        Element columnElement = createElement(changelogDocument, "column", columnAttributes);
        addColumnElement.appendChild(columnElement);

        updateTableChangeset.appendChild(addColumnElement);
    }

    /**
     * The method
     */
    private void generateDropTable(Document changelogDocument, Element removeTableChangeset, String databaseSchemaTableName) {
        Element dropTableElement = changelogDocument.createElement("dropTable");
        dropTableElement.setAttribute("schemaName", "public");
        dropTableElement.setAttribute("tableName", databaseSchemaTableName);

        removeTableChangeset.appendChild(dropTableElement);
    }

    /**
     * The method
     */
    private List<String> generateCreateTableElement(Document changelogDocument, Element changeSetElement, Element addForeignKeysTableChangeset, DocumentProcess documentProcess, List<DocumentProcess> documentProcessList, List<String> tableNames) {
        String documentProcessId = documentProcess.getId();
        Element createTableElement = changelogDocument.createElement("createTable");
        createTableElement.setAttribute("tableName", documentProcessId);

        HashMap<String, String> columnAttributes = new HashMap<>() {{
            put("name", "id");
            put("type", "char(24)");
        }};
        Element columnIdElement = createElement(changelogDocument, "column", columnAttributes);
        columnIdElement.appendChild(createElement(changelogDocument, "constraints", CONSTRAINTS_PRIMARY_KEY_ATTRIBUTES));

        createTableElement.appendChild(columnIdElement);

        List<String> joinTables = new ArrayList<>();
        for (DataField dataField : documentProcess.getDataList()) {
            String columnName = dataField.getId();  // data.getKey();
            String columnType = dataField.getTagName(); // data.getValue();
            List<String> allowedNets = dataField.getAllowedNets();

            if (!columnType.equals("button")) {
                if (columnType.equals("caseRef")) {
                    String[] dataSplit = columnName.split("_");
                    String processInRelationRelationType = "_" + dataSplit[dataSplit.length - 1];     //_ids/id

                    for (String allowedNet : allowedNets) {
                        List<DataField> processInRelationDataFieldList = documentProcessList.stream().filter(processInRelation -> processInRelation.getId().equals(allowedNet)).map(DocumentProcess::getDataList).findFirst().get();
                        String relationTypeFromAllowedNetDataFieldId = processInRelationDataFieldList.stream().filter(df -> df.getAllowedNets() != null && df.getAllowedNets().contains(documentProcessId)).findFirst().map(DataField::getId).get();
                        String[] relationTypeFromAllowedNetSplit = relationTypeFromAllowedNetDataFieldId.split("_");
                        String relationTypeFromAllowedNetProcess = "_" + relationTypeFromAllowedNetSplit[relationTypeFromAllowedNetSplit.length - 1];

                        if (relationTypeFromAllowedNetProcess.equals("_ids") && processInRelationRelationType.equals("_ids")) {
                            if (!tableNames.contains(documentProcessId + "_" + allowedNet) && !tableNames.contains(allowedNet + "_" + documentProcessId)) {
                                generateCreateJoiningTableElement(changelogDocument, changeSetElement, documentProcessId, allowedNet);
                                generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId + "_" + allowedNet, documentProcessId);
                                generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId + "_" + allowedNet, allowedNet);
                                joinTables.add(documentProcessId + "_" + allowedNet);
                                joinTables.add(allowedNet + "_" + documentProcessId);
                            }
                        } else if (processInRelationRelationType.equals("_id")) {
//                                    foreign key
//                            createTableElement.appendChild(generateForeignKeyColumn(changelogDocument, allowedNet));
                            HashMap<String, String> attributes = new HashMap<>() {{
                                put("name", allowedNet + "_id");
                                put("type", DATA_TYPES_MAP.get(columnType));
                            }};
                            createTableElement.appendChild(createElement(changelogDocument, "column", attributes));
                            generateForeignKeyToExistingTable(changelogDocument, addForeignKeysTableChangeset, documentProcessId, allowedNet);
                        }
                    }
                } else {
                    HashMap<String, String> attributes = new HashMap<>() {{
                        put("name", columnName);
                        put("type", DATA_TYPES_MAP.get(columnType));
                    }};
                    createTableElement.appendChild(createElement(changelogDocument, "column", attributes));

                }
            }
        }

        changeSetElement.appendChild(createTableElement);
        return joinTables;
    }

    /**
     * The method
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     */
    private void generateForeignKeyToExistingTable(Document changelogDocument, Element addForeignKeysTableChangeset, String baseTableName, String referencedColumnName) {
        Element addForeignKeyElement = changelogDocument.createElement("addForeignKeyConstraint");
        addForeignKeyElement.setAttribute("baseTableName", baseTableName);
        addForeignKeyElement.setAttribute("baseColumnNames", referencedColumnName + "_id");
        addForeignKeyElement.setAttribute("constraintName", referencedColumnName + "_id");
        addForeignKeyElement.setAttribute("referencedTableName", referencedColumnName);
        addForeignKeyElement.setAttribute("referencedColumnNames", "id");
        addForeignKeysTableChangeset.appendChild(addForeignKeyElement);
    }

    /**
     * The method
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     */
    private void generateCreateJoiningTableElement(Document changelogDocument, Element changeSetElement, String firstProcessId, String secondProcessId) {
        Element createTableElement = changelogDocument.createElement("createTable");
        createTableElement.setAttribute("tableName", firstProcessId + "_" + secondProcessId);

        createTableElement.appendChild(generateForeignKeyColumn(changelogDocument, firstProcessId));
        createTableElement.appendChild(generateForeignKeyColumn(changelogDocument, secondProcessId));

        changeSetElement.appendChild(createTableElement);
    }

    /**
     * The method
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     */
    private Element generateForeignKeyColumn(Document changelogDocument, String processId) {
        return createColumnIdElement(changelogDocument, processId);
    }

    /**
     * The method
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     */
    private Element createColumnIdElement(Document changelogDocument, String processId) {
        return createElement(changelogDocument, "column", new HashMap<>() {{
            put("name", processId + "_id");
            put("type", "char(24)");
        }});
    }

    /**
     * The method creates and adds new changeSet elements to the changelog file, with each new element having a unique ID.
     *
     * @param changelogDocument reference to the document to which the new include element is to be added
     * @param changelogElement  reference to the root element of the changelog where the new include element is added
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
    private String generateId() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * The method
     */
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
        String changeLogFileNamePath = "src/main/resources/db/changelog.xml";
        String changesetFileNamePath = "src/main/resources/db/changeset/changelog_changeset.xml";

        boolean changeLogFileResult = checkFileContent(changeLogFileNamePath, true);

        if (changeLogFileResult) {
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
     * @param changelogFile file with set of changes
     * @param filePath      path to file with set of changes
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
     * @param filePath          path to file with set of changes
     * @param mainChangeLogFile argument determines whether it is the main changelog file or a changelog file that contains a set of changes
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
     * @param changelogElement  reference to the root element of the changelog where the new include element is added
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
