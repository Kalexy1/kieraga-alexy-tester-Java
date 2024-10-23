package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.util.InputReaderUtil;
import com.parkit.parkingsystem.constants.ParkingType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides an interactive shell interface for managing parking system operations.
 */
public class InteractiveShell {

    private static final Logger logger = LogManager.getLogger(InteractiveShell.class);

    private final ParkingService parkingService;
    private final InputReaderUtil inputReaderUtil;

    /**
     * Constructs an InteractiveShell instance with required services and utilities.
     *
     * @param parkingService  Service responsible for parking operations
     * @param inputReaderUtil Utility for reading user inputs
     */
    public InteractiveShell(ParkingService parkingService, InputReaderUtil inputReaderUtil) {
        this.parkingService = parkingService;
        this.inputReaderUtil = inputReaderUtil;
    }

    /**
     * Initializes the application and starts the interactive shell.
     */
    public void loadInterface() {
        logger.info("App initialized!!!");
        System.out.println("Welcome to Parking System!");

        boolean continueApp = true;

        while (continueApp) {
            try {
                displayMenu();
                int option = inputReaderUtil.readSelection();
                switch (option) {
                    case 1:
                        handleIncomingVehicle();
                        break;
                    case 2:
                        handleExitingVehicle();
                        break;
                    case 3:
                        System.out.println("Exiting from the system!");
                        continueApp = false;
                        break;
                    default:
                        System.out.println("Unsupported option. Please enter a number corresponding to the provided menu.");
                }
            } catch (Exception e) {
                logger.error("An error occurred while processing your request", e);
                System.out.println("An error occurred: " + e.getMessage());
            }
        }
    }

    /**
     * Displays the main menu of the parking system.
     */
    private void displayMenu() {
        System.out.println("Please select an option. Simply enter the number to choose an action:");
        System.out.println("1. New Vehicle Entering - Allocate Parking Space");
        System.out.println("2. Vehicle Exiting - Generate Ticket Price");
        System.out.println("3. Shutdown System");
    }

    /**
     * Handles the process of an incoming vehicle by saving the ticket and updating the parking spot.
     */
    private void handleIncomingVehicle() {
        try {
            ParkingType parkingType = getParkingType();
            String vehicleRegNumber = getVehicleRegNumber();
            parkingService.processIncomingVehicle(vehicleRegNumber, parkingType);
        } catch (Exception e) {
            logger.error("Error while processing incoming vehicle", e);
            System.out.println("An error occurred while processing the incoming vehicle: " + e.getMessage());
        }
    }

    /**
     * Handles the process of an exiting vehicle by calculating the fare and updating the ticket.
     */
    private void handleExitingVehicle() {
        try {
            String vehicleRegNumber = getVehicleRegNumber();
            parkingService.processExitingVehicle(vehicleRegNumber);
        } catch (Exception e) {
            logger.error("Error while processing exiting vehicle", e);
            System.out.println("An error occurred while processing the exiting vehicle: " + e.getMessage());
        }
    }

    /**
     * Prompts the user to select a parking type and returns the corresponding ParkingType.
     *
     * @return ParkingType corresponding to the user's selection
     * @throws IllegalArgumentException if the user provides an unsupported option
     */
    private ParkingType getParkingType() throws Exception {
        System.out.println("Please select vehicle type:");
        System.out.println("1. CAR");
        System.out.println("2. BIKE");

        int input = inputReaderUtil.readSelection();
        switch (input) {
            case 1:
                return ParkingType.CAR;
            case 2:
                return ParkingType.BIKE;
            default:
                throw new IllegalArgumentException("Unsupported vehicle type.");
        }
    }

    /**
     * Prompts the user to enter the vehicle registration number.
     *
     * @return The vehicle registration number entered by the user
     * @throws Exception if an error occurs while reading the input
     */
    private String getVehicleRegNumber() throws Exception {
        System.out.println("Please type the vehicle registration number and press enter key:");
        return inputReaderUtil.readVehicleRegistrationNumber();
    }

    /**
     * Main method for testing the InteractiveShell independently.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        DataBaseConfig dataBaseConfig = new DataBaseConfig();
        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(dataBaseConfig);
        TicketDAO ticketDAO = new TicketDAO(dataBaseConfig);
        FareCalculatorService fareCalculatorService = new FareCalculatorService();
        InputReaderUtil inputReaderUtil = new InputReaderUtil();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);

        InteractiveShell interactiveShell = new InteractiveShell(parkingService, inputReaderUtil);
        interactiveShell.loadInterface();
    }
}
