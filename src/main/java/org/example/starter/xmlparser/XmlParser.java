package org.example.starter.xmlparser;

import org.example.starter.xmlparser.domain.DocumentProcess;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class XmlParser {

    public List<DocumentProcess> parseXmlFiles () {
        List<DocumentProcess> documentProcessList = new ArrayList<>();
        try {
            File[] files = new File("src/main/resources/petriNets").listFiles();
            JAXBContext jaxbContext = JAXBContext.newInstance(DocumentProcess.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            assert files != null;
            for (File file : files) {
                DocumentProcess documentProcess = (DocumentProcess) jaxbUnmarshaller.unmarshal(file);
                documentProcessList.add(documentProcess);
            }
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        return documentProcessList;
    }
}

