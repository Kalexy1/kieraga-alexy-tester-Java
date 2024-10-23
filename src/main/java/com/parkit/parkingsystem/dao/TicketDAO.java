package com.parkit.parkingsystem.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;

public class TicketDAO {

    private static final Logger logger = LogManager.getLogger(TicketDAO.class);
    private DataBaseConfig dataBaseConfig;

    public TicketDAO(DataBaseConfig dataBaseConfig) {
        this.dataBaseConfig = dataBaseConfig;
    }

    /**
     * Saves a ticket to the database.
     *
     * @param ticket The ticket to save
     * @return True if the ticket was successfully saved, false otherwise
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public boolean saveTicket(Ticket ticket) throws SQLException, ClassNotFoundException {
        if (ticket == null || ticket.getParkingSpot() == null || ticket.getVehicleRegNumber() == null) {
            logger.error("Ticket or required fields are null. Cannot save ticket.");
            return false;
        }

        try (Connection con = dataBaseConfig.getConnection(); 
             PreparedStatement ps = con.prepareStatement(DBConstants.SAVE_TICKET)) {
            
            con.setAutoCommit(false);
            
            ps.setInt(1, ticket.getParkingSpot().getId());
            ps.setString(2, ticket.getVehicleRegNumber());
            ps.setDouble(3, ticket.getPrice());
            ps.setTimestamp(4, Timestamp.valueOf(ticket.getInTime()));
            ps.setTimestamp(5, ticket.getOutTime() != null ? Timestamp.valueOf(ticket.getOutTime()) : null);

            int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected > 0) {
                con.commit();
                logger.info("Ticket saved successfully for vehicle registration number '{}'.", ticket.getVehicleRegNumber());
                return true;
            } else {
                con.rollback();
                logger.warn("No rows affected while saving ticket for vehicle registration number '{}'.", ticket.getVehicleRegNumber());
                return false;
            }

        } catch (SQLException ex) {
            logger.error("SQL error saving ticket for vehicle registration number '{}': {}", ticket.getVehicleRegNumber(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Retrieves a ticket based on the vehicle registration number.
     *
     * @param vehicleRegNumber The vehicle registration number
     * @return The ticket if found, null otherwise
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public Ticket getTicket(String vehicleRegNumber) throws SQLException, ClassNotFoundException {
        if (vehicleRegNumber == null || vehicleRegNumber.trim().isEmpty()) {
            logger.error("Vehicle registration number is null or empty.");
            return null;
        }

        String sql = DBConstants.GET_TICKET;
        Ticket ticket = null;

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, vehicleRegNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParkingSpot parkingSpot = new ParkingSpot(
                        rs.getInt("PARKING_NUMBER"),
                        ParkingType.valueOf(rs.getString("TYPE")),
                        rs.getBoolean("AVAILABLE")
                    );
                    ticket = new Ticket();
                    ticket.setParkingSpot(parkingSpot);
                    ticket.setId(rs.getInt("ID"));
                    ticket.setVehicleRegNumber(vehicleRegNumber);
                    ticket.setPrice(rs.getDouble("PRICE"));
                    ticket.setInTime(rs.getTimestamp("IN_TIME").toLocalDateTime());

                    Timestamp outTime = rs.getTimestamp("OUT_TIME");
                    ticket.setOutTime(outTime != null ? outTime.toLocalDateTime() : null);
                }
            }

        } catch (SQLException ex) {
            logger.error("SQL error fetching ticket for vehicle registration number '{}': {}", vehicleRegNumber, ex.getMessage(), ex);
            throw ex;
        }

        if (ticket == null) {
            logger.warn("No ticket found for vehicle registration number '{}'", vehicleRegNumber);
        }

        return ticket;
    }

    /**
     * Updates a ticket in the database.
     *
     * @param ticket The ticket to update
     * @return True if the ticket was successfully updated, false otherwise
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public boolean updateTicket(Ticket ticket) throws SQLException, ClassNotFoundException {
        if (ticket == null || ticket.getId() <= 0) {
            return false;
        }

        try (Connection connection = dataBaseConfig.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(DBConstants.UPDATE_TICKET)) {
            preparedStatement.setDouble(1, ticket.getPrice());
            if (ticket.getOutTime() != null) {
                preparedStatement.setObject(2, ticket.getOutTime());
            } else {
                preparedStatement.setNull(2, java.sql.Types.TIMESTAMP);
            }
            preparedStatement.setInt(3, ticket.getId());

            logger.debug("Updating ticket with ID: " + ticket.getId() + ", Price: " + ticket.getPrice() + ", OutTime: " + ticket.getOutTime());

            int updateCount = preparedStatement.executeUpdate();

            logger.debug("Update count: " + updateCount);

            if (updateCount == 0) {
                logger.warn("No rows updated. Ticket ID may be invalid: " + ticket.getId());
            }

            return updateCount > 0;

        } catch (SQLException e) {
            logger.error("SQL error updating ticket with ID '" + ticket.getId() + "': " + e.getMessage(), e);
            throw e; 
        }
    }

    /**
     * Deletes all tickets from the database.
     *
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public void deleteAllTickets() throws ClassNotFoundException {
        String deleteQuery = "DELETE FROM TICKET";
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(deleteQuery)) {
            
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("SQL error deleting all tickets: {}", e.getMessage(), e);
        }
    }

    /**
     * Counts the number of tickets for a given vehicle registration number.
     *
     * @param vehicleRegNumber The vehicle registration number
     * @return The number of tickets
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public int getNbTicket(String vehicleRegNumber) throws SQLException, ClassNotFoundException {
        if (vehicleRegNumber == null || vehicleRegNumber.trim().isEmpty()) {
            logger.error("Vehicle registration number is null or empty.");
            return 0;
        }

        String sql = DBConstants.COUNT_TICKETS_FOR_VEHICLE;
        int count = 0;
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, vehicleRegNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }

        } catch (SQLException ex) {
            logger.error("SQL error counting tickets for vehicle registration number '{}': {}", vehicleRegNumber, ex.getMessage(), ex);
            throw ex;  
        }
        return count;
    }
}
