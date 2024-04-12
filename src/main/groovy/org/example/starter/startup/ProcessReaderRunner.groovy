package org.example.starter.startup

import com.netgrif.application.engine.startup.AbstractOrderedCommandLineRunner
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class ProcessReaderRunner extends AbstractOrderedCommandLineRunner {

    @Override
    void run(String... args) throws Exception {
        log.info("Process reading started.")
        readXmlFiles()
    }

    private void readXmlFiles() {

    }
}
