package com.brainium.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks progress for SwedishPlayersExtractor to enable resume functionality.
 */
public class SweExtractorStatus {
    // Teams that have been fully processed
    public Set<String> processedTeams = new HashSet<>();
    
    // Players that have been fully scraped and saved
    public Set<String> scrapedPlayerIds = new HashSet<>();
    
    // Current page number in pagination
    public int currentPage = 1;
    
    // Last team URL being processed (for mid-team resume)
    public String currentTeam = null;
    
    // Timestamp of last update
    public long lastUpdate = System.currentTimeMillis();
    
    private static final String STATUS_FILE = "swedish_extractor_status.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Load status from file, or return new status if file doesn't exist.
     */
    public static SweExtractorStatus load() {
        Path statusPath = Path.of(STATUS_FILE);
        if (Files.exists(statusPath)) {
            try {
                String json = Files.readString(statusPath, StandardCharsets.UTF_8);
                SweExtractorStatus status = gson.fromJson(json, SweExtractorStatus.class);
                System.out.println("📂 Resuming from previous run:");
                System.out.println("   - Processed teams: " + status.processedTeams.size());
                System.out.println("   - Scraped players: " + status.scrapedPlayerIds.size());
                System.out.println("   - Current page: " + status.currentPage);
                if (status.currentTeam != null) {
                    System.out.println("   - Current team: " + status.currentTeam);
                }
                return status;
            } catch (Exception e) {
                System.err.println("⚠️  Failed to load status file, starting fresh: " + e.getMessage());
            }
        }
        System.out.println("🆕 Starting fresh extraction (no previous status found)");
        return new SweExtractorStatus();
    }
    
    /**
     * Save current status to file.
     */
    public void save() {
        try {
            this.lastUpdate = System.currentTimeMillis();
            String json = gson.toJson(this);
            Files.writeString(Path.of(STATUS_FILE), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("⚠️  Failed to save status: " + e.getMessage());
        }
    }
    
    /**
     * Mark a team as fully processed.
     */
    public void markTeamProcessed(String teamUrl) {
        processedTeams.add(teamUrl);
        currentTeam = null;
        save();
    }
    
    /**
     * Mark current team being processed.
     */
    public void setCurrentTeam(String teamUrl) {
        currentTeam = teamUrl;
        save();
    }
    
    /**
     * Mark a player as scraped.
     */
    public void markPlayerScraped(String playerId) {
        scrapedPlayerIds.add(playerId);
        // Save every 10 players to avoid too frequent I/O
        if (scrapedPlayerIds.size() % 10 == 0) {
            save();
        }
    }
    
    /**
     * Update current page number.
     */
    public void setCurrentPage(int page) {
        currentPage = page;
        save();
    }
    
    /**
     * Check if a team has been processed.
     */
    public boolean isTeamProcessed(String teamUrl) {
        return processedTeams.contains(teamUrl);
    }
    
    /**
     * Check if a player has been scraped.
     */
    public boolean isPlayerScraped(String playerId) {
        return scrapedPlayerIds.contains(playerId);
    }
    
    /**
     * Reset/clear status (useful for starting completely fresh).
     */
    public static void reset() {
        try {
            Files.deleteIfExists(Path.of(STATUS_FILE));
            System.out.println("[INFO] Status file deleted, will start fresh");
        } catch (IOException e) {
            System.err.println("Failed to delete status file: " + e.getMessage());
        }
    }
}
