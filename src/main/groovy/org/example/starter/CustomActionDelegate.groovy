package org.example.starter

import com.netgrif.application.engine.petrinet.domain.dataset.logic.action.ActionDelegate
import com.netgrif.application.engine.workflow.domain.Case

import org.example.starter.databaseconnector.DatabaseService
import org.springframework.stereotype.Component

import java.sql.Connection
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
            statement.addBatch();
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void createPersonProcessInstanceInTable(Connection connection, Case processInstance) {
        System.out.println("Idem robiť insert")
        def sql = "INSERT INTO " + processInstance.processIdentifier + " (id, telephone_number,  " +
                "sequence_number, document_file, date_of_registration, request_submitted, nationality_enumeration, vehicle_header) " + "VALUES(?,?,?,?,?,?,?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, processInstance.stringId)
            statement.setString(2, processInstance.getDataField("telephone_number").toString())

            statement.setInt(3, processInstance.getDataField("sequence_number").value as int)
            statement.setString(4, processInstance.getDataField("document_file").toString())
            statement.setDate(5, new java.sql.Date((processInstance.getDataField("date_of_registration").value as java.util.Date).getTime()))
            statement.setBoolean(6, processInstance.getDataField("request_submitted").value as boolean)
            statement.setString(7, processInstance.getDataField("nationality_enumeration").value.toString())
            statement.setString(8, processInstance.getDataField("vehicle_header").toString())

            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }

        Case vehicleCase = workflowService.findOne(processInstance.dataSet["vehicle_ids"].value[0].toString())
        def sqlVehicle = "INSERT INTO vehicle (id, person_id, registration_date_time) " + "VALUES(?,?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sqlVehicle)) {
            statement.setString(1, processInstance.dataSet["vehicle_ids"].value[0].toString())
            statement.setString(2, processInstance.stringId)
            statement.setTimestamp(3, new java.sql.Timestamp((vehicleCase.getDataField("registration_date_time").value as java.util.Date).getTime()))
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