package com.parkit.parkingsystem;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.service.FareCalculatorService;
import com.parkit.parkingsystem.util.InputReaderUtil;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.service.InteractiveShell;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main class to initialize and start the parking system application.
 */
public class App {
    private static final Logger logger = LogManager.getLogger(App.class);

    public static void main(String[] args) {
        // Create the necessary components for the parking system
        DataBaseConfig dataBaseConfig = new DataBaseConfig();
        ParkingSpotDAO parkingSpotDAO = new ParkingSpotDAO(dataBaseConfig);
        TicketDAO ticketDAO = new TicketDAO(dataBaseConfig);
        FareCalculatorService fareCalculatorService = new FareCalculatorService();
        InputReaderUtil inputReaderUtil = new InputReaderUtil();
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO, fareCalculatorService);

        // Initialize and start the interactive shell with the provided services
        try {
            logger.info("Initializing Parking System");

            InteractiveShell interactiveShell = new InteractiveShell(parkingService, inputReaderUtil);
            interactiveShell.loadInterface();
        } catch (Exception e) {
            logger.error("An error occurred while initializing the Parking System", e);
            System.out.println("An error occurred: " + e.getMessage());
        } finally {
            // Clean up resources, e.g., close Scanner if needed
            if (inputReaderUtil != null) {
                inputReaderUtil.close();
            }
        }
    }
}
