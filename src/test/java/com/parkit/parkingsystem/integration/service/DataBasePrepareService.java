package com.parkit.parkingsystem.integration.service;

import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;

import java.sql.SQLException;
import java.time.LocalDateTime;
import com.parkit.parkingsystem.constants.ParkingType;

public class DataBasePrepareService {
    
    private ParkingSpotDAO parkingSpotDAO;
    private TicketDAO ticketDAO;

    public DataBasePrepareService(ParkingSpotDAO parkingSpotDAO, TicketDAO ticketDAO) {
        this.parkingSpotDAO = parkingSpotDAO;
        this.ticketDAO = ticketDAO;
    }

    /**
     * Clears all entries from the database by deleting all tickets and parking spots.
     *
     * @throws ClassNotFoundException If the database driver class is not found
     * @throws SQLException If an SQL error occurs
     */
    public void clearDataBaseEntries() throws ClassNotFoundException, SQLException {
        ticketDAO.deleteAllTickets();
        parkingSpotDAO.deleteAllParkingSpots();
    }

    /**
     * Populates the parking spot table with a specified number of available parking spots.
     *
     * @param numberOfSpots The number of parking spots to create
     * @throws ClassNotFoundException If the database driver class is not found
     * @throws SQLException If an SQL error occurs
     */
    public void populateParkingSpotTable(int numberOfSpots) throws ClassNotFoundException, SQLException {
        for (int i = 1; i <= numberOfSpots; i++) {
            ParkingSpot spot = new ParkingSpot(i, ParkingType.CAR, true); // Available spot
            parkingSpotDAO.saveParkingSpot(spot);
        }
    }

    /**
     * Populates the parking spot table with a specified number of parking spots,
     * marking them as occupied.
     *
     * @param numberOfOccupiedSpots The number of parking spots to occupy
     * @throws ClassNotFoundException If the database driver class is not found
     * @throws SQLException If an SQL error occurs
     */
    public void populateParkingSpotTableWithOccupiedSpots(int numberOfOccupiedSpots) throws ClassNotFoundException, SQLException {
        populateParkingSpotTable(numberOfOccupiedSpots);
        for (int i = 1; i <= numberOfOccupiedSpots; i++) {
            ParkingSpot spot = parkingSpotDAO.getParkingSpot(i);
            spot.setAvailable(false);
            parkingSpotDAO.updateParking(spot, false);
        }
    }

    /**
     * Adds multiple tickets to the database for testing purposes.
     *
     * @param numberOfTickets The number of tickets to create
     * @throws SQLException If an SQL error occurs
     * @throws ClassNotFoundException If the database driver class is not found
     */
    public void addMultipleTickets(int numberOfTickets) throws SQLException, ClassNotFoundException {
        for (int i = 1; i <= numberOfTickets; i++) {
            Ticket ticket = new Ticket();
            ticket.setVehicleRegNumber("TEST" + i);
            ParkingSpot spot = parkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);
            ticket.setParkingSpot(spot);
            ticket.setInTime(LocalDateTime.now().minusHours(i));
            ticket.setOutTime(LocalDateTime.now().minusHours(i).plusMinutes(30));
            ticket.setPrice(0); // Default price
            ticketDAO.saveTicket(ticket);

            spot.setAvailable(false);
            parkingSpotDAO.updateParking(spot, false);
        }
    }
}
