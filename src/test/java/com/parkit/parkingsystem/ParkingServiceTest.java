package com.parkit.parkingsystem;

import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.exception.CustomException;
import com.parkit.parkingsystem.exception.CustomException.DatabaseException;
import com.parkit.parkingsystem.exception.CustomException.FareCalculationException;
import com.parkit.parkingsystem.exception.CustomException.NoAvailableParkingSpotException;
import com.parkit.parkingsystem.exception.CustomException.ParkingSpotNotFoundException;
import com.parkit.parkingsystem.exception.CustomException.ParkingSpotUpdateException;
import com.parkit.parkingsystem.exception.CustomException.TicketNotFoundException;
import com.parkit.parkingsystem.exception.CustomException.TicketSaveException;
import com.parkit.parkingsystem.exception.CustomException.TicketUpdateException;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

public class ParkingServiceTest {

    @InjectMocks
    private ParkingService parkingService;

    @Mock
    private InputReaderUtil inputReaderUtil;

    @Mock
    private ParkingSpotDAO parkingSpotDAO;

    @Mock
    private TicketDAO ticketDAO;

    @Mock
    private FareCalculatorService fareCalculatorService;

    private static final String NULL_OR_EMPTY_REG_NUMBER_MSG = "Vehicle registration number cannot be null or empty";
    private static final String INVALID_REG_NUMBER_LENGTH_MSG = "Vehicle registration number must be between 2 and 10 characters long";
    private static final int MIN_REG_NUMBER_LENGTH = 2;
    private static final int MAX_REG_NUMBER_LENGTH = 10;
    private static final ParkingType VALID_PARKING_TYPE = ParkingType.CAR;
    private static final ParkingType INVALID_PARKING_TYPE = null;

    private static final String DATABASE_ERROR_MSG = "Database error occurred while ";


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);
        parkingService.addParkingSpot(new ParkingSpot(1, ParkingType.CAR, true));
    }

    private String getTestVehicleRegNumber() {
        return "ABC123";
    }

    private ParkingType getTestParkingType() {
        return ParkingType.CAR;
    }
    
    private ParkingSpot createTestParkingSpot() {
        return new ParkingSpot(1, VALID_PARKING_TYPE, true);
    }
    
    private ParkingSpot createTestParkingSpot(boolean available) {
        return new ParkingSpot(1, getTestParkingType(), available);
    }

    private <T extends Throwable> T assertThrowsWithMessage(Class<T> expectedType, Executable executable, String expectedMessage) {
        T thrown = assertThrows(expectedType, executable);
        assertEquals(expectedMessage, thrown.getMessage(), "Exception message does not match");
        return thrown;
    }

    private Ticket createTestTicket() {
        Ticket ticket = new Ticket();
        ticket.setId(0);
        ticket.setInTime(LocalDateTime.now().minusHours(1));
        ticket.setOutTime(LocalDateTime.now());
        ticket.setParkingSpot(createTestParkingSpot(false));
        return ticket;
    }

    @Test
    public void testProcessIncomingVehicle() throws Exception {
        String vehicleRegNumber = getTestVehicleRegNumber();
        ParkingType parkingType = getTestParkingType();
        ParkingSpot parkingSpot = createTestParkingSpot(true);

        when(parkingSpotDAO.getNextAvailableSpot(parkingType)).thenReturn(parkingSpot);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(parkingSpot, false)).thenReturn(true);

        parkingService.processIncomingVehicle(vehicleRegNumber, parkingType);

        verify(parkingSpotDAO).getNextAvailableSpot(parkingType);
        verify(ticketDAO).saveTicket(any(Ticket.class));
        verify(parkingSpotDAO).updateParking(parkingSpot, false);
    }

    @Test
    public void processIncomingVehicle_shouldSaveTicketAndUpdateSpot_whenSpotIsAvailable() throws Exception {
        ParkingSpot parkingSpot = createTestParkingSpot(true);
        when(parkingSpotDAO.getNextAvailableSpot(any(ParkingType.class))).thenReturn(parkingSpot);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class), eq(false))).thenReturn(true);

        parkingService.processIncomingVehicle(getTestVehicleRegNumber(), getTestParkingType());

        verify(ticketDAO, times(1)).saveTicket(any(Ticket.class));
        verify(parkingSpotDAO, times(1)).updateParking(parkingSpot, false);
    }
    
    @Test
    public void processIncomingVehicle_shouldHandleParkingSpotUpdateException_whenUnableToUpdateSpot() throws Exception {
        ParkingSpot parkingSpot = createTestParkingSpot(true);
        when(parkingSpotDAO.getNextAvailableSpot(any(ParkingType.class))).thenReturn(parkingSpot);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class), eq(false))).thenReturn(false);

        ParkingSpotUpdateException thrown = assertThrows(ParkingSpotUpdateException.class, () -> parkingService.processIncomingVehicle(getTestVehicleRegNumber(), getTestParkingType()));

        assertEquals("Unable to update parking spot availability for parking spot ID: " + parkingSpot.getId(), thrown.getMessage());
    }

    @Test
    public void processIncomingVehicle_shouldHandleSQLException_whenDatabaseErrorOccurs() throws Exception {
        String vehicleRegNumber = getTestVehicleRegNumber();
        ParkingType parkingType = getTestParkingType();
        SQLException sqlException = new SQLException("Database error");

        when(parkingSpotDAO.getNextAvailableSpot(parkingType)).thenThrow(sqlException);

        DatabaseException thrown = assertThrows(DatabaseException.class, () -> parkingService.processIncomingVehicle(vehicleRegNumber, parkingType));

        assertEquals("Database error occurred while processing 'incoming vehicle': " + sqlException.getMessage(), thrown.getMessage());
    }

    @Test
    public void processIncomingVehicle_shouldHandleTicketSaveException_whenUnableToSaveTicket() throws Exception {
        ParkingSpot parkingSpot = createTestParkingSpot(true);
        when(parkingSpotDAO.getNextAvailableSpot(any(ParkingType.class))).thenReturn(parkingSpot);
        when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(false);

        TicketSaveException thrown = assertThrows(TicketSaveException.class, () -> parkingService.processIncomingVehicle(getTestVehicleRegNumber(), getTestParkingType()));

        assertEquals("Failed to save ticket for vehicle: " + getTestVehicleRegNumber(), thrown.getMessage());
    }

    @Test
    public void processIncomingVehicle_shouldThrowIllegalArgumentException_whenInvalidVehicleRegNumber() throws ClassNotFoundException, SQLException {
        String invalidVehicleRegNumber = null;
        ParkingType parkingType = getTestParkingType();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.processIncomingVehicle(invalidVehicleRegNumber, parkingType);
        });

        assertEquals(NULL_OR_EMPTY_REG_NUMBER_MSG, thrown.getMessage());
        verify(parkingSpotDAO, never()).getNextAvailableSpot(any(ParkingType.class));
        verify(ticketDAO, never()).saveTicket(any(Ticket.class));
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class), eq(false));
    }

    @Test
    public void processIncomingVehicle_shouldThrowIllegalArgumentException_whenInvalidParkingType() throws Exception {
        String vehicleRegNumber = "ABC123";
        ParkingType nullParkingType = null;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.processIncomingVehicle(vehicleRegNumber, nullParkingType);
        });

        assertEquals("Parking type cannot be null", thrown.getMessage());
    }

    @Test
    public void processIncomingVehicle_shouldThrowIllegalArgumentException_whenRegNumberIsTooShort() {
        String shortRegNumber = "";
        ParkingType parkingType = ParkingType.CAR;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.processIncomingVehicle(shortRegNumber, parkingType);
        });

        assertEquals("Vehicle registration number cannot be null or empty", thrown.getMessage());
    }

    @Test
    public void processIncomingVehicle_shouldThrowIllegalArgumentException_whenRegNumberIsTooLong() {
        String longRegNumber = "LONGREGNUMBER";
        ParkingType parkingType = ParkingType.CAR;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.processIncomingVehicle(longRegNumber, parkingType);
        });

        assertEquals("Vehicle registration number must be between 2 and 10 characters long", thrown.getMessage());
    }
    
    @Test
    public void processIncomingVehicle_shouldNotThrowException_whenRegNumberIsValid() throws ClassNotFoundException, DatabaseException, NoAvailableParkingSpotException, ParkingSpotUpdateException, TicketSaveException, SQLException {
        String validRegNumber = "ABC123";
        ParkingType testParkingType = getTestParkingType();

        ParkingService mockParkingService = mock(ParkingService.class);
        
        doNothing().when(mockParkingService).processIncomingVehicle(validRegNumber, testParkingType);

        try {
            mockParkingService.processIncomingVehicle(validRegNumber, testParkingType);
        } catch (IllegalArgumentException e) {
            fail("Unexpected exception: " + e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception thrown: " + e.getMessage());
        }
    }
    
    @Test
    public void validateVehicleRegistration_shouldThrowIllegalArgumentException_whenVehicleRegNumberIsNull() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.validateVehicleRegistration(null);
        });
        assertEquals(NULL_OR_EMPTY_REG_NUMBER_MSG, thrown.getMessage());
    }

    @Test
    public void validateVehicleRegistration_shouldThrowIllegalArgumentException_whenVehicleRegNumberIsEmpty() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.validateVehicleRegistration("");
        });
        assertEquals(NULL_OR_EMPTY_REG_NUMBER_MSG, thrown.getMessage());
    }
    
    @Test
    public void validateVehicleRegistration_shouldThrowException_whenVehicleRegNumberIsSpaces() {
        String vehicleRegNumber = "     ";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.validateVehicleRegistration(vehicleRegNumber);
        });
        assertEquals(ParkingService.NULL_OR_EMPTY_REG_NUMBER_MSG, thrown.getMessage());
    }

    @Test
    public void validateVehicleRegistration_shouldThrowIllegalArgumentException_whenVehicleRegNumberIsWhitespace() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.validateVehicleRegistration("   ");
        });
        assertEquals(NULL_OR_EMPTY_REG_NUMBER_MSG, thrown.getMessage());
    }

    @Test
    public void validateVehicleRegistration_shouldThrowIllegalArgumentException_whenVehicleRegNumberIsTooShort() {
        String tooShortRegNumber = "A";

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.validateVehicleRegistration(tooShortRegNumber);
        });

        assertEquals(INVALID_REG_NUMBER_LENGTH_MSG, thrown.getMessage());
    }

    @Test
    public void validateVehicleRegistration_shouldNotThrowException_whenVehicleRegNumberIsValid() {
        try {
            parkingService.validateVehicleRegistration("ABC123");
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Unexpected IllegalArgumentException thrown");
        }
    }

    @Test
    public void validateVehicleRegistration_shouldNotThrowException_whenVehicleRegNumberIsValidWithTrim() {
        try {
            parkingService.validateVehicleRegistration("  ABC123  ");
        } catch (IllegalArgumentException e) {
            throw new AssertionError("Unexpected IllegalArgumentException thrown");
        }
    }

    @Test
    public void calculateFare_shouldReturnCorrectFare_whenCalculationIsSuccessful() {
        Ticket ticket = createTestTicket();
        double expectedFare = 10.0;
        when(fareCalculatorService.calculateFare(ticket, false)).thenReturn(expectedFare);

        double actualFare = fareCalculatorService.calculateFare(ticket, false);

        assertEquals(expectedFare, actualFare, "The fare calculated should be as expected.");
        verify(fareCalculatorService).calculateFare(ticket, false);
    }

    @Test
    public void updateTicketAndParkingSpot_shouldThrowTicketUpdateException_whenTicketIsNull() throws ClassNotFoundException, SQLException {
        Ticket nullTicket = null;
        double fare = 10.0;

        TicketUpdateException thrown = assertThrows(TicketUpdateException.class, () -> {
            parkingService.updateTicketAndParkingSpot(nullTicket, fare);
        });

        assertEquals("Ticket cannot be null", thrown.getMessage());
        verify(ticketDAO, never()).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class), eq(true));
    }

    @Test
    public void updateTicketAndParkingSpot_shouldUpdateTicketAndParkingSpot_whenAllOperationsSucceed() throws Exception {
        Ticket ticket = createTestTicket();
        double fare = 10.0;
        when(ticketDAO.updateTicket(ticket)).thenReturn(true);
        when(parkingSpotDAO.updateParking(ticket.getParkingSpot(), true)).thenReturn(true);

        parkingService.updateTicketAndParkingSpot(ticket, fare);

        assertEquals(fare, ticket.getPrice(), "The ticket price should be updated to the fare.");
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO).updateParking(ticket.getParkingSpot(), true);
    }

    @Test
    public void updateTicketAndParkingSpot_shouldThrowTicketUpdateException_whenTicketUpdateFails() throws Exception {
        Ticket ticket = createTestTicket();
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false);

        TicketUpdateException thrown = assertThrows(TicketUpdateException.class, () -> {
            parkingService.updateTicketAndParkingSpot(ticket, 10.0);
        });

        assertEquals("Unable to update the ticket for ticket ID: " + ticket.getId(), thrown.getMessage());
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class), eq(true));
    }

    @Test
    public void updateTicketAndParkingSpot_shouldThrowParkingSpotUpdateException_whenParkingSpotUpdateFails() throws Exception {
        Ticket ticket = createTestTicket();
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class), eq(true))).thenReturn(false);

        ParkingSpotUpdateException thrown = assertThrows(ParkingSpotUpdateException.class, () -> {
            parkingService.updateTicketAndParkingSpot(ticket, 10.0);
        });

        assertEquals("Failed to update parking spot availability for spot ID: " + ticket.getParkingSpot().getId(), thrown.getMessage());
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO).updateParking(ticket.getParkingSpot(), true);
    }

    @Test
    public void updateTicketAndParkingSpot_shouldThrowTicketUpdateException_whenParkingSpotIsNull() throws Exception {
        Ticket ticket = createTestTicket();
        ticket.setParkingSpot(null);
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
        TicketUpdateException thrown = assertThrows(TicketUpdateException.class, () -> {
            parkingService.updateTicketAndParkingSpot(ticket, 10.0);
        });
        assertEquals("Parking spot information is missing for ticket ID: " + ticket.getId(), thrown.getMessage());
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class), eq(true));
    }
    
    @Test
    public void processExitingVehicle_shouldThrowTicketNotFoundException_whenNoTicketIsFound() throws Exception {
        String vehicleRegNumber = "ABC123";
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(null);

        TicketNotFoundException thrown = assertThrows(TicketNotFoundException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("No ticket found for vehicle registration number: " + vehicleRegNumber, thrown.getMessage());
        verify(ticketDAO).getTicket(vehicleRegNumber);
    }
    
    @Test
    public void processExitingVehicle_shouldHandleEmptyVehicleRegNumber() throws Exception {
        String vehicleRegNumber = "";
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(null);

        TicketNotFoundException thrown = assertThrows(TicketNotFoundException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("No ticket found for vehicle registration number: " + vehicleRegNumber, thrown.getMessage());
        verify(ticketDAO).getTicket(vehicleRegNumber);
    }

    @Test
    public void processExitingVehicle_shouldHandleSQLException() throws Exception {
        String vehicleRegNumber = "LMN456";
        when(ticketDAO.getTicket(vehicleRegNumber)).thenThrow(new SQLException("Database error"));

        Exception thrown = assertThrows(SQLException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("Database error", thrown.getMessage());
        verify(ticketDAO).getTicket(vehicleRegNumber);
    }

    @Test
    public void processExitingVehicle_shouldProcessTicket_whenTicketIsFound() throws Exception {
        String vehicleRegNumber = "XYZ789";
        Ticket ticket = createTestTicket();
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(ticket);
        double fare = 10.0;
        when(fareCalculatorService.calculateFare(ticket, false)).thenReturn(fare);
        when(ticketDAO.updateTicket(ticket)).thenReturn(true);
        when(parkingSpotDAO.updateParking(ticket.getParkingSpot(), true)).thenReturn(true);

        parkingService.processExitingVehicle(vehicleRegNumber);

        verify(ticketDAO).getTicket(vehicleRegNumber);
        verify(fareCalculatorService).calculateFare(ticket, false);
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO).updateParking(ticket.getParkingSpot(), true);
    }

    @Test
    public void processExitingVehicle_shouldCalculateFareAndUpdateTicket_whenTicketIsValid() throws Exception {
        String vehicleRegNumber = getTestVehicleRegNumber();
        Ticket ticket = createTestTicket();
        double fare = 10.0;
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(ticket);
        when(fareCalculatorService.calculateFare(ticket, false)).thenReturn(fare);
        when(ticketDAO.updateTicket(ticket)).thenReturn(true);
        when(parkingSpotDAO.updateParking(ticket.getParkingSpot(), true)).thenReturn(true);

        parkingService.processExitingVehicle(vehicleRegNumber);

        verify(ticketDAO).getTicket(vehicleRegNumber);
        verify(fareCalculatorService).calculateFare(ticket, false);
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO).updateParking(ticket.getParkingSpot(), true);
    }

    @Test
    public void processExitingVehicle_shouldHandleFareCalculationException_whenFareCalculationFails() throws ClassNotFoundException, SQLException {
        String vehicleRegNumber = "ABC123";
        Ticket ticket = new Ticket();
        ticket.setId(0);
        
        ticket.setInTime(LocalDateTime.now().minusHours(1));

        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(ticket);
        
        doThrow(new RuntimeException("Database error")).when(fareCalculatorService).calculateFare(any(Ticket.class), eq(false));

        FareCalculationException thrownException = assertThrows(FareCalculationException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("Unable to calculate fare for ticket ID: 0", thrownException.getMessage());
    }

    @Test
    public void processExitingVehicle_shouldHandleTicketUpdateException_whenTicketUpdateFails() throws Exception {
        String vehicleRegNumber = getTestVehicleRegNumber();
        Ticket ticket = createTestTicket();
        double fare = 10.0;
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(ticket);
        when(fareCalculatorService.calculateFare(ticket, false)).thenReturn(fare);
        when(ticketDAO.updateTicket(ticket)).thenReturn(false);

        TicketUpdateException thrown = assertThrows(TicketUpdateException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("Unable to update the ticket for ticket ID: " + ticket.getId(), thrown.getMessage());
        verify(ticketDAO).getTicket(vehicleRegNumber);
        verify(fareCalculatorService).calculateFare(ticket, false);
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class), eq(true));
    }

    @Test
    public void processExitingVehicle_shouldHandleParkingSpotUpdateException_whenParkingSpotUpdateFails() throws Exception {
        String vehicleRegNumber = getTestVehicleRegNumber();
        Ticket ticket = createTestTicket();
        double fare = 10.0;
        when(ticketDAO.getTicket(vehicleRegNumber)).thenReturn(ticket);
        when(fareCalculatorService.calculateFare(ticket, false)).thenReturn(fare);
        when(ticketDAO.updateTicket(ticket)).thenReturn(true);
        when(parkingSpotDAO.updateParking(ticket.getParkingSpot(), true)).thenReturn(false);

        ParkingSpotUpdateException thrown = assertThrows(ParkingSpotUpdateException.class, () -> {
            parkingService.processExitingVehicle(vehicleRegNumber);
        });

        assertEquals("Unable to update parking spot availability for parking spot ID: " + ticket.getParkingSpot().getId(), thrown.getMessage());
        verify(ticketDAO).getTicket(vehicleRegNumber);
        verify(fareCalculatorService).calculateFare(ticket, false);
        verify(ticketDAO).updateTicket(ticket);
        verify(parkingSpotDAO).updateParking(ticket.getParkingSpot(), true);
    }
    
    @Test
    public void getNextParkingNumberIfAvailable_shouldThrowIllegalArgumentException_whenParkingTypeIsNull() throws Exception {
        ParkingType nullParkingType = null;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.getNextParkingNumberIfAvailable(nullParkingType);
        });

        assertEquals("Parking type cannot be null", thrown.getMessage());
        verify(parkingSpotDAO, never()).getNextAvailableSpot(any(ParkingType.class));
    }

    @Test
    public void getNextParkingNumberIfAvailable_shouldReturnNull_whenNoSpotIsAvailable() throws Exception {
        when(parkingSpotDAO.getNextAvailableSpot(VALID_PARKING_TYPE)).thenReturn(null);

        ParkingSpot actualSpot = parkingService.getNextParkingNumberIfAvailable(VALID_PARKING_TYPE);

        assertNull(actualSpot);
        verify(parkingSpotDAO).getNextAvailableSpot(VALID_PARKING_TYPE);
    }

    @Test
    public void getNextParkingNumberIfAvailable_shouldThrowIllegalArgumentException_whenParkingTypeIsInvalid() throws Exception {
        ParkingType nullParkingType = null;

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.getNextParkingNumberIfAvailable(nullParkingType);
        });

        assertEquals("Parking type cannot be null", thrown.getMessage());
        verify(parkingSpotDAO, never()).getNextAvailableSpot(any(ParkingType.class));
    }

    @Test
    public void getNextParkingNumberIfAvailable_shouldThrowDatabaseException_whenSQLExceptionIsThrown() throws Exception {
        ParkingType validParkingType = ParkingType.BIKE;
        when(parkingSpotDAO.getNextAvailableSpot(validParkingType)).thenThrow(new SQLException("Database error"));

        DatabaseException thrown = assertThrows(DatabaseException.class, () -> {
            parkingService.getNextParkingNumberIfAvailable(validParkingType);
        });

        assertEquals("Database error: getting next parking number: Database error", thrown.getMessage());
        verify(parkingSpotDAO).getNextAvailableSpot(validParkingType);
    }

    @Test
    public void getNextParkingNumberIfAvailable_shouldThrowIllegalArgumentException_whenDAOThrowsIllegalArgumentException() throws Exception {
        ParkingType invalidParkingType = ParkingType.CAR;
        when(parkingSpotDAO.getNextAvailableSpot(invalidParkingType)).thenThrow(new IllegalArgumentException("Invalid argument"));

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            parkingService.getNextParkingNumberIfAvailable(invalidParkingType);
        });

        assertEquals("Unknown parking type: CAR", thrown.getMessage());
        verify(parkingSpotDAO).getNextAvailableSpot(invalidParkingType);
    }

    @Test
    public void testCreateNewTicket() {
        String vehicleRegNumber = "ABC123";
        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, true);
        
        Ticket ticket = parkingService.createNewTicket(vehicleRegNumber, parkingSpot);

        assertNotNull(ticket, "The ticket should not be null.");
        assertEquals(vehicleRegNumber, ticket.getVehicleRegNumber(), "The vehicle registration number should match.");
        assertEquals(parkingSpot, ticket.getParkingSpot(), "The parking spot should match.");
        assertNotNull(ticket.getInTime(), "The inTime should not be null.");
        
        assertTrue(LocalDateTime.now().minusSeconds(10).isBefore(ticket.getInTime()), "The inTime should be recent.");
        assertTrue(LocalDateTime.now().plusSeconds(10).isAfter(ticket.getInTime()), "The inTime should be recent.");
    }
    
}
