package com.parkit.parkingsystem.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.exception.CustomException.DatabaseException;
import com.parkit.parkingsystem.exception.CustomException.ParkingSpotNotFoundException;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseITTest {

    private static DataBaseTestConfig dataBaseTestConfig;
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;
    private static FareCalculatorService fareCalculatorService;

    @Mock
    private static Logger logger;

    @Mock
    private InputReaderUtil inputReaderUtil;
    
    @Mock
    private DataBaseConfig dataBaseConfig;

    @BeforeAll
    public static void setUp() throws Exception {
        dataBaseTestConfig = new DataBaseTestConfig();
        parkingSpotDAO = new ParkingSpotDAO(dataBaseTestConfig);
        fareCalculatorService = new FareCalculatorService();
        ticketDAO = new TicketDAO(dataBaseTestConfig);  
        dataBasePrepareService = new DataBasePrepareService(parkingSpotDAO, ticketDAO);
        ticketDAO.deleteAllTickets();
        parkingSpotDAO.deleteAllParkingSpots();
    }

    @BeforeEach
    public void setUpPerTest() throws Exception {
        dataBasePrepareService.populateParkingSpotTable(10);
        dataBasePrepareService.addMultipleTickets(5);
    }

    @AfterEach
    public void tearDownEach() throws Exception {
        ticketDAO.deleteAllTickets();
        parkingSpotDAO.deleteAllParkingSpots();
    }

    @Test
    public void testParkingACar() throws Exception {
        String regNumber = "ABCDEF";
        ParkingType parkingType = ParkingType.CAR;

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);

        parkingService.processIncomingVehicle(regNumber, parkingType);

        Ticket ticket = ticketDAO.getTicket(regNumber);
        assertNotNull(ticket, "Ticket should be created and saved in the database");

        ParkingSpot parkingSpot = parkingSpotDAO.getParkingSpot(ticket.getParkingSpot().getId());
        assertNotNull(parkingSpot, "ParkingSpot should be retrieved from the database");
        assertFalse(parkingSpot.isAvailable(), "ParkingSpot should be marked as occupied");
    }

    @Test
    public void testParkingLotExit() throws Exception {
        String regNumber = "TEST1";
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);
        DataBasePrepareService dataBasePrepareService = new DataBasePrepareService(parkingSpotDAO, ticketDAO);

        dataBasePrepareService.addMultipleTickets(5);

        Ticket ticket = ticketDAO.getTicket(regNumber);
        assertNotNull(ticket, "Ticket should be created and available in the database");

        assertNotNull(ticket.getInTime(), "Entry time should not be null");

        parkingService.processExitingVehicle(regNumber); 

        Ticket updatedTicket = ticketDAO.getTicket(regNumber); 
        assertNotNull(updatedTicket, "Ticket should still exist");
        assertTrue(updatedTicket.getOutTime().isAfter(updatedTicket.getInTime()), "Exit time must be after entry time");

        assertNotNull(updatedTicket.getPrice(), "Ticket price should be calculated");

        assertTrue(parkingSpotDAO.getParkingSpot(updatedTicket.getParkingSpot().getId()).isAvailable(), "ParkingSpot should be marked as available");
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {
        String regNumber = "RECUR123";
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);
        DataBasePrepareService dataBasePrepareService = new DataBasePrepareService(parkingSpotDAO, ticketDAO);

        parkingSpotDAO.deleteAllParkingSpots();

        for (int i = 1; i <= 5; i++) {
            ParkingSpot spot = new ParkingSpot(i, ParkingType.CAR, true);
            parkingSpotDAO.saveParkingSpot(spot);
        }

        List<ParkingSpot> spots = parkingSpotDAO.getAllParkingSpots();
        assertFalse(spots.isEmpty(), "Parking spots should have been added to the database");

        for (ParkingSpot spot : spots) {
            logger.info("Parking Spot ID: {}, Type: {}, Available: {}", spot.getId(), spot.getParkingType(), spot.isAvailable());
        }

        ParkingSpot availableSpot = parkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);
        assertNotNull(availableSpot, "Parking spot should not be null after adding parking spots");

        Ticket specificTicket = new Ticket();
        specificTicket.setVehicleRegNumber(regNumber);

        specificTicket.setParkingSpot(availableSpot);
        specificTicket.setInTime(LocalDateTime.now().minusMinutes(30));
        specificTicket.setOutTime(null);
        specificTicket.setPrice(0);

        logger.info("Saving ticket with VehicleRegNumber: {}, ParkingSpotID: {}, InTime: {}, Price: {}",
            specificTicket.getVehicleRegNumber(),
            specificTicket.getParkingSpot().getId(),
            specificTicket.getInTime(),
            specificTicket.getPrice());

        boolean isSaved = ticketDAO.saveTicket(specificTicket);
        assertTrue(isSaved, "Ticket should be saved successfully");

        assertNotNull(specificTicket.getParkingSpot(), "Parking spot should be associated with the ticket");
        assertNotNull(specificTicket.getParkingSpot().getId(), "Parking spot ID should not be null after saving the ticket");

        Ticket firstTicket = ticketDAO.getTicket(regNumber);
        assertNotNull(firstTicket, "Ticket should be available in the database");
        assertNotNull(firstTicket.getParkingSpot(), "Parking spot should not be null in the first ticket");

        assertNotNull(firstTicket.getInTime(), "First ticket should have a valid entry time");

        parkingService.processExitingVehicle(regNumber);

        parkingService.processIncomingVehicle(regNumber, ParkingType.CAR);
        Ticket secondTicket = ticketDAO.getTicket(regNumber);
        assertNotNull(secondTicket, "Second ticket should be created and available in the database");

        secondTicket.setOutTime(secondTicket.getInTime().plusMinutes(20));
        ticketDAO.updateTicket(secondTicket);
        parkingService.processExitingVehicle(regNumber);

        Ticket finalTicket = ticketDAO.getTicket(regNumber);
        assertNotNull(finalTicket, "Final ticket should be available");

        double expectedDiscountedPrice = finalTicket.getPrice() / 0.95;
        assertEquals(expectedDiscountedPrice, finalTicket.getPrice(), 0.01, "The final price should match the expected discounted price");
    }

    @Test
    public void testSaveParkingSpot() throws Exception {
        int uniqueParkingNumber = new Random().nextInt(10000);
        ParkingSpot parkingSpot = new ParkingSpot(uniqueParkingNumber, ParkingType.CAR, true);

        parkingSpotDAO.saveParkingSpot(parkingSpot);

        ParkingSpot retrievedSpot = parkingSpotDAO.getParkingSpot(parkingSpot.getId());
        assertNotNull(retrievedSpot, "Parking spot should be retrievable");
        assertEquals(parkingSpot.getId(), retrievedSpot.getId(), "Parking spot ID should match");
        assertEquals(parkingSpot.getParkingType(), retrievedSpot.getParkingType(), "Parking spot type should match");
        assertEquals(parkingSpot.isAvailable(), retrievedSpot.isAvailable(), "Parking spot availability should match");
    }

    @Test
    public void testSaveTicket() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setVehicleRegNumber("TEST123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ParkingSpot spot = new ParkingSpot(1, ParkingType.CAR, true);
        ticket.setParkingSpot(spot);
        
        boolean result = ticketDAO.saveTicket(ticket);
        assertTrue(result, "Ticket should be saved successfully");
    }

    @Test
    public void testSaveTicketWithIncompleteDetails() throws Exception {
        Ticket incompleteTicket = new Ticket();
        incompleteTicket.setVehicleRegNumber("INCOMPLETE");
        boolean result = ticketDAO.saveTicket(incompleteTicket);

        assertFalse(result, "Saving a ticket with incomplete details should return false.");
    }

    @Test
    public void testSaveTicketWithExtremeValues() throws Exception {
        Ticket ticket = new Ticket();
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("EXTREME123");
        ticket.setPrice(Double.MAX_VALUE);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusYears(100));

        boolean result = ticketDAO.saveTicket(ticket);

        assertTrue(result, "Ticket should be saved successfully even with extreme values.");
    }
    
    @Test
    public void testSaveTicketAfterDeletion() throws Exception {
        Ticket ticket = new Ticket();
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("DELETE123");
        ticket.setPrice(20.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(2));

        ticketDAO.saveTicket(ticket);
        ticketDAO.deleteAllTickets();

        Ticket fetchedTicket = ticketDAO.getTicket("DELETE123");

        assertNull(fetchedTicket, "Ticket should be null after deletion.");
    }

    @Test
    public void testDatabaseResetBeforeEachTest() throws Exception {
        List<ParkingSpot> parkingSpots = parkingSpotDAO.getAllParkingSpots();
        assertEquals(10, parkingSpots.size(), "Database should have 10 parking spots after setup.");
    }
    
    @Test
    public void testSaveValidTicket() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABC123");
        ticket.setPrice(10.0);

        LocalDateTime inTime = LocalDateTime.now();
        LocalDateTime outTime = inTime.plusHours(1);
        ticket.setInTime(inTime);
        ticket.setOutTime(outTime);

        boolean result = ticketDAO.saveTicket(ticket);

        assertTrue(result, "Ticket should be saved successfully.");
        Ticket savedTicket = ticketDAO.getTicket(ticket.getVehicleRegNumber());
        assertNotNull(savedTicket, "Saved ticket should be retrievable from the database.");
        System.out.println("Saved ticket: " + savedTicket);

        assertEquals(ticket.getVehicleRegNumber(), savedTicket.getVehicleRegNumber(), "Vehicle registration number should match.");
        assertEquals(ticket.getPrice(), savedTicket.getPrice(), "Price should match.");
    }

    @Test
    public void testSaveTicketWithMissingParkingSpot() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(null);
        ticket.setVehicleRegNumber("MISSINGSPOT123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        boolean result = ticketDAO.saveTicket(ticket);

        assertFalse(result, "Ticket with a missing parking spot should not be saved.");
    }

    @Test
    public void testSaveTicketWithInvalidVehicleRegNumber() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber(null);
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        boolean result = ticketDAO.saveTicket(ticket);

        assertFalse(result, "Ticket with an invalid vehicle registration number should not be saved.");
    }

    @Test
    public void testSaveTicketWithFutureInTime() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("FUTURE123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now().plusDays(1));
        ticket.setOutTime(LocalDateTime.now().plusDays(1).plusHours(1));

        boolean result = ticketDAO.saveTicket(ticket);

        assertTrue(result, "Ticket with future in-time should be saved.");
        Ticket savedTicket = ticketDAO.getTicket(ticket.getVehicleRegNumber());
        assertNotNull(savedTicket, "Saved ticket with future in-time should be retrievable from the database.");
    }

    @Test
    public void testSaveTicketWithNullOutTime() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("NULL123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(null);

        boolean result = ticketDAO.saveTicket(ticket);

        assertTrue(result, "Ticket with null out-time should be saved.");
        Ticket savedTicket = ticketDAO.getTicket(ticket.getVehicleRegNumber());
        assertNotNull(savedTicket, "Saved ticket with null out-time should be retrievable from the database.");
        assertNull(savedTicket.getOutTime(), "Out-time should be null for tickets saved with null out-time.");
    }

    @Test
    public void testSaveTicketHandlesSQLException() throws Exception {
        DataBaseConfig faultyConfig = mock(DataBaseConfig.class);
        when(faultyConfig.getConnection()).thenThrow(new SQLException("Database error"));

        TicketDAO faultyTicketDAO = new TicketDAO(faultyConfig);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        ticket.setVehicleRegNumber("ERROR123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        SQLException thrown = assertThrows(SQLException.class, () -> {
            faultyTicketDAO.saveTicket(ticket);
        });

        assertEquals("Database error", thrown.getMessage(), "SQLException should be thrown with the correct message.");
    }

    @Test
    public void testSaveTicketHandlesClassNotFoundException() throws Exception {
        DataBaseConfig faultyConfig = mock(DataBaseConfig.class);
        when(faultyConfig.getConnection()).thenThrow(new ClassNotFoundException("Driver not found"));

        TicketDAO faultyTicketDAO = new TicketDAO(faultyConfig);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, false));
        ticket.setVehicleRegNumber("ERROR123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class, () -> {
            faultyTicketDAO.saveTicket(ticket);
        });

        assertEquals("Driver not found", thrown.getMessage(), "ClassNotFoundException should be thrown with the correct message.");
    }

    @Test
    public void testGetTicketWithValidVehicleRegNumber() throws Exception {
        String regNumber = "VALID123";
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber(regNumber);
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));
        ticketDAO.saveTicket(ticket);

        Ticket retrievedTicket = ticketDAO.getTicket(regNumber);

        assertNotNull(retrievedTicket, "Ticket should be retrieved from the database.");
        assertEquals(regNumber, retrievedTicket.getVehicleRegNumber(), "Vehicle registration number should match.");
        assertEquals(ticket.getPrice(), retrievedTicket.getPrice(), 0.01, "Price should match.");
    }

    @Test
    public void testGetTicketWithInvalidVehicleRegNumber() throws Exception {
        Ticket retrievedTicket = ticketDAO.getTicket("INVALID123");
        assertNull(retrievedTicket, "No ticket should be retrieved for an invalid vehicle registration number.");
    }

    @Test
    public void testGetTicketWithNullVehicleRegNumber() throws Exception {
        Ticket retrievedTicket = ticketDAO.getTicket(null);
        assertNull(retrievedTicket, "No ticket should be retrieved for a null vehicle registration number.");
    }
    
    @Test
    public void testGetTicketWithEmptyVehicleRegNumber() throws Exception {
        Ticket retrievedTicket = ticketDAO.getTicket("");
        assertNull(retrievedTicket, "No ticket should be retrieved for an empty vehicle registration number.");
    }

    @Test
    public void testGetTicketWithMissingDetails() throws Exception {
        String regNumber = "MISSING123";
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(null);
        ticket.setVehicleRegNumber(regNumber);
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));
        assertFalse(ticketDAO.saveTicket(ticket));
    }

    @Test
    public void testGetTicketHandlesSQLException() throws Exception {
        DataBaseConfig faultyConfig = mock(DataBaseConfig.class);
        when(faultyConfig.getConnection()).thenThrow(new SQLException("Database error"));

        TicketDAO faultyTicketDAO = new TicketDAO(faultyConfig);

        SQLException thrown = assertThrows(SQLException.class, () -> {
            faultyTicketDAO.getTicket("ANY123");
        });

        assertEquals("Database error", thrown.getMessage(), "SQLException should be thrown with the correct message.");
    }

    @Test
    public void testGetTicketHandlesClassNotFoundException() throws Exception {
        DataBaseConfig faultyConfig = mock(DataBaseConfig.class);
        when(faultyConfig.getConnection()).thenThrow(new ClassNotFoundException("Driver not found"));

        TicketDAO faultyTicketDAO = new TicketDAO(faultyConfig);

        ClassNotFoundException thrown = assertThrows(ClassNotFoundException.class, () -> {
            faultyTicketDAO.getTicket("ANY123");
        });

        assertEquals("Driver not found", thrown.getMessage(), "ClassNotFoundException should be thrown with the correct message.");
    }

    @Test
    public void testConsistencyOfDataRetrieval() throws Exception {
        String regNumber = "CONSIST123";
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber(regNumber);
        ticket.setPrice(15.0);
        ticket.setInTime(LocalDateTime.now().minusHours(1).withNano(0));
        ticket.setOutTime(LocalDateTime.now().withNano(0));
        ticketDAO.saveTicket(ticket);

        Ticket retrievedTicket = ticketDAO.getTicket(regNumber);

        assertNotNull(retrievedTicket, "Ticket should be retrieved from the database.");
        assertEquals(ticket.getVehicleRegNumber(), retrievedTicket.getVehicleRegNumber(), "Vehicle registration number should match.");
        assertEquals(ticket.getPrice(), retrievedTicket.getPrice(), 0.01, "Price should match.");
        assertEquals(ticket.getInTime(), retrievedTicket.getInTime().withNano(0), "In time should match.");
        assertEquals(ticket.getOutTime(), retrievedTicket.getOutTime().withNano(0), "Out time should match.");
        assertEquals(ticket.getParkingSpot().getId(), retrievedTicket.getParkingSpot().getId(), "Parking spot ID should match.");
    }

    @Test
    public void testDatabaseConnectionFailure() throws Exception {
        DataBaseTestConfig faultyConfig = new DataBaseTestConfig() {
            @Override
            public Connection getConnection() throws ClassNotFoundException, SQLException {
                throw new SQLException("Connection failed");
            }
        };
        ParkingSpotDAO faultyParkingSpotDAO = new ParkingSpotDAO(faultyConfig);
        TicketDAO faultyTicketDAO = new TicketDAO(faultyConfig);
        ParkingService parkingService = new ParkingService(inputReaderUtil, faultyParkingSpotDAO, faultyTicketDAO, fareCalculatorService);

        DatabaseException exception = assertThrows(DatabaseException.class, () -> {
            parkingService.processIncomingVehicle("FAULTY123", ParkingType.CAR);
        });

        assertEquals("Database error occurred while processing 'incoming vehicle': Connection failed", exception.getMessage(), "Should throw DatabaseException with the correct message");
    }
    
    @Test
    public void testGetNextAvailableSpot() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);

        when(mockConnection.prepareStatement(ParkingSpotDAO.GET_NEXT_AVAILABLE_SPOT_QUERY)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(true);
        when(mockResultSet.getInt("PARKING_NUMBER")).thenReturn(1);
        when(mockResultSet.getString("TYPE")).thenReturn(ParkingType.CAR.name());
        when(mockResultSet.getBoolean("AVAILABLE")).thenReturn(true);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        ParkingSpot result = parkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);

        assertNotNull(result, "Should return an available parking spot.");
        assertEquals(1, result.getId(), "The returned spot ID should match.");
        assertEquals(ParkingType.CAR, result.getParkingType(), "The parking type should match.");
        assertTrue(result.isAvailable(), "The parking spot should be available.");
    }

    
    @Test
    public void testGetNextAvailableSpotNoAvailableSpots() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(ParkingSpotDAO.GET_NEXT_AVAILABLE_SPOT_QUERY)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        when(mockResultSet.next()).thenReturn(false);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        ParkingSpot result = parkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);

        assertNull(result, "Should return null if no available parking spots.");
    }

    
    @Test
    public void testGetNextAvailableSpotWithNullType() {
        assertThrows(IllegalArgumentException.class, () -> {
            parkingSpotDAO.getNextAvailableSpot(null);
        });
    }

    @Test
    public void testGetNextAvailableSpotSQLException() throws Exception {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(dataBaseConfig.getConnection()).thenReturn(mockConnection);

        String sqlQuery = "SELECT PARKING_NUMBER, TYPE, AVAILABLE FROM parking WHERE TYPE = ? AND AVAILABLE = TRUE LIMIT 1";
        when(mockConnection.prepareStatement(sqlQuery)).thenReturn(mockPreparedStatement);

        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Test SQL exception"));

        ParkingSpotDAO mockParkingSpotDAO = new ParkingSpotDAO(dataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            mockParkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);
        });

        assertEquals("Test SQL exception", thrownException.getMessage(), "Exception message should match.");
    }
    
    @Test
    public void testGetNextAvailableSpotClassNotFoundException() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        when(mockDataBaseConfig.getConnection()).thenThrow(new ClassNotFoundException("Test ClassNotFoundException"));

        ParkingSpotDAO mockParkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        assertThrows(ClassNotFoundException.class, () -> {
            mockParkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);
        });
    }
    
    @Test
    public void testGetParkingSpot() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        lenient().when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(ParkingSpotDAO.GET_PARKING_SPOT_QUERY)).thenReturn(mockPreparedStatement);
        lenient().when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        lenient().when(mockResultSet.next()).thenReturn(true);
        lenient().when(mockResultSet.getInt("PARKING_NUMBER")).thenReturn(1);
        lenient().when(mockResultSet.getString("TYPE")).thenReturn(ParkingType.CAR.name());
        lenient().when(mockResultSet.getBoolean("AVAILABLE")).thenReturn(true);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        ParkingSpot result = parkingSpotDAO.getParkingSpot(1);

        assertNotNull(result, "Should return the parking spot if it exists.");
        assertEquals(1, result.getId(), "The returned spot ID should match.");
        assertEquals(ParkingType.CAR, result.getParkingType(), "The parking type should match.");
        assertTrue(result.isAvailable(), "The parking spot should be available.");
    }

    @Test
    public void testGetParkingSpotNotFound() throws Exception {
        ParkingSpot result = parkingSpotDAO.getParkingSpot(999);
        assertNull(result, "Should return null if the parking spot does not exist.");
    }

    @Test
    public void testGetParkingSpotWithInvalidNumber() {
        assertThrows(IllegalArgumentException.class, () -> {
            parkingSpotDAO.getParkingSpot(-1);
        }, "Should throw IllegalArgumentException for invalid parking number.");
    }

    @Test
    public void testGetParkingSpotSQLException() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.GET_PARKING_SPOT)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeQuery()).thenThrow(new SQLException("Test SQL exception"));

        ParkingSpotDAO mockParkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        assertThrows(SQLException.class, () -> {
            mockParkingSpotDAO.getParkingSpot(1);
        });
    }

    @Test
    public void testGetParkingSpotClassNotFoundException() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        when(mockDataBaseConfig.getConnection()).thenThrow(new ClassNotFoundException("Test ClassNotFoundException"));

        ParkingSpotDAO mockParkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        assertThrows(ClassNotFoundException.class, () -> {
            mockParkingSpotDAO.getParkingSpot(1);
        });
    }
    
    @Test
    public void testGetParkingSpotUnavailable() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        ResultSet mockResultSet = mock(ResultSet.class);

        lenient().when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        lenient().when(mockConnection.prepareStatement(ParkingSpotDAO.GET_PARKING_SPOT_QUERY)).thenReturn(mockPreparedStatement);
        lenient().when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet);

        lenient().when(mockResultSet.next()).thenReturn(true);
        lenient().when(mockResultSet.getInt("PARKING_NUMBER")).thenReturn(2);
        lenient().when(mockResultSet.getString("TYPE")).thenReturn(ParkingType.BIKE.name());
        lenient().when(mockResultSet.getBoolean("AVAILABLE")).thenReturn(false);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        ParkingSpot result = parkingSpotDAO.getParkingSpot(2);

        assertNotNull(result, "Should return the parking spot even if it is not available.");
        assertFalse(result.isAvailable(), "The parking spot should be marked as unavailable.");
    }

    @Test
    public void testUpdateParkingSuccess() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_PARKING_SPOT)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);

        boolean result = parkingSpotDAO.updateParking(parkingSpot, false);

        assertTrue(result, "The parking spot should be updated successfully.");
    }

    @Test
    public void testUpdateParkingFailure() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_PARKING_SPOT)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(0);

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);

        boolean result = parkingSpotDAO.updateParking(parkingSpot, false);

        assertFalse(result, "The parking spot update should fail.");
    }

    @Test
    public void testUpdateParkingWithNullSpot() {
        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mock(DataBaseConfig.class));

        assertThrows(IllegalArgumentException.class, () -> {
            parkingSpotDAO.updateParking(null, false);
        }, "ParkingSpot cannot be null");
    }

    @Test
    public void testUpdateParkingWithInvalidParkingNumber() throws Exception {
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(dataBaseConfig.getConnection()).thenReturn(mockConnection);
        
        when(mockConnection.prepareStatement(DBConstants.UPDATE_PARKING_SPOT))
            .thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Invalid parking number"));

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(dataBaseConfig);
        ParkingSpot parkingSpot = new ParkingSpot(-1, ParkingType.CAR, true);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            parkingSpotDAO.updateParking(parkingSpot, false);
        });

        assertEquals("Invalid parking number", thrownException.getMessage(), "Exception message should match.");
    }
    
    @Test
    public void testUpdateParkingSQLException() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_PARKING_SPOT)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Test SQL exception"));

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            parkingSpotDAO.updateParking(parkingSpot, false);
        });

        assertEquals("Test SQL exception", thrownException.getMessage());
    }

    @Test
    public void testSaveTicketValid() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticket.setVehicleRegNumber("ABC123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        boolean result = ticketDAO.saveTicket(ticket);

        assertTrue(result);
    }
    
    @Test
    public void testSaveTicketNull() throws Exception {
        boolean result = ticketDAO.saveTicket(null);
        assertFalse(result);
    }
    
    @Test
    public void testSaveTicketWithNullFields() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(null);
        ticket.setVehicleRegNumber(null);
        boolean result = ticketDAO.saveTicket(ticket);
        assertFalse(result);
    }
    
    @Test
    public void testSaveTicketSQLException() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticket.setVehicleRegNumber("ABC123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.SAVE_TICKET)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Test SQL exception"));

        TicketDAO ticketDAO = new TicketDAO(mockDataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            ticketDAO.saveTicket(ticket);
        });

        assertEquals("Test SQL exception", thrownException.getMessage(), "SQLException message should match.");
    }

    
    @Test
    public void testGetNextParkingSpot() throws Exception {
        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(dataBaseTestConfig);

        ParkingSpot parkingSpot = parkingSpotDAO.getNextAvailableSpot(ParkingType.CAR);

        assertNotNull(parkingSpot);
        assertTrue(parkingSpot.getId() > 0);
    }
    
    @Test
    public void testUpdateParkingSpotValid() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        boolean result = parkingSpotDAO.updateParking(parkingSpot, false);
        assertTrue(result);
    }
    
    @Test
    public void testUpdateParkingSpotNull() {
        IllegalArgumentException thrownException = assertThrows(IllegalArgumentException.class, () -> {
            parkingSpotDAO.updateParking(null, false);
        });
        assertEquals("ParkingSpot cannot be null", thrownException.getMessage());
    }
    
    @Test
    public void testUpdateParkingSpotSQLException() throws Exception {
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_PARKING_SPOT))
            .thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Test SQL exception"));

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            parkingSpotDAO.updateParking(parkingSpot, false);
        });

        assertEquals("Test SQL exception", thrownException.getMessage(), "SQLException message should match.");
    }

    @Test
    public void testGetTicketValid() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticket.setVehicleRegNumber("ABC123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());

        ticketDAO.saveTicket(ticket);

        Ticket retrievedTicket = ticketDAO.getTicket("ABC123");

        assertNotNull(retrievedTicket);
        assertEquals("ABC123", retrievedTicket.getVehicleRegNumber());
    }
    
    @Test
    public void testGetTicketNonExisting() throws Exception {
        Ticket ticket = ticketDAO.getTicket("NON_EXISTING");
        assertNull(ticket);
    }
    
    @Test
    public void testGetTicketNullOrEmpty() throws Exception {
        assertNull(ticketDAO.getTicket(null));
        assertNull(ticketDAO.getTicket(""));
    }
    
    @Test
    public void testGetTicketSQLException() throws Exception {
        String vehicleRegNumber = "ABC123";
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        when(mockDataBaseConfig.getConnection()).thenThrow(new SQLException("Test SQL exception"));

        TicketDAO ticketDAO = new TicketDAO(mockDataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            ticketDAO.getTicket(vehicleRegNumber);
        });

        assertEquals("Test SQL exception", thrownException.getMessage());
    }

    @Test
    public void testUpdateTicketNullOrInvalidId() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(0);

        boolean result = ticketDAO.updateTicket(ticket);

        assertFalse(result);
    }
    
    @Test
    public void testDeleteAllTickets() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticket.setVehicleRegNumber("ABC123");
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());

        ticketDAO.saveTicket(ticket);

        ticketDAO.deleteAllTickets();

        assertNull(ticketDAO.getTicket("ABC123"));
    }
    
    @Test
    public void testGetNbTicket() throws Exception {
        String vehicleRegNumber = "ABC123";
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
        ticket.setVehicleRegNumber(vehicleRegNumber);
        ticket.setPrice(10.0);
        ticket.setInTime(LocalDateTime.now());

        ticketDAO.saveTicket(ticket);

        int count = ticketDAO.getNbTicket(vehicleRegNumber);

        assertEquals(1, count);
    }
    
    @Test
    public void testGetNbTicketNonExisting() throws Exception {
        int count = ticketDAO.getNbTicket("NON_EXISTING");
        assertEquals(0, count);
    }
    
    @Test
    public void testGetNbTicketNullOrEmpty() throws Exception {
        assertEquals(0, ticketDAO.getNbTicket(null));
        assertEquals(0, ticketDAO.getNbTicket(""));
    }
    
    @Test
    public void testGetNbTicketSQLException() throws Exception {
        String vehicleRegNumber = "ABC123";
        
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        when(mockDataBaseConfig.getConnection()).thenThrow(new SQLException("Test SQL exception"));

        TicketDAO ticketDAO = new TicketDAO(mockDataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            ticketDAO.getNbTicket(vehicleRegNumber);
        });

        assertEquals("Test SQL exception", thrownException.getMessage(), "Exception message should match.");
    }
    
    @Test
    public void testUpdateTicketNullTicket() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        TicketDAO dao = new TicketDAO(mockDataBaseConfig);

        boolean result = dao.updateTicket(null);

        assertFalse(result, "Update should fail with a null ticket.");
    }

    @Test
    public void testUpdateTicketInvalidId() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(0);
        ticket.setPrice(15.0);
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        TicketDAO dao = new TicketDAO(mockDataBaseConfig);

        boolean result = dao.updateTicket(ticket);

        assertFalse(result, "Update should fail with an invalid ticket ID.");
    }

    @Test
    public void testUpdateTicketSQLException() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setPrice(15.0);
        ticket.setOutTime(LocalDateTime.now().plusHours(1));

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);

        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_TICKET)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenThrow(new SQLException("Test SQL exception"));

        TicketDAO dao = new TicketDAO(mockDataBaseConfig);

        SQLException thrownException = assertThrows(SQLException.class, () -> {
            dao.updateTicket(ticket);
        });

        assertEquals("Test SQL exception", thrownException.getMessage());
    }

    @Test
    public void testUpdateTicketNullOutTime() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setPrice(15.0);
        ticket.setOutTime(null);

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        
        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_TICKET)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        TicketDAO dao = new TicketDAO(mockDataBaseConfig);

        boolean result = dao.updateTicket(ticket);

        assertTrue(result, "Ticket should be updated successfully even with a null out time.");
    }

    @Test
    public void testUpdateTicketNormalValues() throws Exception {
        Ticket ticket = new Ticket();
        ticket.setId(2);
        ticket.setPrice(20.0);
        ticket.setOutTime(LocalDateTime.now().plusHours(2));

        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(DBConstants.UPDATE_TICKET)).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        TicketDAO dao = new TicketDAO(mockDataBaseConfig);

        boolean result = dao.updateTicket(ticket);

        assertTrue(result, "Ticket should be updated successfully with normal values.");
    }

    @Test
    public void testDeleteAllParkingSpotsHandlesSQLException() throws Exception {
        DataBaseConfig mockDataBaseConfig = mock(DataBaseConfig.class);
        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockPreparedStatement = mock(PreparedStatement.class);
        when(mockDataBaseConfig.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement("DELETE FROM parking")).thenThrow(new SQLException("Database error"));

        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(mockDataBaseConfig);

        SQLException thrown = assertThrows(SQLException.class, () -> {
            parkingSpotDAO.deleteAllParkingSpots();
        });

        assertEquals("Database error", thrown.getMessage(), "SQLException should be thrown with the correct message.");
    }

}
