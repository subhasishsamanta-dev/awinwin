package com.brainium.core;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Main web scraper class that orchestrates scraping across multiple countries.
 * Supports resuming from last processed country and page using status tracking.
 */
public class WebScrapper {

    private static final String STATUS_DIR = System.getenv("STATUS_FILE") != null ? System.getenv("STATUS_FILE")
            : "status.json";
    private static final Gson gson = new Gson();

    /**
     * Starts the scraping process for a specific position and year, resuming from status if
     * available.
     *
     * @param position The position to scrape (e.g., "g", "f", "d", "c", "lw", "rw")
     * @param year The birth year to filter for
     * @throws Exception if scraping fails
     */
    public static void startScraping(String position, int year) throws Exception {
        // Read status to determine starting point
        StatusInfo status = readStatus();
        int startPage = 1;

        String searchKey = position + "_" + year;

        if (status != null && status.currentSearch.equals(searchKey)) {
            startPage = status.currentPage;
            System.out.println("  🔄 Resuming from page " + startPage + " for " + position.toUpperCase() + " - " + year);
        } else {
            System.out.println("  🆕 Starting scraping for position: " + position.toUpperCase() + " (year: " + year + ") from page: 1");
        }

        // TODO: Replace with secure retrieval of credentials (env vars, config, etc.)
        String email = System.getenv("EP_EMAIL");
        String password = System.getenv("EP_PASSWORD");
        if (email == null || password == null) {
            throw new IllegalArgumentException("EliteProspects credentials not set in environment variables EP_EMAIL and EP_PASSWORD");
        }
        // Delegate to TableScapper for this position and year
        TableScapper.fetchTables(position, year, startPage, email, password);
    }

    /**
     * Reads the current status from status.json.
     *
     * @return StatusInfo object or null if no status exists
     */
    private static StatusInfo readStatus() {
        try {
            if (Files.exists(Paths.get(STATUS_DIR))) {
                String json = Files.readString(Paths.get(STATUS_DIR));
                JsonObject statusObj = gson.fromJson(json, JsonObject.class);
                String search = statusObj.get("currentSearch").getAsString();
                int page = statusObj.get("currentPage").getAsInt();
                return new StatusInfo(search, page);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * Helper class to hold status information.
     */
    private static class StatusInfo {
        String currentSearch;
        int currentPage;

        StatusInfo(String currentSearch, int currentPage) {
            this.currentSearch = currentSearch;
            this.currentPage = currentPage;
        }
    }
}
