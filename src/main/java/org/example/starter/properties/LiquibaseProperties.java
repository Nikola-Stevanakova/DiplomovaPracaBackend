package org.example.starter.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.liquibase")
public class LiquibaseProperties {

    @Value("${spring.liquibase.change-log}")
    private String changeLogFile;
    @Value("${spring.liquibase.changelog.changeset}")
    private String changeLogChangeSetFile;
}
