package org.example.starter.changeloggenerator;

import org.example.starter.databaseconnector.domain.DatabaseSchema;
import org.example.starter.xmlparser.domain.DocumentProcess;

import java.util.List;

public interface IChangelogGenerator {

    void generateChangelogFile();
    void compareDatabaseSchemaAndProcesses(List<DatabaseSchema> databaseSchemaList, List<DocumentProcess> documentProcessList);
}
