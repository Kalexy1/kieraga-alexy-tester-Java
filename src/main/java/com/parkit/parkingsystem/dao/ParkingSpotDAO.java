package com.parkit.parkingsystem.dao;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.model.ParkingSpot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ParkingSpotDAO {

    private static final Logger logger = LogManager.getLogger(ParkingSpotDAO.class);
    
    public static final String GET_NEXT_AVAILABLE_SPOT_QUERY =
            "SELECT PARKING_NUMBER, TYPE, AVAILABLE FROM parking WHERE TYPE = ? AND AVAILABLE = TRUE LIMIT 1";
    public static final String GET_PARKING_SPOT_QUERY =
            "SELECT TYPE, AVAILABLE FROM parking WHERE PARKING_NUMBER = ?";
    private static final String UPDATE_PARKING_SPOT_QUERY = DBConstants.UPDATE_PARKING_SPOT;
    private static final String INSERT_PARKING_SPOT_QUERY =
            "INSERT INTO parking (PARKING_NUMBER, TYPE, AVAILABLE) VALUES (?, ?, ?)";
    private static final String DELETE_ALL_PARKING_SPOTS_QUERY =
            "DELETE FROM parking";

    private final DataBaseConfig dataBaseConfig;

    public ParkingSpotDAO(DataBaseConfig dataBaseConfig) {
        this.dataBaseConfig = dataBaseConfig;
    }

    /**
     * Retrieves the next available parking spot based on the parking type.
     *
     * @param parkingType The type of parking spot to retrieve
     * @return The next available parking spot, or null if none are available
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public ParkingSpot getNextAvailableSpot(ParkingType parkingType) throws SQLException, ClassNotFoundException {
        if (parkingType == null) {
            throw new IllegalArgumentException("ParkingType cannot be null");
        }

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_NEXT_AVAILABLE_SPOT_QUERY)) {

            ps.setString(1, parkingType.name());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParkingSpot availableSpot = mapResultSetToParkingSpot(rs);
                    if (availableSpot != null) {
                        logger.info("Found available parking spot: ID {}", availableSpot.getId());
                    } else {
                        logger.warn("Mapping result set returned null for parking spot");
                    }
                    return availableSpot;
                } else {
                    logger.info("No available parking spot found for type '{}'", parkingType);
                    return null;
                }
            }
        } catch (SQLException e) {
            logger.error("Error while fetching next available parking spot for type '{}'", parkingType, e);
            throw e;
        }
    }

    /**
     * Retrieves a parking spot by its number.
     *
     * @param parkingNumber The parking spot number
     * @return The parking spot, or null if not found
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public ParkingSpot getParkingSpot(int parkingNumber) throws SQLException, ClassNotFoundException {
        if (parkingNumber <= 0) {
            throw new IllegalArgumentException("Parking number must be greater than 0");
        }

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_PARKING_SPOT_QUERY)) {

            ps.setInt(1, parkingNumber);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToParkingSpot(rs, parkingNumber);
                }
                return null;
            }
        } catch (SQLException e) {
            logger.error("Error while fetching parking spot by number '{}'", parkingNumber, e);
            throw e;
        }
    }

    /**
     * Updates the availability status of a parking spot.
     *
     * @param parkingSpot The parking spot to update
     * @param isAvailable The new availability status
     * @return True if the update was successful, false otherwise
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public boolean updateParking(ParkingSpot parkingSpot, boolean isAvailable) throws SQLException, ClassNotFoundException {
        if (parkingSpot == null) {
            throw new IllegalArgumentException("ParkingSpot cannot be null");
        }

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_PARKING_SPOT_QUERY)) {

            ps.setBoolean(1, isAvailable);
            ps.setInt(2, parkingSpot.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error while updating parking spot availability for spot '{}'", parkingSpot.getId(), e);
            throw e;
        }
    }

    /**
     * Saves a parking spot to the database.
     *
     * @param parkingSpot The parking spot to save
     * @throws ClassNotFoundException If the database driver class is not found
     * @throws SQLException If an SQL error occurs
     */
    public void saveParkingSpot(ParkingSpot parkingSpot) throws ClassNotFoundException, SQLException {
        if (parkingSpot == null) {
            throw new IllegalArgumentException("ParkingSpot cannot be null");
        }

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_PARKING_SPOT_QUERY)) {
            
            ps.setInt(1, parkingSpot.getId());
            ps.setString(2, parkingSpot.getParkingType().name());
            ps.setBoolean(3, parkingSpot.isAvailable());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error while saving parking spot '{}'", parkingSpot.getId(), e);
            throw e;
        }
    }
    
    /**
     * Deletes all parking spots from the database.
     *
     * @throws ClassNotFoundException If the database driver class is not found
     * @throws SQLException If an SQL error occurs
     */
    public void deleteAllParkingSpots() throws SQLException, ClassNotFoundException {
        String sql = DELETE_ALL_PARKING_SPOTS_QUERY;
        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (SQLException ex) {
            logger.error("Error while deleting all parking spots", ex);
            throw ex;
        }
    }

    /**
     * Retrieves all parking spots from the database.
     *
     * @return A list of all parking spots
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public List<ParkingSpot> getAllParkingSpots() throws SQLException, ClassNotFoundException {
        List<ParkingSpot> parkingSpots = new ArrayList<>();
        String sql = "SELECT * FROM parking";

        try (Connection con = dataBaseConfig.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("PARKING_NUMBER");
                ParkingType type = ParkingType.valueOf(rs.getString("TYPE"));
                boolean available = rs.getBoolean("AVAILABLE");
                parkingSpots.add(new ParkingSpot(id, type, available));
            }
        }
        return parkingSpots;
    }

    private ParkingSpot mapResultSetToParkingSpot(ResultSet rs) throws SQLException {
        int parkingNumber = rs.getInt("PARKING_NUMBER");
        ParkingType type = ParkingType.valueOf(rs.getString("TYPE"));
        boolean isAvailable = rs.getBoolean("AVAILABLE");
        return new ParkingSpot(parkingNumber, type, isAvailable);
    }

    private ParkingSpot mapResultSetToParkingSpot(ResultSet rs, int parkingNumber) throws SQLException {
        ParkingType type = ParkingType.valueOf(rs.getString("TYPE"));
        boolean isAvailable = rs.getBoolean("AVAILABLE");
        return new ParkingSpot(parkingNumber, type, isAvailable);
    }
}
