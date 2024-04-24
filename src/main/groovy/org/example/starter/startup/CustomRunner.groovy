package org.example.starter.startup

import com.netgrif.application.engine.startup.AbstractOrderedCommandLineRunner
import org.example.starter.changeloggenerator.ChangelogGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CustomRunner extends AbstractOrderedCommandLineRunner {

    private static Logger log = LoggerFactory.getLogger(CustomRunner.class)

    private ChangelogGenerator changelogGenerator = new ChangelogGenerator();
//    private LiquibaseRunner liquibaseRunner = new LiquibaseRunner()

    @Override
    void run(String... args) throws Exception {
        log.info("Calling custom runner");

        changelogGenerator.generateChangelogFile()
    }
}
