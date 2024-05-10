package org.example.starter

import com.netgrif.application.engine.petrinet.domain.dataset.logic.action.ActionDelegate
import com.netgrif.application.engine.workflow.domain.Case
import liquibase.database.Database
import org.bson.types.ObjectId
import org.example.starter.databaseconnector.DatabaseService
import org.springframework.stereotype.Component

import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

@Component
class CustomActionDelegate extends ActionDelegate {

    DatabaseService databaseService = new DatabaseService();

    void updateOrInsert() {
        Connection connection = databaseService.getDatabaseConnection()

        if (checkIfInstanceExistsInTable(connection, useCase.processIdentifier, useCase.stringId)) {
            updateDataOfPersonProcessInstance(connection, useCase.processIdentifier)
        } else {
            createPersonProcessInstanceInTable(connection, useCase)
        }
    }

    void updateDataOfPersonProcessInstance(Connection connection, String tableName) {
        System.out.println("Idem robiť update")

        def sql = "INSERT INTO " + tableName + " ( name) " + "VALUES(?)"

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
//            statement.setString(2, useCase.dataSet["name"].value.toString())
            statement.addBatch();
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void createPersonProcessInstanceInTable(Connection connection , Case processInstance) {
        System.out.println("Idem robiť insert")

//        def sql = "INSERT INTO" + processInstance.processIdentifier + "(id, title, enumeration_select, enumeration_map_select, multichoice_select, multichoice_map_select, count_select,  " +
//                "boolean_field, date_select, date_time_select, file_select, file_list_select, user_select, user_list_select, vehicle_header) " + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

        def sql = "INSERT INTO " + processInstance.processIdentifier + " (id, title, count_select,  " +
                "boolean_field, date_select, date_time_select, file_select, vehicle_header) " + "VALUES(?,?,?,?,?,?,?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, processInstance.stringId)
            statement.setString(2, processInstance.getDataField("title").toString())

//            statement.setString(3, processInstance.getDataField("enumeration_select").toString())
//            statement.setString(4, processInstance.getDataField("enumeration_map_select").toString())
//            statement.setString(5, processInstance.getDataField("multichoice_select").toString())
//            statement.setString(6, processInstance.getDataField("multichoice_map_select").toString())

            statement.setInt(3, processInstance.getDataField("count_select").value as int)
            statement.setBoolean(4, processInstance.getDataField("boolean_field").value as boolean)
            statement.setDate(5, new java.sql.Date((processInstance.getDataField("date_select").value as java.util.Date).getTime()))
            statement.setTimestamp(6, new java.sql.Timestamp((processInstance.getDataField("date_time_select").value as java.util.Date).getTime()))
            statement.setString(7, processInstance.getDataField("file_select").toString())

//            statement.setArray(12, processInstance.getDataField("file_list_select").value.namesPaths as List)
//            statement.setString(13, processInstance.getDataField("user_select").toString())
            statement.setString(8, processInstance.getDataField("vehicle_header").toString())
            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    static boolean checkIfInstanceExistsInTable(Connection connection, String tableName, String stringId) {
        def getsql = "SELECT * FROM " + tableName + " WHERE id = ?"

        try (PreparedStatement statement = connection.prepareStatement(getsql)) {
            statement.setString(1, stringId)

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    System.out.println("Instance of process already exists in postgresql")
                    return true
                }
            }
        } catch (SQLException e) {
            e.printStackTrace()
        }
        return false
    }

}