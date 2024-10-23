package com.parkit.parkingsystem.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.exception.CustomException.InvalidTicketException;
import com.parkit.parkingsystem.exception.CustomException.OutTimeBeforeInTimeException;
import com.parkit.parkingsystem.model.Ticket;

/**
 * Service for calculating parking fares.
 */
public class FareCalculatorService {

    private static final Logger logger = LogManager.getLogger(FareCalculatorService.class);

    private static final double CAR_RATE_PER_HOUR = 1.0;
    private static final double BIKE_RATE_PER_HOUR = 0.5;
    private static final double DISCOUNT_RATE = 0.95;
    private static final long FREE_PARKING_DURATION_IN_MINUTES = 30;

    private java.time.Clock clock = java.time.Clock.systemDefaultZone();

    /**
     * Calculates the parking fare based on the ticket information and user status.
     *
     * @param ticket The ticket containing parking information.
     * @param discount Indicates if the user is eligible for a discount.
     * @return The calculated fare.
     * @throws IllegalArgumentException If in-time or out-time is invalid.
     */
    public double calculateFare(Ticket ticket, boolean discount) {
        validateTicket(ticket);

        long duration = Duration.between(ticket.getInTime(), ticket.getOutTime()).toMinutes();
        if (duration <= 0) {
            throw new IllegalArgumentException("Exit time must be after entry time.");
        }

        double ratePerHour = getRatePerHour(ticket.getParkingSpot().getParkingType());

        double durationInHours = Math.max(0, (duration - FREE_PARKING_DURATION_IN_MINUTES) / 60.0);
        double fare = Math.ceil(durationInHours) * ratePerHour;

        if (discount) {
            fare *= DISCOUNT_RATE;
        }

        return fare;
    }

    /**
     * Validates the in-time and out-time of the ticket.
     *
     * @param ticket The ticket to validate.
     * @throws InvalidTicketException If in-time or out-time is null or in-time is in the future.
     * @throws OutTimeBeforeInTimeException If out-time is before in-time.
     */
    public void validateTicket(Ticket ticket) throws InvalidTicketException, OutTimeBeforeInTimeException {
        if (ticket.getInTime() == null || ticket.getOutTime() == null) {
            throw new InvalidTicketException(
                String.format("In-time or out-time is not set. In-time: %s, Out-time: %s",
                    ticket.getInTime(), ticket.getOutTime()));
        }

        if (ticket.getInTime().isAfter(LocalDateTime.now(clock))) {
            throw new InvalidTicketException(
                String.format("In-time is in the future: %s", ticket.getInTime()));
        }

        if (ticket.getInTime().isAfter(ticket.getOutTime())) {
            throw new OutTimeBeforeInTimeException(
                String.format("Out-time is before in-time. In-time: %s, Out-time: %s",
                    ticket.getInTime(), ticket.getOutTime()));
        }
    }

    /**
     * Gets the rate per hour based on the parking type.
     *
     * @param type The parking type.
     * @return The rate per hour.
     * @throws IllegalArgumentException If the parking type is unknown.
     */
    public double getRatePerHour(ParkingType type) {
        if (type == null) {
            String message = "Unknown parking type: null";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }

        switch (type) {
            case CAR:
                return CAR_RATE_PER_HOUR;
            case BIKE:
                return BIKE_RATE_PER_HOUR;
            default:
                String message = "Unknown parking type: " + type;
                logger.error(message);
                throw new IllegalArgumentException(message);
        }
    }
}
