package com.parkit.parkingsystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.exception.CustomException;
import com.parkit.parkingsystem.exception.CustomException.InvalidTicketException;
import com.parkit.parkingsystem.exception.CustomException.OutTimeBeforeInTimeException;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.FareCalculatorService;

public class FareCalculatorServiceTest {

    private FareCalculatorService fareCalculatorService;
    private static final double BIKE_RATE = 0.5;
    private static final double CAR_RATE = 1.0;
    private static final double DISCOUNT_RATE = 0.95;
    private static final double FREE_PARKING_DURATION = 30; // minutes
    private static final Logger logger = LogManager.getLogger(FareCalculatorService.class);

    private final LocalDateTime FIXED_TIME = LocalDateTime.of(2024, 8, 20, 9, 0);
    private Clock clock;

    @BeforeEach
    public void setUp() {
        fareCalculatorService = new FareCalculatorService();
        clock = Clock.fixed(FIXED_TIME.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    }

    @Test
    public void testCalculateFareBikeLessThan30Minutes() {
        Ticket ticket = createTicket(ParkingType.BIKE, 29);
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(0.0, fare, "Bike parking less than 30 minutes should be free.");
    }

    @Test
    public void testCalculateFareBikeExactly30Minutes() {
        Ticket ticket = createTicket(ParkingType.BIKE, 30);
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(0.0, fare, "Bike parking exactly 30 minutes should be free.");
    }

    @Test
    public void testCalculateFareBikeMoreThan30MinutesWithoutDiscount() {
        Ticket ticket = createTicket(ParkingType.BIKE, 60);
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(BIKE_RATE, fare, "Bike parking for 1 hour should cost 0.5.");
    }

    @Test
    public void testCalculateFareBikeMoreThan30MinutesWithDiscount() {
        Ticket ticket = createTicket(ParkingType.BIKE, 60);
        double fare = fareCalculatorService.calculateFare(ticket, true);
        assertEquals(BIKE_RATE * DISCOUNT_RATE, fare, "Bike parking for 1 hour with discount should cost 0.475.");
    }

    @Test
    public void testCalculateFareCarLessThan30Minutes() {
        Ticket ticket = createTicket(ParkingType.CAR, 29);
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(0.0, fare, "Car parking less than 30 minutes should be free.");
    }

    @Test
    public void testCalculateFareCarExactly30Minutes() {
        Ticket ticket = createTicket(ParkingType.CAR, 30);
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(0.0, fare, "Car parking exactly 30 minutes should be free.");
    }

    @Test
    public void testCalculateFareCarMoreThan30MinutesWithoutDiscount() {
        Ticket ticket = createTicket(ParkingType.CAR, 90); // 90 minutes
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(CAR_RATE, fare, "Car parking for 1.5 hours should cost 1.0.");
    }

    @Test
    public void testCalculateFareCarMoreThan30MinutesWithDiscount() {
        Ticket ticket = createTicket(ParkingType.CAR, 90); // 90 minutes
        double fare = fareCalculatorService.calculateFare(ticket, true);
        assertEquals(CAR_RATE * DISCOUNT_RATE, fare, "Car parking for 1.5 hours with discount should cost 0.95.");
    }

    @Test
    public void testCalculateFareBikeFor45MinutesWithoutDiscount() {
        Ticket ticket = createTicket(ParkingType.BIKE, 45); // 45 minutes
        double fare = fareCalculatorService.calculateFare(ticket, false);
        assertEquals(BIKE_RATE, fare, 0.0001, "Bike parking for 45 minutes should cost 0.5.");
    }

    @Test
    public void testCalculateFareCarFor15MinutesWithDiscount() {
        Ticket ticket = createTicket(ParkingType.CAR, 15); // 15 minutes
        double fare = fareCalculatorService.calculateFare(ticket, true);
        assertEquals(0.0, fare, "Car parking for 15 minutes with discount should be free.");
    }
    
    @Test
    public void testCalculateFareWithZeroDuration() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.now());
        ticket.setOutTime(LocalDateTime.now()); // Same in-time and out-time

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false);
        });

        assertEquals("Exit time must be after entry time.", thrown.getMessage());
    }

    @Test
    public void testCalculateFareWithNegativeDuration() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.now().plusDays(1)); // inTime is in the future
        ticket.setOutTime(LocalDateTime.now()); // outTime is in the past

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false);
        });

        assertEquals("In-time is in the future: " + ticket.getInTime(), thrown.getMessage());
    }
    
    @Test
    public void testCalculateFareWithInTimeNull() {
        Ticket ticket = new Ticket();
        ticket.setOutTime(LocalDateTime.of(2024, 8, 20, 9, 0)); // outTime defined but inTime is null

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false); // Updated to include the boolean argument
        });

        assertEquals("In-time or out-time is not set. In-time: null, Out-time: 2024-08-20T09:00", thrown.getMessage());
    }

    @Test
    public void testCalculateFareWithOutTimeNull() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.of(2024, 8, 20, 8, 30)); // inTime defined but outTime is null

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false); // Updated to include the boolean argument
        });

        assertEquals("In-time or out-time is not set. In-time: 2024-08-20T08:30, Out-time: null", thrown.getMessage());
    }

    @Test
    public void testCalculateFareWithOutTimeBeforeInTime() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.of(2024, 8, 20, 8, 30));
        ticket.setOutTime(LocalDateTime.of(2024, 8, 20, 8, 0)); // outTime before inTime

        OutTimeBeforeInTimeException thrown = assertThrows(OutTimeBeforeInTimeException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false); // Updated to include the boolean argument
        });

        assertEquals("Out-time is before in-time. In-time: 2024-08-20T08:30, Out-time: 2024-08-20T08:00", thrown.getMessage());
    }

    @Test
    public void testCalculateFareWithFutureInTime() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.of(2024, 8, 21, 9, 10)); // inTime in the future
        ticket.setOutTime(LocalDateTime.of(2024, 8, 20, 9, 0)); // outTime is before inTime

        OutTimeBeforeInTimeException thrown = assertThrows(OutTimeBeforeInTimeException.class, () -> {
            fareCalculatorService.calculateFare(ticket, false);
        });

        assertEquals("Out-time is before in-time. In-time: 2024-08-21T09:10, Out-time: 2024-08-20T09:00", thrown.getMessage());
    }

    @Test
    public void testGetRatePerHourWithNullType() {
        // Given
        ParkingType nullType = null;

        // When
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            fareCalculatorService.getRatePerHour(nullType);
        });

        // Then
        String expectedMessage = "Unknown parking type: null";
        assertEquals(expectedMessage, thrown.getMessage(), "Exception message should match the expected message.");
    }

    @Test
    public void testGetRatePerHourWithCarType() {
        // Given
        ParkingType carType = ParkingType.CAR;

        // When
        double rate = fareCalculatorService.getRatePerHour(carType);

        // Then
        assertEquals(CAR_RATE, rate, "Rate per hour for CAR should be 1.0.");
    }

    @Test
    public void testGetRatePerHourWithBikeType() {
        // Given
        ParkingType bikeType = ParkingType.BIKE;

        // When
        double rate = fareCalculatorService.getRatePerHour(bikeType);

        // Then
        assertEquals(BIKE_RATE, rate, "Rate per hour for BIKE should be 0.5.");
    }
    
    @Test
    public void testValidateTicketValid() {
        Ticket ticket = createTicket(ParkingType.CAR, 60);
        // Should not throw any exception
        fareCalculatorService.validateTicket(ticket);
    }
    
    @Test
    public void testValidateTicketInTimeNull() {
        Ticket ticket = createTicket(ParkingType.CAR, 60);
        ticket.setInTime(null);

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.validateTicket(ticket);
        });

        assertEquals("In-time or out-time is not set. In-time: null, Out-time: 2024-08-20T09:00", thrown.getMessage());
    }

    @Test
    public void testValidateTicketOutTimeNull() {
        Ticket ticket = createTicket(ParkingType.CAR, 60);
        ticket.setOutTime(null);

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.validateTicket(ticket);
        });

        assertEquals("In-time or out-time is not set. In-time: 2024-08-20T08:00, Out-time: null", thrown.getMessage());
    }

    @Test
    public void testValidateTicketInTimeInFuture() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.now().plusDays(1)); // inTime in the future
        ticket.setOutTime(LocalDateTime.now()); // outTime is now

        InvalidTicketException thrown = assertThrows(InvalidTicketException.class, () -> {
            fareCalculatorService.validateTicket(ticket);
        });

        assertEquals("In-time is in the future: " + ticket.getInTime(), thrown.getMessage());
    }

    @Test
    public void testValidateTicketOutTimeBeforeInTime() {
        Ticket ticket = new Ticket();
        ticket.setInTime(LocalDateTime.of(2024, 8, 20, 9, 10));
        ticket.setOutTime(LocalDateTime.of(2024, 8, 20, 8, 0)); // outTime before inTime

        OutTimeBeforeInTimeException thrown = assertThrows(OutTimeBeforeInTimeException.class, () -> {
            fareCalculatorService.validateTicket(ticket);
        });

        assertEquals("Out-time is before in-time. In-time: 2024-08-20T09:10, Out-time: 2024-08-20T08:00", thrown.getMessage());
    }
    
    @Test
    public void testGetRatePerHourUnknownType() {
        // Act & Assert
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            fareCalculatorService.getRatePerHour(null);
        });
        assertEquals("Unknown parking type: null", thrown.getMessage());
    }

    private Ticket createTicket(ParkingType type, int minutes) {
        Ticket ticket = new Ticket();
        ticket.setParkingSpot(new ParkingSpot(1, type, false));
        ticket.setInTime(FIXED_TIME.minusMinutes(minutes));
        ticket.setOutTime(FIXED_TIME);
        return ticket;
    }
}
