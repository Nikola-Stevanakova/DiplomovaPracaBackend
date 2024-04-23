package org.example.starter.liquibase;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.example.starter.xmlParser.XMLParser;
import org.example.starter.xmlParser.domain.DataField;
import org.example.starter.xmlParser.domain.DocumentProcess;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class LiquibaseRunner {

    public void performDatabaseUpdate() {
        List<DocumentProcess> documentProcessList = new XMLParser().parseXML();
        // 1. Generovanie changeset XML suboru
//        generateChangesets(documentProcessList);

        // 2. Spustenie update databazy
        runLiquibase();
    }

    public static void generateChangesets(List<DocumentProcess> documentProcessList) {
        try {
            // TODO: prehodit na property value
            String changesetFileName = "src/main/resources/db/changelog_v0.xml";
            File changesetFile = new File(changesetFileName);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document changesetDoc;
            Element rootElement;

            // Ak subor existuje, ale nemá root changeSet element, tak sa vytvori, inak sa vytiahne
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

            writeChangeSetToXml(changesetDoc, changesetFileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Element generateChangeSetElement(Document changesetDoc, DocumentProcess documentProcess) {
        // TODO: dorobit generovanie jedinecneho id tabulky
        String tableName = documentProcess.getId() + "_table";

        // Vytvori sa element na vytvorenie tabulky a nastavi sa jej tag tableName
        Element createTableElement = changesetDoc.createElement("createTable");
        createTableElement.setAttribute("tableName", tableName);

        // Pridanie stlpcov tabulky, kde jeden stlpec obsahuje tagy name a type
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

    private static void writeChangeSetToXml(Document changesetDoc, String changesetFileName) {
        try {
            // Zapisanie changeSetu do suboru xml
            TransformerFactory transformerFactory = TransformerFactory.newInstance();

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            DOMSource source = new DOMSource(changesetDoc);
            FileWriter writer = new FileWriter(changesetFileName);
            StreamResult result = new StreamResult(writer);

            transformer.transform(source, result);

            System.out.println("Generated Changeset XML.");
        } catch (IOException | TransformerException e) {
            throw new RuntimeException(e);
        }
    }


    public static void runLiquibase() {
        try {
            String changelogFilePath = "/db/changelog.xml";
            Connection connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "admin", "password");
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(changelogFilePath, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
        } catch (LiquibaseException | SQLException e ) {
            e.printStackTrace();
        }
    }
}