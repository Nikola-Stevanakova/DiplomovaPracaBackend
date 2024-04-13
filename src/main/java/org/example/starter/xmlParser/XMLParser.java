package org.example.starter.xmlParser;

import org.example.starter.xmlParser.domain.Document;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XMLParser {

    public List<Document> parseXML () {
        List<Document> documentList = new ArrayList<>();
        try {
            File[] files = new File("src/main/resources/petriNets").listFiles();

            // Vytvorenie inštancie JAXBContext z balíčka, kde sa nachádzajú vaše triedy
            JAXBContext jaxbContext = JAXBContext.newInstance(Document.class);
            // Vygenerovanie triedy z XML súboru
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            assert files != null;
            for (File file : files) {
                Document document = (Document) jaxbUnmarshaller.unmarshal(file);
                documentList.add(document);
            }
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return documentList;
    }
}

