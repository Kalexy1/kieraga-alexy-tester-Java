package com.parkit.parkingsystem.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.exception.CustomException;
import com.parkit.parkingsystem.exception.CustomException.DatabaseException;
import com.parkit.parkingsystem.exception.CustomException.FareCalculationException;
import com.parkit.parkingsystem.exception.CustomException.ParkingSpotNotFoundException;
import com.parkit.parkingsystem.exception.CustomException.ParkingSpotUpdateException;
import com.parkit.parkingsystem.exception.CustomException.TicketNotFoundException;
import com.parkit.parkingsystem.exception.CustomException.TicketSaveException;
import com.parkit.parkingsystem.exception.CustomException.TicketUpdateException;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.util.InputReaderUtil;

/**
 * Service class to handle parking operations.
 */
public class ParkingService {

    private final InputReaderUtil inputReaderUtil;
    private final ParkingSpotDAO parkingSpotDAO;
    private final TicketDAO ticketDAO;
    private final FareCalculatorService fareCalculatorService;

    public static final String NULL_OR_EMPTY_REG_NUMBER_MSG = "Vehicle registration number cannot be null or empty";
    public static final String INVALID_REG_NUMBER_LENGTH_MSG = "Vehicle registration number must be between 2 and 10 characters long";
    public static final String PARKING_TYPE_NULL_MSG = "Parking type cannot be null";
    public static final String TICKET_NOT_FOUND_MSG = "No ticket found for vehicle registration number: ";
    public static final String FARE_CALCULATION_ERROR_MSG = "Unable to calculate fare";
    public static final String TICKET_UPDATE_ERROR_MSG = "Unable to update the ticket for ticket ID: ";
    public static final String PARKING_SPOT_UPDATE_ERROR_MSG = "Unable to update parking spot availability for parking spot ID: ";
    public static final String DATABASE_ERROR_MSG = "Database error: ";
    public static final String PARKING_SPOT_MISSING_ERROR_MSG = "Parking spot information is missing for ticket ID: ";

    private static final int MIN_REG_NUMBER_LENGTH = 2;
    private static final int MAX_REG_NUMBER_LENGTH = 10;

    public ParkingService(InputReaderUtil inputReaderUtil, ParkingSpotDAO parkingSpotDAO, TicketDAO ticketDAO, FareCalculatorService fareCalculatorService) {
        this.inputReaderUtil = inputReaderUtil;
        this.parkingSpotDAO = parkingSpotDAO;
        this.ticketDAO = ticketDAO;
        this.fareCalculatorService = fareCalculatorService;
    }
    
    private List<ParkingSpot> parkingSpots = new ArrayList<>();

    /**
     * Adds a parking spot to the list of parking spots.
     *
     * @param spot The parking spot to be added
     */
    public void addParkingSpot(ParkingSpot spot) {
        parkingSpots.add(spot);
    }

    /**
     * Processes an incoming vehicle by assigning a parking spot and creating a new ticket.
     *
     * @param vehicleRegNumber Vehicle registration number
     * @param parkingType Type of parking spot
     * @throws ClassNotFoundException If class not found
     * @throws DatabaseException If there is a database error
     * @throws SQLException If there is an SQL error
     * @throws ParkingSpotUpdateException If there is an error updating the parking spot
     * @throws TicketSaveException If there is an error saving the ticket
     */
    public void processIncomingVehicle(String vehicleRegNumber, ParkingType parkingType) 
            throws ParkingSpotUpdateException, TicketSaveException, DatabaseException, ClassNotFoundException, SQLException {
        
        validateVehicleRegistration(vehicleRegNumber);
        
        if (parkingType == null) {
            throw new IllegalArgumentException(PARKING_TYPE_NULL_MSG);
        }

        ParkingSpot parkingSpot;
        try {
            parkingSpot = parkingSpotDAO.getNextAvailableSpot(parkingType);
        } catch (SQLException e) {
            throw new DatabaseException("Database error occurred while processing 'incoming vehicle': " + e.getMessage());
        }
        
        if (parkingSpot == null) {
            throw new ParkingSpotNotFoundException("No available parking spot for type: " + parkingType);
        }

        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber(vehicleRegNumber);
        ticket.setParkingSpot(parkingSpot);
        ticket.setInTime(LocalDateTime.now());

        boolean isTicketSaved = ticketDAO.saveTicket(ticket);
        if (!isTicketSaved) {
            throw new TicketSaveException("Failed to save ticket for vehicle: " + vehicleRegNumber);
        }

        boolean isSpotUpdated = parkingSpotDAO.updateParking(parkingSpot, false);
        if (!isSpotUpdated) {
            throw new ParkingSpotUpdateException("Unable to update parking spot availability for parking spot ID: " + parkingSpot.getId());
        }
    }
    
    /**
     * Validates the vehicle registration number.
     *
     * @param vehicleRegNumber Vehicle registration number
     * @throws IllegalArgumentException If the registration number is null, empty, or has an invalid length
     */
    public void validateVehicleRegistration(String vehicleRegNumber) {
        if (vehicleRegNumber == null || vehicleRegNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(NULL_OR_EMPTY_REG_NUMBER_MSG);
        }
        
        String trimmedRegNumber = vehicleRegNumber.trim();
        int length = trimmedRegNumber.length();
        
        if (length < MIN_REG_NUMBER_LENGTH || length > MAX_REG_NUMBER_LENGTH) {
            throw new IllegalArgumentException(INVALID_REG_NUMBER_LENGTH_MSG);
        }
    }

    /**
     * Validates the parking type.
     *
     * @param parkingType The parking type to validate
     * @throws IllegalArgumentException If the parking type is null
     */
    private void validateParkingType(ParkingType parkingType) {
        if (parkingType == null) {
            throw new IllegalArgumentException(PARKING_TYPE_NULL_MSG);
        }
    }

    /**
     * Creates a new ticket for a vehicle in a parking spot.
     *
     * @param vehicleRegNumber Vehicle registration number
     * @param parkingSpot The parking spot where the vehicle is parked
     * @return A new Ticket object
     */
    public Ticket createNewTicket(String vehicleRegNumber, ParkingSpot parkingSpot) {
        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber(vehicleRegNumber);
        ticket.setParkingSpot(parkingSpot);
        ticket.setInTime(LocalDateTime.now());
        return ticket;
    }

    /**
     * Processes an exiting vehicle by updating the ticket and parking spot.
     *
     * @param vehicleRegNumber Vehicle registration number
     * @throws Exception If there is an error during processing
     */
    public void processExitingVehicle(String vehicleRegNumber) throws Exception {
        System.out.println("Processing exit for vehicle: " + vehicleRegNumber);
        
        Ticket ticket = ticketDAO.getTicket(vehicleRegNumber);
        if (ticket == null) {
            throw new TicketNotFoundException(TICKET_NOT_FOUND_MSG + vehicleRegNumber);
        }

        if (ticket.getInTime() == null) {
            throw new IllegalArgumentException("Entry time is not set for ticket ID: " + ticket.getId());
        }

        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime exitTime = currentTime.isBefore(ticket.getInTime()) 
                                  ? ticket.getInTime().plusMinutes(1) 
                                  : currentTime;

        ticket.setOutTime(exitTime);
        System.out.println("Exit time set to: " + ticket.getOutTime());

        if (ticket.getOutTime().isBefore(ticket.getInTime())) {
            throw new IllegalArgumentException("Exit time must be after entry time for ticket ID: " + ticket.getId());
        }

        try {
            double fare = fareCalculatorService.calculateFare(ticket, false); 
            ticket.setPrice(fare);
            System.out.println("Fare calculated: " + fare);
        } catch (Exception e) {
            throw FareCalculationException.createWithTicketId(ticket.getId(), e);
        }

        if (!ticketDAO.updateTicket(ticket)) {
            throw new TicketUpdateException(TICKET_UPDATE_ERROR_MSG + ticket.getId());
        }

        if (!parkingSpotDAO.updateParking(ticket.getParkingSpot(), true)) {
            throw new ParkingSpotUpdateException(PARKING_SPOT_UPDATE_ERROR_MSG + ticket.getParkingSpot().getId());
        }

        System.out.println("Exit processed successfully for vehicle: " + vehicleRegNumber);
    }

    /**
     * Updates the ticket and the associated parking spot.
     *
     * @param ticket The ticket to update
     * @param fare The calculated fare to set on the ticket
     * @throws ClassNotFoundException If class not found
     * @throws SQLException If there is an SQL error
     * @throws ParkingSpotUpdateException If there is an error updating the parking spot
     */
    public void updateTicketAndParkingSpot(Ticket ticket, double fare) throws ClassNotFoundException, SQLException, ParkingSpotUpdateException {
        if (ticket == null) {
            throw new TicketUpdateException("Ticket cannot be null");
        }
        
        ticket.setPrice(fare);

        if (!ticketDAO.updateTicket(ticket)) {
            throw new TicketUpdateException(TICKET_UPDATE_ERROR_MSG + ticket.getId());
        }

        ParkingSpot parkingSpot = ticket.getParkingSpot();
        if (parkingSpot != null) {
            if (!parkingSpotDAO.updateParking(parkingSpot, true)) {
                throw new ParkingSpotUpdateException("Failed to update parking spot availability for spot ID: " + parkingSpot.getId());
            }
        } else {
            throw new TicketUpdateException(PARKING_SPOT_MISSING_ERROR_MSG + ticket.getId());
        }
    }

    /**
     * Retrieves the next available parking spot of the given type.
     *
     * @param parkingType Type of parking spot
     * @return The next available parking spot
     * @throws ClassNotFoundException If class not found
     * @throws DatabaseException If there is a database error
     */
    public ParkingSpot getNextParkingNumberIfAvailable(ParkingType parkingType) throws ClassNotFoundException, DatabaseException {
        validateParkingType(parkingType);

        try {
            return parkingSpotDAO.getNextAvailableSpot(parkingType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown parking type: " + parkingType, e);
        } catch (SQLException e) {
            throw new DatabaseException("Database error: getting next parking number: " + e.getMessage(), e);
        }
    }
}
