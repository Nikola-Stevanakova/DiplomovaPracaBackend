package org.example.starter.databaseconnector;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import org.example.starter.databaseconnector.domain.DatabaseSchema;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;

@Service
public class DatabaseService {

    private final static String TABLE_LABEL = "TABLE_NAME";
    private final static String COLUMN_LABEL = "COLUMN_NAME";
    private final static String TYPE_LABEL = "TYPE_NAME";
    private final static String CHANGELOG_TABLE_NAME = "databasechangelog";
    private final static String CHANGELOG_LOCK_TABLE_NAME = "databasechangeloglock";

    private final String databaseUrl = "jdbc:postgresql://localhost:5432/postgres";
    private final String databaseUsername = "admin";
    private final String databasePassword = "password";

    /**
     * The method connects and return database.
     */
    public Database connectToDatabase() {
        try {
            Connection connection = DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);

            return DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        } catch (LiquibaseException | SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * The method creates and returns database connection.
     */
    public Connection getDatabaseConnection() {
        try {
            return DriverManager.getConnection(databaseUrl, databaseUsername, databasePassword);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * The method connects to database and returns DatabaseSchema object.
     */
    public List<DatabaseSchema> getDatabaseSchema() {
        List<DatabaseSchema> databaseSchemaList = new ArrayList<DatabaseSchema>();
        Connection connection = getDatabaseConnection();

        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet dataTables = databaseMetaData.getTables(null, null, null, new String[]{"TABLE"});

            while (dataTables.next()) {
                String tableName = dataTables.getString(TABLE_LABEL);

                if (!tableName.equals(CHANGELOG_TABLE_NAME) && !tableName.equals(CHANGELOG_LOCK_TABLE_NAME)) {
                    DatabaseSchema databaseSchema = new DatabaseSchema();
                    HashMap<String, String> tableNameTypeMap = new HashMap<>();

                    ResultSet dataColumns = databaseMetaData.getColumns(null, null, tableName, null);

                    while (dataColumns.next()) {
                        String columnName = dataColumns.getString(COLUMN_LABEL);
                        String typeName = dataColumns.getString(TYPE_LABEL);

                        tableNameTypeMap.put(columnName, typeName);
                    }
                    databaseSchema.setTableName(tableName);
                    databaseSchema.setColumnNameType(tableNameTypeMap);

                    databaseSchemaList.add(databaseSchema);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return databaseSchemaList;
    }
}
