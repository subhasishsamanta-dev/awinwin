package com.brainium.core;

/**
 * Utility to reset/clear the SwedishPlayersExtractor status.
 * Run this if you want to start extraction from scratch.
 */
public class ResetExtractorStatus {
    public static void main(String[] args) {
        System.out.println("Resetting SwedishPlayersExtractor status...");
        SweExtractorStatus.reset();
        System.out.println("Status reset complete. Next run will start from scratch.");
    }
}
