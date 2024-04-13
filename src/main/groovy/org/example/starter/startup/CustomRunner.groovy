package org.example.starter.startup

import com.netgrif.application.engine.startup.AbstractOrderedCommandLineRunner
import org.example.starter.xmlParser.XMLParser
import org.example.starter.xmlParser.domain.Document
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CustomRunner extends AbstractOrderedCommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(CustomRunner.class)

    XMLParser xmlParser = new XMLParser()

    @Override
    void run(String... args) throws Exception {
        log.info("Calling custom runner");

        List<Document> documentList = xmlParser.parseXML()


    }
}
