
package com.brainium.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Uploads scraped player data to the API endpoint.
 */
public class ApiUploader {
    
    private static final String API_BASE_URL = "https://webdev11.mydevfactory.com/nabaruna-sinha/awinwin/public/api/";
    private static final String ENDPOINT = "update-scrap-player-details";
    private static final String FULL_URL = API_BASE_URL + ENDPOINT;
    private static final String FAILED_URLS_FILE = "failed_player_urls.txt";
    private static final int NETWORK_RETRIES_PER_BATCH = 3;
    private static final long NETWORK_RETRY_BACKOFF_MS = 2000L;
    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    /**
     * Upload the recent_swedish_players_data.json file to the API in batches.
     * 
     * @return true if upload successful, false otherwise
     */
    public static boolean uploadPlayerData() {
        return uploadPlayerData(50); // Default: 50 players per batch
    }
    
    /**
     * Upload the recent_swedish_players_data.json file to the API in batches.
     * 
     * @param batchSize Number of players to send per request (recommended: 50-100)
     * @return true if all batches uploaded successfully, false otherwise
     */
    public static boolean uploadPlayerData(int batchSize) {
        Path dataFile = Path.of("recent_swedish_players_data.json");
        
        if (!Files.exists(dataFile)) {
            System.err.println("❌ Error: recent_swedish_players_data.json not found!");
            return false;
        }
        
        try {
            // Read the JSON file
            String jsonContent = Files.readString(dataFile, StandardCharsets.UTF_8);
            
            if (jsonContent.trim().isEmpty()) {
                System.err.println("⚠️  Warning: JSON file is empty, nothing to upload");
                return false;
            }
            
            System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║                  API UPLOAD DEBUG INFO                         ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            
            // Parse JSON - handle both array format [...] and object format {"recentlyUpdatedPlayers": [...]}
            JsonElement jsonElement = JsonParser.parseString(jsonContent);
            JsonArray playersArray;
            int totalPlayers = 0;
            
            System.out.println("\n📄 JSON FILE ANALYSIS:");
            System.out.println("   File size: " + jsonContent.length() + " bytes (" + (jsonContent.length() / 1024 / 1024) + " MB)");
            System.out.println("   Format: " + (jsonElement.isJsonArray() ? "Array" : "Object"));
            
            if (jsonElement.isJsonArray()) {
                // Old format: [...] 
                playersArray = jsonElement.getAsJsonArray();
                totalPlayers = playersArray.size();
                System.out.println("   ℹ️  Detected array format");
            } else if (jsonElement.isJsonObject()) {
                // New format: {"recentlyUpdatedPlayers": [...]}
                JsonObject jsonData = jsonElement.getAsJsonObject();
                if (jsonData.has("recentlyUpdatedPlayers")) {
                    playersArray = jsonData.getAsJsonArray("recentlyUpdatedPlayers");
                    totalPlayers = playersArray.size();
                    
                    // Clean up null values for specific required fields
                    cleanupNullFields(playersArray);
                    
                    // Show sample player structure
                    if (totalPlayers > 0) {
                        JsonElement firstPlayer = playersArray.get(0);
                        System.out.println("\n📊 SAMPLE PLAYER DATA (first entry):");
                        if (firstPlayer.isJsonObject()) {
                            JsonObject playerObj = firstPlayer.getAsJsonObject();
                            System.out.println("   Fields present: " + playerObj.keySet());
                            if (playerObj.has("user_id")) {
                                System.out.println("   Sample user_id: " + playerObj.get("user_id"));
                            }
                            if (playerObj.has("name")) {
                                System.out.println("   Sample name: " + playerObj.get("name"));
                            }
                            if (playerObj.has("nation")) {
                                System.out.println("   Sample nation: " + playerObj.get("nation"));
                            }
                        }
                    }
                } else {
                    System.err.println("   ⚠️  Warning: JSON object doesn't contain 'recentlyUpdatedPlayers' key");
                    System.err.println("   Available keys: " + jsonData.keySet());
                    return false;
                }
            } else {
                System.err.println("❌ Error: Invalid JSON format - not array or object");
                return false;
            }
            
            // Track failures from pre-validation and batch upload
            java.util.List<String> failedPlayerUrls = new java.util.ArrayList<>();

            // Always upload all valid players (no resume/skip)
            Set<String> uploadedIds = new HashSet<>();
            List<JsonElement> pendingPlayers = new ArrayList<>();
            for (int i = 0; i < totalPlayers; i++) {
                JsonElement p = playersArray.get(i);
                if (!sanitizePlayerForUpload(p, failedPlayerUrls)) {
                    continue;
                }
                pendingPlayers.add(p);
            }
            int originalTotal = totalPlayers;
            totalPlayers = pendingPlayers.size();
            int skippedPlayers = originalTotal - totalPlayers;

            // Calculate batches
            int totalBatches = (int) Math.ceil((double) totalPlayers / batchSize);
            
            System.out.println("\n📤 BATCH UPLOAD STRATEGY:");
            System.out.println("   Total players in file: " + originalTotal);
            System.out.println("   Players selected for upload: " + totalPlayers);
            System.out.println("   Skipped invalid players: " + skippedPlayers);
            System.out.println("   Batch size: " + batchSize + " players/batch");
            System.out.println("   Total batches: " + totalBatches);
            System.out.println("   Target URL: " + FULL_URL);
            if (jsonContent.length() > 10_000_000) {
                System.out.println("   ⚠️  WARNING: Original file is " + (jsonContent.length() / 1024 / 1024) + " MB");
                System.out.println("   ✅ Using batch upload to avoid server limits");
            }
            
            // Track results
            int successfulBatches = 0;
            int failedBatches = 0;
            java.util.List<String> failedBatchNumbers = new java.util.ArrayList<>();
            
            
            System.out.println("\n" + "═".repeat(64));
            System.out.println("🚀 STARTING BATCH UPLOAD");
            System.out.println("═".repeat(64));
            
            // Process each batch
            for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
                int startIdx = batchNum * batchSize;
                int endIdx = Math.min(startIdx + batchSize, totalPlayers);
                int currentBatchSize = endIdx - startIdx;
                
                System.out.println("\n📦 BATCH " + (batchNum + 1) + "/" + totalBatches);
                System.out.println("   Players: " + startIdx + " to " + (endIdx - 1) + " (" + currentBatchSize + " players)");
                
                // Extract batch
                JsonArray batchArray = new JsonArray();
                for (int i = startIdx; i < endIdx; i++) {
                    batchArray.add(pendingPlayers.get(i));
                }
                
                // Wrap in expected format
                JsonObject batchData = new JsonObject();
                batchData.add("recentlyUpdatedPlayers", batchArray);
                String batchJson = new Gson().toJson(batchData);
                
                System.out.println("   Batch size: " + batchJson.length() + " bytes (" + (batchJson.length() / 1024) + " KB)");
                
                // Debug: Show first player's data structure for batch 2
                if (batchNum == 1 && batchArray.size() > 0) {
                    System.out.println("\n   📋 BATCH 2 SAMPLE - First player data:");
                    System.out.println("   " + batchArray.get(0).toString().substring(0, Math.min(300, batchArray.get(0).toString().length())));
                    System.out.println();
                }
                
                // Create HTTP POST request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(FULL_URL))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .timeout(Duration.ofMinutes(2))
                        .POST(HttpRequest.BodyPublishers.ofString(batchJson, StandardCharsets.UTF_8))
                        .build();
                
                System.out.println("   🚀 Sending batch " + (batchNum + 1));
                List<String> batchPlayerIds = extractPlayerIds(batchArray);
                if (!batchPlayerIds.isEmpty()) {
                    System.out.println("   👤 Player IDs: " + String.join(", ", batchPlayerIds));
                }
                boolean batchHandled = false;
                for (int attempt = 1; attempt <= NETWORK_RETRIES_PER_BATCH && !batchHandled; attempt++) {
                    long startTime = System.currentTimeMillis();
                    try {
                        // Send request
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        long duration = System.currentTimeMillis() - startTime;
                        int statusCode = response.statusCode();
                        String responseBody = response.body();

                        System.out.println("   📥 Response: " + statusCode + " (" + duration + "ms)");

                        if (statusCode >= 200 && statusCode < 300) {
                            System.out.println("   ✅ Batch " + (batchNum + 1) + " SUCCESS");
                            successfulBatches++;
                            if (!batchPlayerIds.isEmpty()) {
                                uploadedIds.addAll(batchPlayerIds);
                            }

                            // Show response if available
                            if (responseBody != null && !responseBody.trim().isEmpty() && responseBody.length() < 500) {
                                System.out.println("   Response: " + responseBody);
                            }
                            batchHandled = true;
                        } else if (statusCode == 422) {
                            System.err.println("   ❌ Batch " + (batchNum + 1) + " VALIDATION ERROR (HTTP 422)");

                            // Parse response to identify which players failed
                            List<String> failedPlayerUrls_422 = parseValidationErrorResponse(responseBody, batchArray);
                            if (!failedPlayerUrls_422.isEmpty()) {
                                failedPlayerUrls.addAll(failedPlayerUrls_422);
                                System.err.println("   📝 Identified " + failedPlayerUrls_422.size() + " invalid players");
                                System.err.println("   ⚠️  Invalid players logged to Updated_failed_players.txt");
                            } else {
                                // If we can't parse specific errors, log all URLs from batch
                                failedPlayerUrls.addAll(extractPlayerUrls(batchArray));
                                System.err.println("   ⚠️  All players in batch logged to Updated_failed_players.txt");
                            }

                            failedBatches++;
                            failedBatchNumbers.add(String.valueOf(batchNum + 1));
                            System.err.println("   ⚠️  Continuing with next batch...\n");
                            batchHandled = true;
                        } else {
                            boolean retryableStatus = statusCode == 429 || statusCode >= 500;
                            if (retryableStatus && attempt < NETWORK_RETRIES_PER_BATCH) {
                                long delayMs = NETWORK_RETRY_BACKOFF_MS * attempt;
                                System.err.println("   ⚠️  Batch " + (batchNum + 1) + " got HTTP " + statusCode
                                        + ". Retrying in " + delayMs + "ms (" + attempt + "/"
                                        + NETWORK_RETRIES_PER_BATCH + ")");
                                sleepQuietly(delayMs);
                                continue;
                            }

                            System.err.println("   ❌ Batch " + (batchNum + 1) + " FAILED (HTTP " + statusCode + ")");
                            failedBatches++;
                            failedBatchNumbers.add(String.valueOf(batchNum + 1));
                            failedPlayerUrls.addAll(extractPlayerUrls(batchArray));

                            // Show error response
                            if (responseBody != null && !responseBody.trim().isEmpty()) {
                                System.err.println("   Error response: " + responseBody);
                            }

                            // Provide helpful error messages based on status code
                            switch (statusCode) {
                                case 400:
                                    System.err.println("   💡 Bad Request - Check JSON format");
                                    break;
                                case 401:
                                    System.err.println("   💡 Unauthorized - API authentication required");
                                    break;
                                case 403:
                                    System.err.println("   💡 Forbidden - Check API permissions");
                                    break;
                                case 404:
                                    System.err.println("   💡 Not Found - Check API endpoint URL");
                                    break;
                                case 500:
                                    System.err.println("   💡 Server Error - Server-side issue");
                                    break;
                            }

                            System.err.println("   ⚠️  Continuing with next batch...");
                            batchHandled = true;
                        }

                    } catch (Exception e) {
                        boolean retryable = isRetryableTransportException(e) && attempt < NETWORK_RETRIES_PER_BATCH;
                        if (retryable) {
                            long delayMs = NETWORK_RETRY_BACKOFF_MS * attempt;
                            System.err.println("   ⚠️  Batch " + (batchNum + 1) + " network error: " + e.getMessage()
                                    + ". Retrying in " + delayMs + "ms (" + attempt + "/"
                                    + NETWORK_RETRIES_PER_BATCH + ")");
                            sleepQuietly(delayMs);
                            continue;
                        }

                        System.err.println("   ❌ Batch " + (batchNum + 1) + " ERROR: " + e.getMessage());
                        failedBatches++;
                        failedBatchNumbers.add(String.valueOf(batchNum + 1));
                        // Ensure timeout/network exceptions also preserve failed player URLs
                        failedPlayerUrls.addAll(extractPlayerUrls(batchArray));
                        System.err.println("   📝 Logged batch player URLs for retry tracking");
                        batchHandled = true;
                    }
                }
                
                // Small delay between batches to avoid overwhelming server
                if (batchNum < totalBatches - 1) {
                    try {
                        Thread.sleep(500); // 500ms delay between batches
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            
            // Write failed player URLs to file
            if (!failedPlayerUrls.isEmpty()) {
                Set<String> uniqueFailedUrls = new HashSet<>(failedPlayerUrls);
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(FAILED_URLS_FILE, true))) { // append mode
                    for (String url : uniqueFailedUrls) {
                        writer.write(url);
                        writer.newLine();
                    }
                    System.out.println("\n📝 Failed player URLs appended to: " + FAILED_URLS_FILE);
                    System.out.println("   Total failed players (this run): " + uniqueFailedUrls.size());
                } catch (IOException e) {
                    System.err.println("⚠️  Warning: Could not write to " + FAILED_URLS_FILE + ": " + e.getMessage());
                }
            }
            
            // Final summary
            System.out.println("\n" + "═".repeat(64));
            System.out.println("📊 UPLOAD SUMMARY");
            System.out.println("═".repeat(64));
            System.out.println("   Total players: " + totalPlayers);
            System.out.println("   Total batches: " + totalBatches);
            System.out.println("   ✅ Successful: " + successfulBatches + " batches");
            System.out.println("   ❌ Failed: " + failedBatches + " batches");
            
            if (failedBatches > 0) {
                System.err.println("   Failed batch numbers: " + String.join(", ", failedBatchNumbers));
                System.err.println("\n⚠️  Some batches failed. Check " + FAILED_URLS_FILE + " for URLs.");
            }
            
            boolean allSuccess = (failedBatches == 0);
            
            if (allSuccess) {
                System.out.println("\n✅ ALL BATCHES UPLOADED SUCCESSFULLY!");
            } else {
                System.err.println("\n⚠️  Upload completed with " + failedBatches + " failed batch(es)");
            }
            
            System.out.println("═".repeat(64) + "\n");
            
            return allSuccess;
            
        } catch (IOException e) {
            System.err.println("\n❌ IO ERROR:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Type: " + e.getClass().getName());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("\n❌ UNEXPECTED ERROR:");
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Type: " + e.getClass().getName());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean isRetryableTransportException(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof java.net.SocketException
                || t instanceof java.net.SocketTimeoutException
                || t instanceof java.net.ConnectException
                || t instanceof java.net.UnknownHostException
                || t instanceof java.net.http.HttpTimeoutException
                || t instanceof IOException;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Normalize player fields to avoid DB type errors. Returns false if record should
     * be skipped.
     */
    private static boolean sanitizePlayerForUpload(JsonElement playerElement, List<String> failedUrls) {
        if (playerElement == null || !playerElement.isJsonObject()) {
            return false;
        }
        JsonObject player = playerElement.getAsJsonObject();

        if (player.has("age") && !player.get("age").isJsonNull()) {
            String ageRaw = "";
            try {
                ageRaw = player.get("age").getAsString().trim();
            } catch (Exception ignore) {
            }

            String digits = ageRaw.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                player.addProperty("age", digits);
            } else {
                Integer derivedAge = deriveAgeFromBirthdate(player);
                if (derivedAge != null) {
                    player.addProperty("age", derivedAge);
                } else {
                    String url = extractPlayerUrl(player);
                    if (url != null && !url.isBlank()) {
                        failedUrls.add(url);
                    }
                    String id = extractPlayerId(player);
                    System.err.println("   ⚠️  Skipping player due to invalid age value: user_id=" + id + ", age=" + ageRaw);
                    return false;
                }
            }
        }

        return true;
    }

    private static Integer deriveAgeFromBirthdate(JsonObject player) {
        if (player == null || !player.has("birthdate") || player.get("birthdate").isJsonNull()) {
            return null;
        }
        try {
            String birthdateRaw = player.get("birthdate").getAsString().trim();
            if (birthdateRaw.isEmpty() || "-".equals(birthdateRaw) || "?".equals(birthdateRaw)) {
                return null;
            }
            LocalDate dob = LocalDate.parse(birthdateRaw, DOB_FMT);
            int age = LocalDate.now().getYear() - dob.getYear();
            if (age < 0 || age > 90) {
                return null;
            }
            return age;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Clean up null values for specific fields that the API requires to be non-null.
     * Converts null values to sensible defaults.
     */
    private static void cleanupNullFields(JsonArray playersArray) {
        for (JsonElement playerElement : playersArray) {
            if (playerElement.isJsonObject()) {
                JsonObject player = playerElement.getAsJsonObject();
                
                // Replace null shoots with empty string
                if (player.has("shoots") && player.get("shoots").isJsonNull()) {
                    player.addProperty("shoots", "");
                }
                
                // Replace null nation with empty string
                if (player.has("nation") && player.get("nation").isJsonNull()) {
                    player.addProperty("nation", "");
                }
                
                // Replace null place_of_birth with empty string
                if (player.has("place_of_birth") && player.get("place_of_birth").isJsonNull()) {
                    player.addProperty("place_of_birth", "");
                }
                
                // Replace null position with empty string
                if (player.has("position") && player.get("position").isJsonNull()) {
                    player.addProperty("position", "");
                }
            }
        }
    }
    
    /**
     * Main method to run upload standalone.
     */
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║     API Uploader - Player Data        ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        // Print JVM memory information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
        long totalMemory = runtime.totalMemory() / (1024 * 1024); // MB
        System.out.println("JVM Memory Info:");
        System.out.println("  Max Memory: " + maxMemory + " MB");
        System.out.println("  Total Memory: " + totalMemory + " MB\n");
        
        // --- CHECK IF EXTRACTION COMPLETED SUCCESSFULLY ---
        Path extractionMarker = Path.of(".extraction_success");
        if (!Files.exists(extractionMarker)) {
            System.err.println("❌ ERROR: Extraction did not complete successfully!");
            System.err.println("   Marker file not found: " + extractionMarker.toAbsolutePath());
            System.err.println("   Please run SwedishPlayersExtractor first.");
            terminateProcess(1, "Extraction marker missing");
        }
        
        // --- CREATE LOCK FILE WITH PID ---
        Path lockFile = Path.of("api_uploader.lock");
        Path pidFile = Path.of("api_uploader.pid");
        
        try {
            // Check if lock file exists
            if (Files.exists(lockFile)) {
                if (Files.exists(pidFile)) {
                    String oldPid = Files.readString(pidFile).trim();
                    System.err.println("❌ ApiUploader is already running (PID: " + oldPid + ")");
                    System.err.println("   Lock file: " + lockFile.toAbsolutePath());
                    System.err.println("   If this is a stale lock, remove: rm " + lockFile.toAbsolutePath());
                    terminateProcess(1, "Uploader lock already exists");
                }
            }
            
            // Create lock file and PID file
            String pid = String.valueOf(ProcessHandle.current().pid());
            Files.writeString(lockFile, "LOCKED by PID " + pid + " at " + java.time.LocalDateTime.now());
            Files.writeString(pidFile, pid);
            System.out.println("🔒 Lock acquired (PID: " + pid + ") - Lock file: " + lockFile.toAbsolutePath());
            
            // Ensure lock file is deleted on JVM shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(lockFile);
                    Files.deleteIfExists(pidFile);
                    System.out.println("🔓 Lock released - deleted " + lockFile.toAbsolutePath());
                } catch (Exception e) {
                    System.err.println("Warning: Failed to delete lock file: " + e.getMessage());
                }
            }));
        } catch (Exception e) {
            System.err.println("❌ Failed to create lock file: " + e.getMessage());
            terminateProcess(1, "Failed to create uploader lock");
        }
        
        boolean success = false;
        try {
            success = uploadPlayerData();
            
            // Print final memory usage
            runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            System.out.println("\nMemory used: " + usedMemory + " MB / " + (runtime.maxMemory() / (1024 * 1024)) + " MB");
            
        } catch (OutOfMemoryError e) {
            System.err.println("\n❌ OUT OF MEMORY ERROR in ApiUploader!");
            System.err.println("   Heap memory exhausted. Consider:");
            System.err.println("   1. Increasing -Xmx value in MAVEN_OPTS");
            System.err.println("   2. Reducing batch size in uploadPlayerData()");
            System.err.println("   Current settings: -Xmx" + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + "m");
            e.printStackTrace();
            terminateProcess(137, "Out of memory");  // Signal OOM error (128 + 9)
        } catch (Exception e) {
            System.err.println("\n❌ UNEXPECTED ERROR in ApiUploader: " + e.getMessage());
            e.printStackTrace();
            terminateProcess(1, "Unexpected runtime error");
        }

        int exitCode = success ? 0 : 1;
        String reason = success ? "All batches completed" : "One or more batches failed";
        terminateProcess(exitCode, reason);
    }

    /**
     * Print explicit exit details and terminate the JVM with the provided code.
     */
    private static void terminateProcess(int exitCode, String reason) {
        System.out.println("\n🏁 ApiUploader terminating");
        System.out.println("   Reason: " + reason);
        System.out.println("   Exit code: " + exitCode);
        System.exit(exitCode);
    }

    private static String extractPlayerId(JsonElement player) {
        if (player == null || !player.isJsonObject()) {
            return null;
        }
        JsonObject obj = player.getAsJsonObject();
        if (!obj.has("user_id")) {
            return null;
        }
        try {
            return obj.get("user_id").getAsString().trim();
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<String> extractPlayerIds(JsonArray batchArray) {
        List<String> ids = new ArrayList<>();
        if (batchArray == null) {
            return ids;
        }
        for (JsonElement player : batchArray) {
            String id = extractPlayerId(player);
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<String> extractPlayerUrls(JsonArray batchArray) {
        List<String> urls = new ArrayList<>();
        if (batchArray == null) {
            return urls;
        }
        for (JsonElement player : batchArray) {
            if (player != null && player.isJsonObject()) {
                JsonObject playerObj = player.getAsJsonObject();
                if (playerObj.has("profile_link")) {
                    try {
                        String url = playerObj.get("profile_link").getAsString();
                        if (url != null && !url.trim().isEmpty()) {
                            urls.add(url.trim());
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        }
        return urls;
    }

    /**
     * Parse API validation error response and extract profile_link URLs of players that failed.
     * The API returns validation errors for specific players (e.g., user_id doesn't exist).
     */
    private static List<String> parseValidationErrorResponse(String responseBody, JsonArray batchArray) {
        List<String> failedUrls = new ArrayList<>();
        
        if (responseBody == null || responseBody.trim().isEmpty() || batchArray == null) {
            return failedUrls;
        }
        
        try {
            // Try to parse error response to identify which player records failed
            JsonElement responseElement = JsonParser.parseString(responseBody);
            if (responseElement.isJsonObject()) {
                JsonObject responseObj = responseElement.getAsJsonObject();
                
                // Check for errors field that might contain player-specific errors
                if (responseObj.has("errors")) {
                    JsonElement errorsElement = responseObj.get("errors");
                    if (errorsElement.isJsonObject()) {
                        JsonObject errors = errorsElement.getAsJsonObject();
                        
                        // Extract indices of failed players from error messages
                        // e.g., "recentlyUpdatedPlayers.0.user_id" indicates player at index 0 failed
                        for (String key : errors.keySet()) {
                            if (key.startsWith("recentlyUpdatedPlayers.")) {
                                try {
                                    String[] parts = key.split("\\.");
                                    if (parts.length > 1) {
                                        int playerIndex = Integer.parseInt(parts[1]);
                                        if (playerIndex >= 0 && playerIndex < batchArray.size()) {
                                            JsonElement player = batchArray.get(playerIndex);
                                            String url = extractPlayerUrl(player);
                                            if (url != null && !url.isEmpty()) {
                                                failedUrls.add(url);
                                            }
                                        }
                                    }
                                } catch (NumberFormatException e) {
                                    // Skip if index is not a number
                                }
                            }
                        }
                    }
                }
                
                // If we couldn't parse specific errors, check for message field
                if (failedUrls.isEmpty() && responseObj.has("message")) {
                    String message = responseObj.get("message").getAsString();
                    System.err.println("   API Error message: " + message);
                }
            }
        } catch (Exception e) {
            System.err.println("   ⚠️  Could not parse validation error response: " + e.getMessage());
        }
        
        return failedUrls;
    }

    /**
     * Extract a single player's profile_link URL from a JsonElement.
     */
    private static String extractPlayerUrl(JsonElement player) {
        if (player == null || !player.isJsonObject()) {
            return null;
        }
        JsonObject playerObj = player.getAsJsonObject();
        if (playerObj.has("profile_link")) {
            try {
                return playerObj.get("profile_link").getAsString();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
