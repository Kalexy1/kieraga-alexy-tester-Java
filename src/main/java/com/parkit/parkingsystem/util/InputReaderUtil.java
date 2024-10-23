package com.parkit.parkingsystem.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

/**
 * Utility class for reading user input from the console.
 */
public class InputReaderUtil {

    private static final Scanner scan = new Scanner(System.in);
    private static final Logger logger = LogManager.getLogger(InputReaderUtil.class);

    /**
     * Reads an integer selection from the user.
     * 
     * @return the integer input from the user, or -1 if an error occurs
     */
    public int readSelection() {
        try {
            return Integer.parseInt(scan.nextLine());
        } catch (NumberFormatException e) {
            logger.error("Error while reading integer input", e);
            System.out.println("Invalid input. Please enter a valid number.");
            return -1;
        }
    }

    /**
     * Reads a vehicle registration number from the user.
     * 
     * @return the vehicle registration number entered by the user
     * @throws IllegalArgumentException if the input is null or empty
     */
    public String readVehicleRegistrationNumber() throws IllegalArgumentException {
        try {
            String vehicleRegNumber = scan.nextLine().trim();
            if (vehicleRegNumber.isEmpty()) {
                throw new IllegalArgumentException("Vehicle registration number cannot be empty.");
            }
            return vehicleRegNumber;
        } catch (IllegalArgumentException e) {
            logger.error("Error while reading vehicle registration number", e);
            System.out.println(e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while reading vehicle registration number", e);
            System.out.println("An unexpected error occurred while reading the vehicle registration number.");
            throw e;
        }
    }

    /**
     * Closes the scanner resource.
     */
    public void close() {
        if (scan != null) {
            scan.close();
        }
    }
}
