package com.brainium.core;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.brainium.schema.PlayerProfile;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Utility class for scraping player tables from EliteProspects and saving data
 * to CSV.
 * Supports resuming from last processed page using status tracking.
 */
public class TableScapper {

    // Configuration via environment variables with defaults
    private static String OUTPUT_DIR = "output.csv"; // Will be set dynamically based on position
    private static final String STATUS_DIR = System.getenv("STATUS_FILE") != null ? System.getenv("STATUS_FILE")
            : "status.json";
    private static final String FAILED_PLAYERS_FILE = "failed_players.txt";

    private static final Gson gson = new Gson();
    
    // Track scraped player IDs to avoid duplicates
    private static Set<String> scrapedPlayerIds = new HashSet<>();

    // Date cutoff for filtering players
    private static java.time.LocalDate dateCutoff;

    // Always use a single output file for all positions

    /**
     * Fetches player tables for a given position and year, starting from the specified page
     * or resuming from status.
     * Scrapes player profiles and writes them to CSV incrementally, updating status
     * after each page.
     *
     * @param position    The position to scrape (e.g., "g", "f", "d", "c", "lw", "rw")
     * @param year        The birth year to filter for (e.g., 2011)
     * @param defaultPage The default starting page if no status exists
     * @throws Exception if scraping or file operations fail
     */
    public static void fetchTables(String position, int year, int defaultPage, String email, String password) throws Exception {
        // Always use a single output file for all positions
        OUTPUT_DIR = "output.csv";

        // Set date cutoff for the current year (from Jan 1 to Dec 31 of the year)
        dateCutoff = java.time.LocalDate.of(year, 1, 1);

        // Load existing player IDs from CSV to avoid duplicates across runs
        loadScrapedPlayerIds();

        // Load status to resume from last page
        String searchKey = position + "_" + year;
        int startPage = readStatus(searchKey, defaultPage);

        int page = startPage;

        // Create/update status file immediately so it's visible
        updateStatus(searchKey, page);
        System.out.println("  [INFO] Status saved to status.json (Position: " + position.toUpperCase() + ", Year: " + year + ", Page: " + page + ")");

        // Write CSV header if file doesn't exist
        writeCSVHeaderIfNeeded();

        // --- LOGIN AND GET TOKENS ---
        JsonObject loginResp = EliteProspectsAPI.loginAndGetTokens(email, password);
        // Extract tokens and build cookie string
        String token = loginResp.has("token") ? loginResp.get("token").getAsString() : null;
        String streamToken = loginResp.has("streamToken") ? loginResp.get("streamToken").getAsString() : null;
        // You may need to add more cookies/headers as required by the site
        StringBuilder cookieBuilder = new StringBuilder();
        if (token != null) cookieBuilder.append("ep_next_token=").append(token).append(";");
        if (streamToken != null) cookieBuilder.append("streamToken=").append(streamToken).append(";");
        // Add any other static or required cookies here if needed
        String dynamicCookies = cookieBuilder.toString();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(8); // 8 threads
        Object csvLock = new Object();
        Object scrapedSetLock = new Object();
        while (true) {
            String url = String.format("https://www.eliteprospects.com/search/player?position=%s&dob=%d&nation=swe&page=%d",
                    escapeForFormat(position), year, page);

            System.out.println("  📄 Page " + page + " | Position: " + position.toUpperCase() + " | Year: " + year);

            // Connect to the page with dynamic cookies and increased timeout
            Document doc = null;
            try {
                doc = Jsoup.connect(url)
                        .header("cookie", dynamicCookies)
                        .timeout(60000) // Increased to 60 seconds
                        .maxBodySize(0) // No limit on body size
                        .get();
            } catch (java.net.SocketTimeoutException e) {
                System.err.println("Timeout fetching page " + page + " for position " + position.toUpperCase() + " (year: " + year + "). Skipping to next page.");
                page++;
                continue;
            } catch (Exception e) {
                System.err.println("Error fetching page " + page + " for position " + position.toUpperCase() + " (year: " + year + "): " + e.getMessage());
                page++;
                continue;
            }

            // Select player links from the table
            Elements links = doc.select("td.name a");

            if (links.isEmpty()) {
                System.out.println("  [OK] No more players on page " + page + ". Year " + year + " completed!");
                System.out.println();
                break; // No more players, exit loop
            }

            System.out.println("  👥 Found " + links.size() + " players on page " + page);

            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            for (Element link : links) {
                String profileLink = link.absUrl("href");
                String[] parts = profileLink.split("/");
                String playerId = parts[parts.length - 2];
                String playerUserName = parts[parts.length - 1];

                // Skip if already scraped
                synchronized (scrapedSetLock) {
                    if (scrapedPlayerIds.contains(playerId)) {
                        System.out.println("    [SKIP] Skipping already scraped player: " + playerId);
                        continue;
                    }
                }

                String playerUrl = String.format("https://www.eliteprospects.com/player/%s/%s", escapeForFormat(playerId),
                        escapeForFormat(playerUserName));

                futures.add(executor.submit(() -> {
                    try {
                        PlayerProfile profile = ProfileScapper.getProfile(playerUrl, playerId);
                        profile.userId = playerId;
                        profile.userName = playerUserName;

                        // Always include player in output.csv (do not exclude based on DOB)
                        synchronized (csvLock) {
                            writeProfileToCSV(profile);
                        }
                        System.out.println("    [OK] Included: " + playerId + " | " + profile.name + " | DOB: " + profile.dateOfBirth);

                        // Add to scraped set
                        synchronized (scrapedSetLock) {
                            scrapedPlayerIds.add(playerId);
                        }

                        // Add a small delay between requests to avoid rate limiting (500ms)
                        Thread.sleep(500);
                    } catch (Exception e) {
                        System.err.println("Failed to scrape player " + playerId + " after retries: " + e.getMessage());
                        // Log failed player to file for later retry
                        logFailedPlayer(playerId, playerUserName, position, e.getMessage());
                        // Continue with next player instead of stopping
                    }
                }));
            }
            // Wait for all player tasks to finish before moving to next page
            for (java.util.concurrent.Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }

            page++; // Move to next page

            // Update status after processing the page with the NEXT page number
            updateStatus(position + "_" + year, page);
            System.out.println("  💾 Progress saved: Page " + page + " (Position: " + position.toUpperCase() + ", Year: " + year + ")");
        }
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.MINUTES);
    }

    /**
     * Loads already scraped player IDs from the CSV file to avoid duplicates.
     */
    private static void loadScrapedPlayerIds() {
        try {
            if (Files.exists(Paths.get(OUTPUT_DIR))) {
                List<String> lines = Files.readAllLines(Paths.get(OUTPUT_DIR));
                // Skip header
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (!line.trim().isEmpty()) {
                        // Extract player ID (first column)
                        int firstComma = line.indexOf(',');
                        if (firstComma > 0) {
                            String playerId = line.substring(0, firstComma).trim();
                            scrapedPlayerIds.add(playerId);
                        }
                    }
                }
                System.out.println("  [INFO] Loaded " + scrapedPlayerIds.size() + " already scraped player IDs from output.csv");
            } else {
                System.out.println("  📝 Starting fresh - no existing output.csv found");
            }
        } catch (IOException e) {
            System.out.println("Warning: Could not load scraped player IDs: " + e.getMessage());
        }
    }

    /**
     * Reads the current status from status.json to determine the starting page for
     * a position+year combination.
     *
     * @param searchKey   The search key (position_year, e.g., "g_2011")
     * @param defaultPage The default page if no status exists
     * @return The page to start from
     */
    private static int readStatus(String searchKey, int defaultPage) {
        try {
            java.nio.file.Path statusPath = java.nio.file.Paths.get(STATUS_DIR);
            if (java.nio.file.Files.exists(statusPath)) {
                String json = java.nio.file.Files.readString(statusPath);
                JsonObject status = gson.fromJson(json, JsonObject.class);
                if (status.has("currentSearch") && status.get("currentSearch").getAsString().equals(searchKey)) {
                    return status.get("currentPage").getAsInt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultPage;
    }

    /**
     * Updates the status.json with the current progress for a position+year combination.
     *
     * @param searchKey The search key (position_year, e.g., "g_2011")
     * @param page      The last processed page
     */
    private static void updateStatus(String searchKey, int page) {
        try (FileWriter writer = new FileWriter(STATUS_DIR)) {
            JsonObject status = new JsonObject();
            status.addProperty("currentSearch", searchKey);
            status.addProperty("currentPage", page);
            gson.toJson(status, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes the CSV header if the file doesn't exist.
     */
    private static void writeCSVHeaderIfNeeded() {
        try {
            if (!Files.exists(Paths.get(OUTPUT_DIR))) {
                try (FileWriter writer = new FileWriter(OUTPUT_DIR)) {
                    writer.write(
                            "User ID,Username,Name,Date of Birth,Age,Place of Birth,Nation,Youth Team,latest_team_position,latest_team,seasone,Position,Height,Weight,Shoots,Contract,Player Type,Cap Hit,Cap Hit Image,NHL Rights,Drafted,Highlights,Agency,Relation,Image URL,Skills,Status\n");
                }
            }
            // Note: excluded_players.csv is no longer created - all players are written to output.csv
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Appends a single player profile to the CSV file.
     *
     * @param profile The PlayerProfile to write
     */
    private static void writeProfileToCSV(PlayerProfile profile) {
        try (FileWriter writer = new FileWriter(OUTPUT_DIR, true)) { // Append mode
            String playerTypeStr = String.join("; ", profile.playerType != null ? profile.playerType : new String[0]);
            String highlightsStr = String.join("; ", profile.highlights != null ? profile.highlights : new String[0]);
            String skillsStr = profile.getSkillsFormatted();

            // Format latest_team_position as #<number> and remove any trailing / or whitespace
            String formattedPosition = "";
            if (profile.latest_team_position != null) {
                String temp = profile.latest_team_position.trim();
                // Remove any trailing / and whitespace
                temp = temp.replaceAll("/.*$", "").trim();
                if (!temp.isEmpty()) {
                    // Add # if not present
                    if (!temp.startsWith("#")) {
                        formattedPosition = "#" + temp;
                    } else {
                        formattedPosition = temp;
                    }
                }
            }

            // Fetch stats from EliteProspectsAPI and use JSON in the Position cell
            String positionJson = "";
            try {
                String statsResponse = EliteProspectsAPI.fetchStatsFromAPI(profile.userId);
                positionJson = EliteProspectsAPI.parsePlayerStats(profile.position, statsResponse);
            } catch (Exception e) {
                positionJson = profile.position != null ? profile.position : "";
            }

                    // Build CSV row safely without String.format to avoid format-specifier issues
                    String[] fields = new String[] {
                        escapeForFormat(escapeCSV(profile.userId)),
                        escapeForFormat(escapeCSV(profile.userName)),
                        escapeForFormat(escapeCSV(profile.name)),
                        escapeForFormat(escapeCSV(profile.dateOfBirth)),
                        escapeForFormat(escapeCSV(profile.age)),
                        escapeForFormat(escapeCSV(profile.placeOfBirth)),
                        escapeForFormat(escapeCSV(profile.nation)),
                        escapeForFormat(escapeCSV(profile.youthTeam)),
                        escapeForFormat(escapeCSV(formattedPosition)),
                        escapeForFormat(escapeCSV(profile.latest_team)),
                        escapeForFormat(escapeCSV(profile.season)),
                        escapeForFormat(escapeCSV(positionJson)), // JSON in Position cell
                        escapeForFormat(escapeCSV(profile.height)),
                        escapeForFormat(escapeCSV(profile.weight)),
                        escapeForFormat(escapeCSV(profile.shoots)),
                        escapeForFormat(escapeCSV(profile.contract)),
                        escapeForFormat(playerTypeStr),
                        escapeForFormat(escapeCSV(profile.capHit)),
                        escapeForFormat(escapeCSV(profile.capHitImage)),
                        escapeForFormat(escapeCSV(profile.nhlRights)),
                        escapeForFormat(escapeCSV(profile.drafted)),
                        escapeForFormat(highlightsStr),
                        escapeForFormat(escapeCSV(profile.agency)),
                        escapeForFormat(escapeCSV(profile.relation)),
                        escapeForFormat(escapeCSV(profile.imageUrl)),
                        escapeForFormat(skillsStr),
                        escapeForFormat(escapeCSV(profile.status))
                    };
                    StringBuilder row = new StringBuilder();
                    for (int i = 0; i < fields.length; i++) {
                        if (i > 0) row.append(',');
                        row.append(fields[i]);
                    }
                    row.append('\n');
                    writer.write(row.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Escapes a string for CSV format by wrapping in quotes if it contains commas,
     * quotes, or newlines.
     *
     * @param value The string value to escape
     * @return The escaped CSV field
     */
    private static String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            // Escape quotes by doubling them
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return value;
    }

    /**
     * Escapes '%' characters for safe use in String.format.
     * @param value The string to escape
     * @return The escaped string
     */
    private static String escapeForFormat(String value) {
        if (value == null) return "";
        return value.replace("%", "%%");
    }

    /**
     * Logs failed player information to a file for later retry.
     * Format: playerId,playerUserName,position,timestamp,errorMessage
     *
     * @param playerId The player's ID
     * @param playerUserName The player's username
     * @param position The position
     * @param errorMessage The error message
     */
    private static void logFailedPlayer(String playerId, String playerUserName, String position, String errorMessage) {
        try (FileWriter writer = new FileWriter(FAILED_PLAYERS_FILE, true)) {
            String timestamp = java.time.LocalDateTime.now().toString();
            String[] parts = new String[] {
                escapeForFormat(playerId),
                escapeForFormat(playerUserName),
                escapeForFormat(position),
                escapeForFormat(timestamp),
                escapeForFormat(errorMessage.replace(",", ";").replace("\n", " "))
            };
            StringBuilder logEntry = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) logEntry.append(',');
                logEntry.append(parts[i]);
            }
            logEntry.append(System.lineSeparator());
            writer.write(logEntry.toString());
            writer.flush();
        } catch (IOException e) {
            System.err.println("Failed to log failed player " + playerId + " to file: " + e.getMessage());
        }
    }

    // Inclusion/exclusion based on DOB removed: all players are written to output.csv

    // Excluded player writing removed; all players are written to output.csv
}
