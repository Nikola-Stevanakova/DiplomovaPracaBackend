package org.example.starter.databaseconnector.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Data
@NoArgsConstructor
public class DatabaseSchema {

    private String tableName;
    private HashMap<String, String> columnNameType;

}
