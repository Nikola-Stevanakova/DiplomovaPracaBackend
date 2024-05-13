package org.example.starter.liquibase;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.example.starter.databaseconnector.DatabaseService;

import org.springframework.stereotype.Component;

@Component
public class LiquibaseService {

    private final DatabaseService databaseRunner = new DatabaseService();

    /**
     * The method gets a connection to the database, makes changes on the database using Liquibase
     * and the set of changes defined in the changelog file.
     */
    public void runLiquibase() {
        String changelogFilePath = "/db/changelog.xml";

        try {
            Database database = databaseRunner.connectToDatabase();
            Liquibase liquibase = new Liquibase(changelogFilePath, new ClassLoaderResourceAccessor(), database);

            liquibase.update(new Contexts(), new LabelExpression());
        } catch (LiquibaseException e) {
            e.printStackTrace();
        }
    }


}