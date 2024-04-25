package org.example.starter.changeloggenerator;

import org.apache.xpath.operations.Bool;
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
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class ChangelogGenerator {

    private final DatabaseService databaseService = new DatabaseService();
    private final XmlParser xmlParser = new XmlParser();

    public boolean generateChangelogFile() {
        List<DocumentProcess> documentProcessList = xmlParser.parseXmlFiles();
        List<DatabaseSchema> databaseSchemaList = databaseService.getDatabaseSchema();
        return compareDatabaseSchemaAndProcesses(databaseSchemaList, documentProcessList);
    }

    public boolean compareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList) {
        if (!checkChangelogFileState()) {
            System.out.println("Nepodarilo sa vytvoriť changelog");
            return false;
        }

        List<String> tableNames = databaseSchemaList.stream().map(DatabaseSchema::getTableName).collect(Collectors.toList());

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
                List<String> dataListDocumentProcess = documentProcess.getDataList().stream().map(DataField::getId).collect(Collectors.toList());


            } else {
                //TODO: create Table changelog

            }
        }

//        for (DatabaseSchema databaseSchema : databaseSchemaList) {
//            List<String> processIds = documentProcessList.stream().map(DocumentProcess::getId).collect(Collectors.toList());
//            if (processIds.contains(databaseSchema.getTableName())) {
//
//            } else {
//                //TODO: drop table
//            }
//        }
        return true;
    }

    /**
     * The method deletes the contents of the file or creates the file if file does not exist.
     */
    private boolean checkChangelogFileState() {
        // TODO: properties
        String changeLogFileNamePath = "src/main/resources/db/changelog.xml";
        Boolean changeLogFileResult = getFileOrCreateIfNotExist(changeLogFileNamePath);

        if (changeLogFileResult){
            // TODO: properties
            String changesetFileNamePath = "src/main/resources/db/changeset/changelog_changeset.xml";
            return getFileOrCreateIfNotExist(changesetFileNamePath);
        }
        return false;
    }

    private Boolean getFileOrCreateIfNotExist(String filePath) {
        File newFile = new File(filePath);

        if (!newFile.exists()) {
            try {
                boolean created = newFile.createNewFile();

                if (created) {
                    System.out.println("Súbor " + filePath + " bol úspešne vytvorený.");
                    return checkContentOfChangelogFile(newFile, filePath);
                } else {
                    System.out.println("Nepodarilo sa vytvoriť súbor " + filePath + ".");
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Súbor " + filePath + " už existuje.");
            return checkContentOfChangelogFile(newFile, filePath);
        }
        return false;
    }

    /**
     * The method check if file with set of changes contains root element ChangeLog and if
     * not, creates a new changelog item.
     *
     * @param changelogFile file with set of changes
     */
    private Boolean checkContentOfChangelogFile(File changelogFile, String filePath) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            if (changelogFile.length() == 0) {
                createRootElement(docBuilder, filePath);
            } else {
                Document changelogDocument = docBuilder.parse(changelogFile);
                Element rootElement = changelogDocument.getDocumentElement();
                createRootElement(docBuilder, filePath);
//                if (rootElement.getNodeName().equals("databaseChangeLog") && rootElement.getElementsByTagName()) {
//                   TODO: dorobit to aby cjangelog obsahoval include file... vtedz sa nevygeneruje novy file... ak includes neobsahuje, vygeneruje sa novy file
                    // este tam treba skontrovat ci ten include file je changeset
//                }
//                    new FileWriter(filePath, false).close();
//
//                    createRootElement(docBuilder, filePath);
//                } else {
//                    NodeList childElements = rootElement.getElementsByTagName("changeSet");
//                    if (childElements != null) {
////                       List<Node> nodes = childElements.stream().map(child->child.getNodeName().equals("changeSet")).collect(Collectors.toList());
////                       for (Node node: nodes) {
////                           rootElement.removeChild(node);
////                       }
//                    }
//                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * The method creates and append root element changelog to xml file.
     *
     * @param docBuilder class to create Document instance for XML document
     */
    private void createRootElement(DocumentBuilder docBuilder, String filePath) {
        Document changelogDocument = docBuilder.newDocument();
        Element rootElement = changelogDocument.createElement("databaseChangeLog");
        rootElement.setAttribute("xmlns", "http://www.liquibase.org/xml/ns/dbchangelog");
        rootElement.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        rootElement.setAttribute("xsi:schemaLocation", "http://www.liquibase.org/xml/ns/dbchangelog https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd");
        changelogDocument.appendChild(rootElement);
        writeContentToXmlFile(changelogDocument, filePath);
    }

    /**
     * The method to create a set of changes file of all processes and save it to the xml file.
     *
     * @param documentProcessList list of process objects transformed from xml files
     */
    public void generateChangesets(List<DocumentProcess> documentProcessList) {
//        String changesetFileNamePath = liquibaseProperties.getChangeLogChangeSetFile();
        String changesetFileNamePath = "src/main/resources/db/changelog_changeset.xml";
        Document changesetDoc;
        Element rootElement;

        try {
            File changesetFile = new File(changesetFileNamePath);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            if (changesetFile.length() == 0) {
                changesetDoc = docBuilder.newDocument();
                rootElement = changesetDoc.createElement("changeSet");
                changesetDoc.appendChild(rootElement);
            } else {
                changesetDoc = docBuilder.parse(changesetFile);
                rootElement = changesetDoc.getDocumentElement();
            }

            // Nastavi sa id changesetu
            // TODO: dorobit automaticke generovanie jedinecneho id changesetu
            rootElement.setAttribute("author", "admin");
            rootElement.setAttribute("id", "changeset_0");

            for (DocumentProcess documentProcess : documentProcessList) {
                Element generateChangeSetElement = generateChangeSetElement(changesetDoc, documentProcess);
                rootElement.appendChild(generateChangeSetElement);
            }

            writeContentToXmlFile(changesetDoc, changesetFileNamePath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * The method creates a change based on one process and returns this change in the form of an Element class,
     * which is then transformed and stored in the generated changeset.
     *
     * @param changesetDoc    the object containing the set of changes that need to be made
     * @param documentProcess the process object containing data fields of process
     */
    private static Element generateChangeSetElement(Document changesetDoc, DocumentProcess documentProcess) {
//        tu bude logika na to ci sa generuje createTable alebo drop table
        // TODO: dorobit generovanie jedinecneho id tabulky
        String tableName = documentProcess.getId() + "_table";

        Element createTableElement = changesetDoc.createElement("createTable");
        createTableElement.setAttribute("tableName", tableName);

        List<DataField> dataList = documentProcess.getDataList();
        for (DataField data : dataList) {
            Element columnElement = changesetDoc.createElement("column");
            columnElement.setAttribute("name", data.getId());
            // TODO: type upravit
            columnElement.setAttribute("type", "varchar(255)");
            createTableElement.appendChild(columnElement);
        }
        return createTableElement;
    }


    /**
     * The method transforms the object with a set of changes into xml format and saves it to the file.
     *
     * @param changelogDocument     the object containing the set of changes that need to be made
     * @param filePath path to the file containing the set of changes
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
