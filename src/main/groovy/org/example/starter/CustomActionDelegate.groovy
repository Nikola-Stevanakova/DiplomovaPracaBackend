package org.example.starter

import com.netgrif.application.engine.petrinet.domain.dataset.FileListFieldValue
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

    void removeFromDatabase() {
        Connection connection = databaseService.getDatabaseConnection()

        if (checkIfInstanceExistsInTable(connection, useCase.processIdentifier, useCase.stringId)) {
            removeProcessInstance(connection, useCase.processIdentifier)
        }
    }

    void updateOrInsert() {
        Connection connection = databaseService.getDatabaseConnection()

        if (checkIfInstanceExistsInTable(connection, useCase.processIdentifier, useCase.stringId)) {
            updateDataOfPersonProcessInstance(connection, useCase.processIdentifier)
        } else {
            createPersonProcessInstanceInTable(connection, useCase)
        }
    }

    void removeProcessInstance(Connection connection, Case processInstance) {
        System.out.println("Idem robiť delete")

        def deleteSQL = "DELETE FROM " + processInstance.processIdentifier + " (id) VALUES(?)"

        try (PreparedStatement statement = connection.prepareStatement(deleteSQL)) {
            statement.setString(1, processInstance.stringId)

            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void createPersonProcessInstanceInTable(Connection connection, Case processInstance) {
        System.out.println("Idem robiť insert osoby")
        def sql = "INSERT INTO " + processInstance.processIdentifier + " (id, telephone_number,  " +
                "sequence_number, document_file, date_of_registration, request_submitted, vehicle_header, vehicle_forms, person_name, nationality_enumeration) VALUES(?,?,?,?,?,?,?,?,?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, processInstance.stringId)
            statement.setString(2, processInstance.getDataField("telephone_number").toString())

            statement.setInt(3, processInstance.getDataField("sequence_number").value as int)
            statement.setString(4, processInstance.getDataField("document_file").toString())
            statement.setDate(5, new java.sql.Date((processInstance.getDataField("date_of_registration").value as java.util.Date).getTime()))
            statement.setBoolean(6, processInstance.getDataField("request_submitted").value as boolean)

            statement.setString(7, processInstance.getDataField("vehicle_header").toString())

            ArrayList<String> arrayList = processInstance.dataSet["vehicle_forms"].value
            java.sql.Array array = connection.createArrayOf("VARCHAR", arrayList.toArray());
            statement.setArray(8, array)

            statement.setString(9, processInstance.getDataField("person_name").toString())
            statement.setString(10, processInstance.getDataField("nationality_enumeration").value.toString())

            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Idem robiť insert vozidla")
        Case vehicleCase = workflowService.findOne(processInstance.dataSet["vehicle_ids"].value[0].toString())
        def sqlVehicle = "INSERT INTO vehicle (id, person_id, registration_date_time, seat_count, color, file_list) VALUES(?,?,?,?,?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sqlVehicle)) {
            statement.setString(1, processInstance.dataSet["vehicle_ids"].value[0].toString())
            statement.setString(2, processInstance.stringId)
            statement.setTimestamp(3, new java.sql.Timestamp((vehicleCase.getDataField("registration_date_time").value as java.util.Date).getTime()))
            statement.setInt(4, vehicleCase.getDataField("seat_count").value as int)
            statement.setString(5, vehicleCase.getDataField("color").value.toString())

            FileListFieldValue fileListFieldValues = (FileListFieldValue) vehicleCase.dataSet["file_list"].value
            ArrayList<String> arraylistOfStrings = fileListFieldValues.namesPaths.stream().map(fp -> fp.path).collect(java.util.stream.Collectors.toList());

            java.sql.Array array = connection.createArrayOf("VARCHAR", arraylistOfStrings.toArray());
            statement.setArray(6, array)

            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Idem robiť insert vztahu")
        def sql2 = "INSERT INTO person_vehicle (vehicle_id, person_id) VALUES(?,?)"

        try (PreparedStatement statement = connection.prepareStatement(sql2)) {

            statement.setString(1, processInstance.dataSet["vehicle_ids"].value[0].toString())
            statement.setString(2, processInstance.stringId)

            statement.addBatch()
            statement.executeUpdate()

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

    void updateDataOfPersonProcessInstance(Connection connection, Case processInstance) {
        def sql2 = "UPDATE " + processInstance.processIdentifier + " SET person_name = ? WHERE id = ?"

        try (PreparedStatement statement = connection.prepareStatement(sql2)) {

            statement.setString(1, processInstance.dataSet["person_name"].value[0].toString())
            statement.setString(2, processInstance.stringId)

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