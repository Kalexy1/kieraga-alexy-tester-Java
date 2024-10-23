package com.parkit.parkingsystem.exception;

/**
 * Utility class for custom exceptions in the parking system.
 */
public class CustomException {

    /**
     * Exception thrown when a ticket fails to be saved.
     */
    public static class TicketSaveException extends Exception {
        public TicketSaveException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when a requested ticket is not found.
     */
    public static class TicketNotFoundException extends RuntimeException {
        public TicketNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when updating a parking spot fails.
     */
    public static class ParkingSpotUpdateException extends Exception {
        public ParkingSpotUpdateException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when updating a ticket fails.
     */
    public static class TicketUpdateException extends RuntimeException {
        public TicketUpdateException(String message) {
            super(message);
        }
        public TicketUpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when fare calculation fails.
     */
    public static class FareCalculationException extends RuntimeException {
        public FareCalculationException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Factory method to create an exception with a ticket ID.
         *
         * @param ticketId The ID of the ticket related to the error.
         * @param cause    The underlying cause of the error.
         * @return A new instance of FareCalculationException with a detailed message.
         */
        public static FareCalculationException createWithTicketId(int ticketId, Throwable cause) {
            return new FareCalculationException("Unable to calculate fare for ticket ID: " + ticketId, cause);
        }
    }

    /**
     * Exception thrown when a database operation fails.
     */
    public static class DatabaseException extends Exception {
        public DatabaseException(String message) {
            super(message);
        }

        public DatabaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when a requested parking spot is not found.
     */
    public static class ParkingSpotNotFoundException extends RuntimeException {
        public ParkingSpotNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Exception thrown when there are no available parking spots.
     */
    public static class NoAvailableParkingSpotException extends Exception {
        public NoAvailableParkingSpotException(String message) {
            super(message);
        }
    }

    public static class InvalidTicketException extends RuntimeException {
        public InvalidTicketException(String message) {
            super(message);
        }
    }

    public static class OutTimeBeforeInTimeException extends RuntimeException {
        public OutTimeBeforeInTimeException(String message) {
            super(message);
        }
    }
}
