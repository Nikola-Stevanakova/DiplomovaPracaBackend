package org.example.starter.startup

import com.netgrif.application.engine.startup.AbstractOrderedCommandLineRunner
import org.example.starter.changeloggenerator.ChangelogGenerator

import org.example.starter.databaseconnector.DatabaseService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CustomRunner extends AbstractOrderedCommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(CustomRunner.class)

//    private LiquibaseRunner liquibaseRunner = new LiquibaseRunner()

    @Override
    void run(String... args) throws Exception {
        log.info("Calling custom runner");
        ChangelogGenerator changelogGenerator = new ChangelogGenerator()
        Boolean result = changelogGenerator.generateChangelogFile()
        if(result){
//            spusti update
        }
    }
}
