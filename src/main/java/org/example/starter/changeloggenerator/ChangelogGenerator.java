package org.example.starter.changeloggenerator;

import org.example.starter.databaseconnector.domain.DatabaseSchema;
import org.example.starter.databaseconnector.DatabaseService;
import org.example.starter.xmlparser.XmlParser;
import org.example.starter.xmlparser.domain.DataField;
import org.example.starter.xmlparser.domain.DocumentProcess;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChangelogGenerator implements IChangelogGenerator {

    private final DatabaseService databaseService = new DatabaseService();
    private final XmlParser xmlParser = new XmlParser();


    public void generateChangelogFile() {
        List<DocumentProcess> documentProcessList = xmlParser.parseXmlFiles();
        List<DatabaseSchema> databaseSchemaList = databaseService.getDatabaseSchema();
        compareDatabaseSchemaAndProcesses(databaseSchemaList, documentProcessList);
    }

    public void compareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList) {
        String name = "";

        for (DatabaseSchema databaseSchema : databaseSchemaList) {
            List<String> processIds = documentProcessList.stream().map(DocumentProcess::getId).collect(Collectors.toList());
            if (processIds.contains(databaseSchema.getTableName())) {

            } else {
                //TODO: drop table
            }
        }

    }

    /**
     * The method to create a set of changes file of all processes and save it to the xml file.
     *
     * @param documentProcessList list of process objects transformed from xml files
     */
    public static void generateChangesets(List<DocumentProcess> documentProcessList) {
//        String changesetFileNamePath = liquibaseProperties.getChangeLogChangeSetFile();
        String changesetFileNamePath = "src/main/resources/db/changelog_v0.xml";
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

            writeChangeSetToXml(changesetDoc, changesetFileNamePath);

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
     * @param changesetDoc          the object containing the set of changes that need to be made
     * @param changesetFileNamePath path to the file containing the set of changes
     */
    private static void writeChangeSetToXml(Document changesetDoc, String changesetFileNamePath) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(changesetDoc);
            FileWriter writer = new FileWriter(changesetFileNamePath);
            StreamResult result = new StreamResult(writer);

            transformer.transform(source, result);

            System.out.println("Generated Changeset XML.");
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
