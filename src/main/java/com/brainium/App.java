package com.brainium;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class App {
    public static void main(String[] args) {
        Dotenv.load();
        System.out.println("Starting APP!");
        
        // Add shutdown hook to ensure status is visible on interruption
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\n╔════════════════════════════════════════╗");
            System.out.println("║   SCRAPER STOPPED                  ║");
            System.out.println("║  Check status.json to resume          ║");
            System.out.println("╚════════════════════════════════════════╝");
        }));

        try {
            // Load already-scraped IDs (for visibility) to avoid reprocessing duplicates
            Set<String> scrapedIds = loadScrapedIds("output.csv");
            System.out.println("Loaded " + scrapedIds.size() + " already-scraped player IDs from output.csv");

            // Positions to scrape in order
            List<String> positions = List.of("f"); //"g", "f", "d","c", 
            
            System.out.println("Processing Sweden (SWE) - Positions: " + positions);
            System.out.println("Years:1992-2026");
            System.out.println("========================================");
            
            // Loop through each position first
            for (String position : positions) {
                System.out.println("\n╔════════════════════════════════════════╗");
                System.out.println("║  POSITION: " + position.toUpperCase() + " (" + getPositionName(position) + ")");
                System.out.println("╚════════════════════════════════════════╝");
                
                // Then loop through years for each position
                for (int year = 1992; year <= 2026; year++) {
                    System.out.println("\n  ┌─────────────────────────────────────┐");
                    System.out.println("  │  Year: " + year + " | Position: " + position.toUpperCase());
                    System.out.println("  └─────────────────────────────────────┘");
                    
                    try {
                        com.brainium.core.WebScrapper.startScraping(position, year);
                        System.out.println("\n  Scrap Completed: " + position.toUpperCase() + " - Year " + year);
                    } catch (Exception e) {
                        System.err.println("\n  Error: " + position.toUpperCase() + " - Year " + year + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                System.out.println("\n╔════════════════════════════════════════╗");
                System.out.println("║   POSITION " + position.toUpperCase() + " COMPLETED!");
                System.out.println("║  All years (1992-2026) processed");
                System.out.println("╚════════════════════════════════════════╝\n");
            }

            System.out.println("\n╔═══════════════════════════════╗");
            System.out.println("║  ALL POSITIONS & YEARS COMPLETED!     ║");
            System.out.println("╚════════════════════════════════════════╝");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads already scraped IDs from the CSV file (first column) into a Set.
     * This is primarily used for visibility and to avoid accidental re-processing.
     */
    private static Set<String> loadScrapedIds(String outputFile) {
        Set<String> ids = new HashSet<>();
        try {
            File f = new File(outputFile);
            if (!f.exists()) return ids;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] cols = line.split(",", -1);
                    if (cols.length > 0) {
                        String id = cols[0].replaceAll("^\"|\"$", "").trim();
                        if (!id.isEmpty()) ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: could not load scraped IDs: " + e.getMessage());
        }
        return ids;
    }
    
    /**
     * Gets the full name of a position
     */
    private static String getPositionName(String position) {
        switch (position.toLowerCase()) {
            
            case "f":
                return "Forwards";
            case "d":
                return "Defensemen";
            case "g":
                return "Goaltenders";
            case "c":
                return "Centers";
            case "lw":
                return "Left Wings";
            case "rw":
                return "Right Wings";
            default:
                return position.toUpperCase();
        }
    }
}