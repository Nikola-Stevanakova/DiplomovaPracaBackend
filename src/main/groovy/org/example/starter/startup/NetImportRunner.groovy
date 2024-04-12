package org.example.starter.startup

import com.netgrif.application.engine.petrinet.domain.PetriNet
import com.netgrif.application.engine.petrinet.domain.VersionType
import com.netgrif.application.engine.petrinet.service.interfaces.IPetriNetService
import com.netgrif.application.engine.startup.AbstractOrderedCommandLineRunner
import com.netgrif.application.engine.startup.ImportHelper
import groovy.util.logging.Slf4j
import org.example.starter.processes.PetriNetsEnum
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class NetImportRunner extends AbstractOrderedCommandLineRunner {

    @Autowired
    private IPetriNetService petriNetService

    @Autowired
    private ImportHelper importHelper

    @Override
    void run(String... args) throws Exception {
        log.info("Importing ZWP models.")
        PetriNetsEnum.values().each {
            upsert(it)
        }
    }
    private void upsert(PetriNetsEnum petriNetsEnum) {
        if (petriNetService.getNewestVersionByIdentifier(petriNetsEnum.NET_IDENTIFIER) != null) {
            log.info("${petriNetsEnum.NET_NAME} has already been imported.")
        } else {
            Optional<PetriNet> petriNet = importHelper.createNet(petriNetsEnum.NET_FILE, VersionType.MAJOR)
            if (!petriNet.isPresent()) {
                log.error("Importing ${petriNetsEnum.NET_NAME} failed.")
            }
        }
    }
}