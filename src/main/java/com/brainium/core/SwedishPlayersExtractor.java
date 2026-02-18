package com.brainium.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.brainium.schema.PlayerProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;

/**
 * Extracts recently-played players (yesterday) from EliteProspects games
 * listing,
 * visits each team page, finds players with the Sweden flag, saves their URLs
 * to a file and scrapes full player profiles using `ProfileScapper`.
 */
public class SwedishPlayersExtractor {

    private static final String DEFAULT_GAMES_URL = System.getenv("GAMES_URL") != null
            ? System.getenv("GAMES_URL")
            : "https://www.eliteprospects.com/games/2025-2026/all-leagues/all-teams";

    // Cookie header reused from ProfileScapper for site access
    // Prefer reading cookie header from environment to avoid escaping issues in
    // source
    private static final String COOKIE_HEADER = System.getenv("EP_COOKIE_HEADER") != null
            ? System.getenv("EP_COOKIE_HEADER")
            : "d_s_i=1; _ga=GA1.1;";

    private static final String BASE = "https://www.eliteprospects.com";

    private static final Pattern PLAYER_ID_PATTERN = Pattern.compile("/player(?:\\.php\\?player=|/)(\\d+)");

    // Store cookies obtained from initial fetch or login so subsequent requests
    // reuse them
    private static final Map<String, String> cookiesStore = new HashMap<>();

    public static void main(String[] args) {
        String gamesUrl = args != null && args.length > 0 ? args[0] : DEFAULT_GAMES_URL;
        System.out.println("Starting SwedishPlayersExtractor for: " + gamesUrl);

        // Load status for resume functionality
        SweExtractorStatus status = SweExtractorStatus.load();

        // Map of playerId -> slug (username)
        Map<String, String> idToSlug = new HashMap<>();
        // Map of playerId -> fullUrl
        Map<String, String> idToUrl = new HashMap<>();
        Gson gson = new GsonBuilder().serializeNulls().create();

        try {
            // --- LOGIN AND GET FRESH TOKENS ---
            String email = System.getenv("EP_EMAIL");
            String password = System.getenv("EP_PASSWORD");
            boolean loginSuccess = false;
            
            if (email != null && password != null) {
                try {
                    System.out.println("Logging in to get fresh authentication tokens...");
                    JsonObject loginResp = EliteProspectsAPI.loginAndGetTokens(email, password);
                    String token = loginResp.has("token") ? loginResp.get("token").getAsString() : null;
                    String streamToken = loginResp.has("streamToken") ? loginResp.get("streamToken").getAsString() : null;
                    
                    // Store fresh cookies in cookiesStore
                    if (token != null) {
                        cookiesStore.put("ep_next_token", token);
                    }
                    if (streamToken != null) {
                        cookiesStore.put("streamToken", streamToken);
                    }
                    System.out.println("✓ Fresh authentication tokens obtained successfully!");
                    loginSuccess = true;
                } catch (Exception e) {
                    System.err.println("Warning: Failed to get fresh tokens via API login: " + e.getMessage());
                    System.err.println("Falling back to EP_COOKIE_HEADER environment variable...");
                }
            }
            
            // If API login failed or credentials not set, try to use EP_COOKIE_HEADER
            if (!loginSuccess && COOKIE_HEADER != null && !COOKIE_HEADER.isEmpty()) {
                System.out.println("Using fresh cookies from EP_COOKIE_HEADER environment variable...");
                // Parse cookies from COOKIE_HEADER format (e.g., "cookie1=value1; cookie2=value2")
                String[] cookiePairs = COOKIE_HEADER.split(";");
                for (String pair : cookiePairs) {
                    pair = pair.trim();
                    if (!pair.isEmpty() && pair.contains("=")) {
                        String[] parts = pair.split("=", 2);
                        if (parts.length == 2) {
                            cookiesStore.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                }
                System.out.println("✓ Fresh cookies loaded from environment variable!");
            } else if (!loginSuccess) {
                System.out.println("Note: No fresh credentials available. Will attempt anonymous fetch for basic cookies.");
            }

            System.out.println("\n🚀 Starting extraction from games page...\n");
            // compute yesterday in UTC (site uses UTC timestamps like
            // 2026-02-02T12:00:00+00:00)
            LocalDate targetDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
            // also compute a display text like "February 2" to match the visible td text
            DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
            String displayTarget = targetDate.format(displayFmt);

            // Pagination loop: start from the provided gamesUrl and follow "Next" links
            String pageUrl = gamesUrl;
            Set<String> teamUrls = new LinkedHashSet<>();
            boolean sawHeaderOnce = false;
            int pageCount = 0;
            final int MAX_PAGES = 20; // safety cap to avoid infinite loops

            while (pageUrl != null && pageCount < MAX_PAGES) {
                pageCount++;
                status.setCurrentPage(pageCount);
                Document pageDoc = fetchDocument(pageUrl);

                // Find date header row that corresponds to "yesterday" by parsing the
                // `data-date` attr.
                Elements dateHeaders = pageDoc.select("tr.title:has(td[data-action=transform-to-local-date])");
                if (dateHeaders.isEmpty()) {
                    System.out.println("No date header rows found on games page: " + pageUrl);
                    break;
                }

                Element yesterdayHeader = null;
                for (Element header : dateHeaders) {
                    Element td = header.selectFirst("td[data-date]");
                    boolean matched = false;
                    if (td != null) {
                        String dataDate = td.attr("data-date");
                        try {
                            OffsetDateTime odt = OffsetDateTime.parse(dataDate);
                            if (odt.toLocalDate().equals(targetDate)) {
                                yesterdayHeader = header;
                                matched = true;
                                break;
                            }
                        } catch (Exception ex) {
                            // ignore parse errors and continue
                        }
                        // try matching by displayed text inside the td as a fallback
                        String tdText = td.text() != null ? td.text().trim() : "";
                        if (!matched && tdText.contains(displayTarget)) {
                            yesterdayHeader = header;
                            break;
                        }
                    } else {
                        // header without data-date: try matching header text
                        String headerText = header.text() != null ? header.text().trim() : "";
                        if (headerText.contains(displayTarget)) {
                            yesterdayHeader = header;
                            break;
                        }
                    }
                }

                if (yesterdayHeader == null) {
                    if (!sawHeaderOnce) {
                        // fallback on first page only
                        yesterdayHeader = dateHeaders.last();
                        System.out.println("No exact match for yesterday (" + displayTarget
                                + "); falling back to last date header on page: " + yesterdayHeader.text());
                    } else {
                        // we've seen the header on earlier page(s) and this page doesn't include it;
                        // stop
                        break;
                    }
                }

                List<Element> gameRows = new ArrayList<>();

                if (yesterdayHeader != null) {
                    sawHeaderOnce = true;
                    gameRows = getGameRowsForDate(yesterdayHeader);
                    System.out.println("Page " + pageCount + " (" + pageUrl + ") found " + gameRows.size()
                            + " game rows for yesterday.");
                } else if (sawHeaderOnce) {
                    // We found the header on a previous page, but not this one.
                    // This implies the date block continues from the top of this page.
                    // We must collect rows from the top until we hit a NEW date header.
                    gameRows = collectGamesFromTop(pageDoc);
                    if (gameRows.isEmpty()) {
                        System.out.println("No continuation game rows found on page " + pageCount + ". Ending search.");
                        break;
                    }
                    System.out.println("Page " + pageCount + " (" + pageUrl + ") found " + gameRows.size()
                            + " continuation game rows for yesterday.");
                } else {
                    // Header not found yet, and not seen before.
                    System.out.println("Detailed debug: No yesterday header found on page " + pageCount
                            + " and sawHeaderOnce=false.");
                    break;
                }

                if (!gameRows.isEmpty()) {

                    for (Element gameRow : gameRows) {
                        try {
                            Elements teamTds = gameRow.select("td.team");
                            if (teamTds.size() < 2)
                                continue;

                            Element homeTeamLink = teamTds.get(0).selectFirst("a:nth-of-type(2)");
                            Element awayTeamLink = teamTds.get(1).selectFirst("a:nth-of-type(2)");

                            if (homeTeamLink != null) {
                                String href = homeTeamLink.attr("href");
                                String full = href.startsWith("http") ? href : BASE + href;
                                teamUrls.add(full);
                                
                                if (!status.isTeamProcessed(full)) {
                                    status.setCurrentTeam(full);
                                    collectFromTeam(href, idToSlug, idToUrl);
                                    status.markTeamProcessed(full);
                                } else {
                                    System.out.println("  ⏭️  Skipping already processed team: " + full);
                                }
                            }
                            if (awayTeamLink != null) {
                                String href = awayTeamLink.attr("href");
                                String full = href.startsWith("http") ? href : BASE + href;
                                teamUrls.add(full);
                                
                                if (!status.isTeamProcessed(full)) {
                                    status.setCurrentTeam(full);
                                    collectFromTeam(href, idToSlug, idToUrl);
                                    status.markTeamProcessed(full);
                                } else {
                                    System.out.println("  ⏭️  Skipping already processed team: " + full);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing game row: " + e.getMessage());
                        }
                    }

                    // Check if we should continue to next page:
                    // 1. If the last game row has no next sibling (hit end of page)
                    // 2. AND there's a "Next" pagination link available
                    
                    String nextHref = null;
                    boolean shouldContinue = false;
                    
                    try {
                        // Check if the last collected game row reached end of page
                        if (!gameRows.isEmpty()) {
                            Element lastRow = gameRows.get(gameRows.size() - 1);
                            Element nextSibling = lastRow.nextElementSibling();
                            
                            // Check if next sibling is a different date header (means yesterday's games ended)
                            boolean hitDifferentDate = (nextSibling != null) && 
                                                      (nextSibling.hasClass("title") ||
                                                       nextSibling.selectFirst("td[data-date-type=text]") != null);
                            
                            // Only continue to next page if we hit actual page end (null), NOT a different date
                            if (nextSibling == null) {
                                // True page end - yesterday's games may continue on next page
                                Elements pagLinks = pageDoc.select(".table-pagination a");
                                for (Element a : pagLinks) {
                                    String t = a.text();
                                    if (t != null && t.toLowerCase().contains("next")) {
                                        nextHref = a.absUrl("href");
                                        if (nextHref == null || nextHref.isEmpty())
                                            nextHref = a.attr("href");
                                        if (nextHref != null && !nextHref.isEmpty()) {
                                            shouldContinue = true;
                                        }
                                        break;
                                    }
                                }
                            } else if (hitDifferentDate) {
                                // Hit a different date - stop immediately
                                System.out.println("Detected next date header after " + displayTarget + " games. Stopping pagination.");
                            }
                        }
                    } catch (Exception ex) {
                        System.err.println("Error checking pagination: " + ex.getMessage());
                    }

                    if (shouldContinue && nextHref != null) {
                        // Continue to next page to collect more games from yesterday
                        if (!nextHref.startsWith("http"))
                            nextHref = BASE + (nextHref.startsWith("/") ? nextHref : "/" + nextHref);
                        System.out.println("Continuing to next page (" + displayTarget + " games may continue): " + nextHref);
                        pageUrl = nextHref;
                        continue;
                    } else {
                        // Stop: Either we hit next date header or no more pages
                        System.out.println("Completed collecting all " + displayTarget + " games.");
                        break;
                    }
                } else {
                    // No game rows found, stop
                    break;
                }
            }

            // write team URLs to team.txt
            Path teamsOut = Path.of("team.txt");
            try (BufferedWriter tw = Files.newBufferedWriter(teamsOut, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String t : teamUrls) {
                    tw.write(t);
                    tw.newLine();
                }
            }

            System.out.println("Total unique Swedish players found: " + idToUrl.size());

            // Persist URLs
            Path urlsOut = Path.of("recent_swedish_players_urls.txt");
            try (BufferedWriter w = Files.newBufferedWriter(urlsOut, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String u : idToUrl.values()) {
                    w.write(u);
                    w.newLine();
                }
            }

            // Persist IDs and usernames (slug) in a separate file: id,username,fullUrl
            Path idsOut = Path.of("recent_swedish_players_ids.txt");
            try (BufferedWriter w2 = Files.newBufferedWriter(idsOut, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Map.Entry<String, String> e : idToUrl.entrySet()) {
                    String id = e.getKey();
                    String url = e.getValue();
                    String slug = idToSlug.getOrDefault(id, "");
                    w2.write(id + "," + slug + "," + url);
                    w2.newLine();
                }
            }

            // Write team URLs to `team.txt` (ensure it's always created)
            Path teamsOut2 = Path.of("team.txt");
            try (BufferedWriter tw2 = Files.newBufferedWriter(teamsOut2, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String t : teamUrls) {
                    tw2.write(t);
                    tw2.newLine();
                }
            } catch (Exception ex) {
                System.err.println("Failed to write team.txt: " + ex.getMessage());
            }

            // Scrape full profiles one-by-one and immediately persist them in two forms:
            // 1) JSON-lines file `recent_swedish_players_profiles.jsonl` (append)
            // 2) JSON array file `recent_swedish_players_data.json` (append-safe helper)
            Path profilesOut = Path.of("recent_swedish_players_profiles.jsonl");
            Path exportOut = Path.of("recent_swedish_players_data.json");

            for (Map.Entry<String, String> e : idToUrl.entrySet()) {
                String playerId = e.getKey();
                String fullUrl = e.getValue();
                
                // Skip if player already scraped
                if (status.isPlayerScraped(playerId)) {
                    System.out.println("  ⏭️  Skipping already scraped player: " + playerId);
                    continue;
                }
                
                try {
                    PlayerProfile profile = ProfileScapper.getProfile(fullUrl, playerId);
                    if (profile == null)
                        continue;

                    // Build export object using the shared method
                    LinkedHashMap<String, Object> obj = buildPlayerObject(profile, playerId, fullUrl, idToSlug);
                    String objJson = gson.toJson(obj);

                    // Append to JSON-lines file
                    try {
                        Files.writeString(profilesOut, objJson + System.lineSeparator(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (Exception ex) {
                        System.err.println("Failed to append profile jsonl for " + fullUrl + " : " + ex.getMessage());
                    }

                    // Append to JSON array file safely
                    try {
                        appendObjectToJsonArray(exportOut, objJson);
                    } catch (Exception ex) {
                        System.err.println("Failed to append to recent_swedish_players_data.json for " + fullUrl + " : "
                                + ex.getMessage());
                    }

                    // Mark player as successfully scraped
                    status.markPlayerScraped(playerId);

                } catch (Exception ex) {
                    System.err.println("Failed to scrape profile for " + fullUrl + " : " + ex.getMessage());
                }
            }

            // Also append rows to the main `output.csv` including the Position JSON column
            try {
                Path outCsv = Path.of("output.csv");
                // write header if needed (keep same header as TableScapper)
                if (!Files.exists(outCsv)) {
                    String header = "User ID,Username,Name,Date of Birth,Age,Place of Birth,Nation,Youth Team,latest_team_position,latest_team,seasone,Position,Height,Weight,Shoots,Contract,Player Type,Cap Hit,Cap Hit Image,NHL Rights,Drafted,Highlights,Agency,Relation,Image URL,Skills,Status\n";
                    Files.writeString(outCsv, header, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);
                }

                try (BufferedWriter csvw = Files.newBufferedWriter(outCsv, StandardCharsets.UTF_8,
                        StandardOpenOption.APPEND)) {
                    for (Map.Entry<String, String> e2 : idToUrl.entrySet()) {
                        String playerId = e2.getKey();
                        String fullUrl = e2.getValue();
                        
                        // Skip already scraped players for CSV too
                        if (status.isPlayerScraped(playerId)) {
                            continue;
                        }
                        
                        try {
                            PlayerProfile profile = ProfileScapper.getProfile(fullUrl, playerId);
                            if (profile == null)
                                continue;

                            // fetch stats JSON string for CSV
                            String positionJsonStr = profile.position != null ? profile.position : "";
                            try {
                                String statsResponse = EliteProspectsAPI.fetchStatsFromAPI(profile.userId);
                                positionJsonStr = EliteProspectsAPI.parsePlayerStats(profile.position, statsResponse);
                            } catch (Exception ex) {
                                // keep position string
                            }

                            String playerTypeStr = String.join("; ",
                                    profile.playerType != null ? profile.playerType : new String[0]);
                            String highlightsStr = String.join("; ",
                                    profile.highlights != null ? profile.highlights : new String[0]);
                            String skillsStr = profile.getSkillsFormatted();

                            // Format latest_team_position similarly
                            String formattedPosition = "";
                            if (profile.latest_team_position != null) {
                                String temp = profile.latest_team_position.trim();
                                temp = temp.replaceAll("/.*$", "").trim();
                                if (!temp.isEmpty()) {
                                    if (!temp.startsWith("#"))
                                        formattedPosition = "#" + temp;
                                    else
                                        formattedPosition = temp;
                                }
                            }

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
                                    escapeForFormat(escapeCSV(positionJsonStr)),
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
                                if (i > 0)
                                    row.append(',');
                                row.append(fields[i]);
                            }
                            row.append('\n');
                            csvw.write(row.toString());

                        } catch (Exception ex) {
                            System.err
                                    .println("Failed to append to output.csv for " + fullUrl + " : " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Failed to write output.csv: " + ex.getMessage());
            }

            // --- RETRY FAILED PLAYERS ---
            retryFailedPlayers(status, profilesOut, exportOut, idToSlug);

            // Final status save
            status.save();
            System.out.println("[OK] Extraction complete!");
            System.out.println("   - Total teams processed: " + status.processedTeams.size());
            System.out.println("   - Total players scraped: " + status.scrapedPlayerIds.size());
            System.out.println(
                    "Done. URLs saved to recent_swedish_players_urls.txt, profiles to recent_swedish_players_profiles.jsonl and output.csv updated.");
            System.out.println("\nTo upload player data to API, run:");
            System.out.println("    mvn exec:java -Dexec.mainClass=com.brainium.core.ApiUploader");
            
        } catch (Exception e) {
            System.err.println("Error in SwedishPlayersExtractor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Build player object for JSON export from a PlayerProfile.
     */
    private static LinkedHashMap<String, Object> buildPlayerObject(PlayerProfile profile, String playerId, String fullUrl, Map<String, String> idToSlug) {
        LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
        Object uid;
        try {
            uid = Integer.parseInt(profile.userId != null ? profile.userId.trim() : playerId);
        } catch (Exception ex) {
            uid = profile.userId != null ? profile.userId : playerId;
        }
        obj.put("user_id", uid);
        obj.put("nation", profile.nation);
        obj.put("name", profile.name);
        obj.put("birthdate", profile.dateOfBirth);
        obj.put("latest_team", profile.latest_team);
        obj.put("profile_link", fullUrl);
        obj.put("player_username",
                profile.userName != null ? profile.userName : idToSlug.getOrDefault(playerId, ""));
        obj.put("dob_profile", profile.dateOfBirth);
        obj.put("age", profile.age);
        
        // Replace dashes with empty strings for API compatibility (API rejects "-")
        String placeOfBirth = profile.placeOfBirth != null && !profile.placeOfBirth.equals("-") 
            ? profile.placeOfBirth : "";
        obj.put("place_of_birth", placeOfBirth);
        
        // Fix dual nationality - extract first country only
        String nationNormalized = profile.nation;
        if (nationNormalized != null && nationNormalized.contains("/")) {
            // Extract first country from "USA / Sweden" -> "USA"
            nationNormalized = nationNormalized.split("/")[0].trim();
        }
        obj.put("nation_profile", nationNormalized);
        obj.put("youth_team", profile.youthTeam);

        // position: try to attach parsed stats JSON if available, otherwise position string
        String positionRaw = profile.position != null ? profile.position : "";
        try {
            String statsResponse = EliteProspectsAPI.fetchStatsFromAPI(profile.userId);
            String parsed = EliteProspectsAPI.parsePlayerStats(profile.position, statsResponse);
            // Send position as string (not parsed JSON object)
            obj.put("position", parsed);
        } catch (Exception ex) {
            obj.put("position", positionRaw);
        }

        obj.put("height", profile.height);
        obj.put("weight", profile.weight);
        
        // Always include shoots field (actual value or empty string)
        String shoots = profile.shoots;
        if (shoots == null || (shoots != null && (shoots.trim().isEmpty() || shoots.equals("-")))) {
            shoots = "";
        }
        obj.put("shoots", shoots != null ? shoots : "");
        
        obj.put("contract", profile.contract);
        
        // Player type as string (empty if no data)
        String playerTypeStr = "";
        if (profile.playerType != null && profile.playerType.length > 0) {
            playerTypeStr = String.join("; ", profile.playerType);
        }
        obj.put("player_type", playerTypeStr);
        
        obj.put("cap_hit", profile.capHit);
        obj.put("cap_hit_image", profile.capHitImage);
        obj.put("nhl_rights", profile.nhlRights);
        obj.put("drafted", profile.drafted);
        obj.put("agency", profile.agency);
        obj.put("profile_picture", profile.imageUrl);
        obj.put("relation", profile.relation);

        // Skills as string (empty if no data)
        String skillsStr = "";
        if (profile.skills != null && profile.skills.length > 0) {
            List<String> skillsList = new ArrayList<>();
            for (int i = 0; i < profile.skills.length; i++)
                skillsList.add(profile.skills[i].toFormattedString());
            skillsStr = String.join("; ", skillsList);
        }
        obj.put("skills", skillsStr);

        // Highlights as string (empty if no data)
        String highlightsStr = "";
        if (profile.highlights != null && profile.highlights.length > 0) {
            highlightsStr = String.join("; ", profile.highlights);
        }
        obj.put("highlights", highlightsStr);
        
        obj.put("status", profile.status);
        
        // Award as string (same as highlights)
        obj.put("award", highlightsStr);
        
        obj.put("latest_team_position", profile.latest_team_position);
        obj.put("season", profile.season);

        return obj;
    }

    /**
     * Retry failed players from failed_players.txt file.
     * Format: playerId,playerUserName,position,timestamp,errorMessage
     */
    private static void retryFailedPlayers(SweExtractorStatus status, Path profilesOut, Path exportOut, Map<String, String> idToSlug) {
        Path failedFile = Path.of("failed_players.txt");
        if (!Files.exists(failedFile)) {
            System.out.println("\n[INFO] No failed_players.txt found, skipping retry.");
            return;
        }

        System.out.println("\n========================================");
        System.out.println("🔄 RETRYING FAILED PLAYERS");
        System.out.println("========================================");

        try {
            List<String> lines = Files.readAllLines(failedFile, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                System.out.println("[INFO] failed_players.txt is empty, nothing to retry.");
                return;
            }

            System.out.println("Found " + lines.size() + " failed player(s) to retry...");

            int retrySuccess = 0;
            int retryFailed = 0;
            List<String> stillFailing = new ArrayList<>();
            Gson gson = new GsonBuilder().serializeNulls().create();

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                // Parse CSV line: playerId,playerUserName,position,timestamp,errorMessage
                String[] parts = line.split(",", 5);
                if (parts.length < 2) {
                    System.err.println("⚠️  Skipping malformed line: " + line);
                    stillFailing.add(line);
                    continue;
                }

                String playerId = parts[0].trim();
                String playerUserName = parts[1].trim();

                // Skip if already scraped
                if (status.isPlayerScraped(playerId)) {
                    System.out.println("  ⏭️  Player " + playerId + " already scraped, skipping retry.");
                    retrySuccess++;
                    continue;
                }

                // Construct profile URL
                String fullUrl = BASE + "/player/" + playerId + "/" + playerUserName;

                System.out.println("  🔄 Retrying player: " + playerId + " (" + playerUserName + ")");

                try {
                    // Add delay to avoid rate limiting
                    Thread.sleep(1000);

                    PlayerProfile profile = ProfileScapper.getProfile(fullUrl, playerId);
                    if (profile == null) {
                        System.err.println("    ❌ Retry failed: profile is null for " + playerId);
                        stillFailing.add(line);
                        retryFailed++;
                        continue;
                    }

                    // Build JSON object for this player
                    LinkedHashMap<String, Object> obj = buildPlayerObject(profile, playerId, fullUrl, idToSlug);
                    String objJson = gson.toJson(obj);

                    // Append to JSONL file
                    Files.writeString(profilesOut, objJson + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                    // Append to JSON array file
                    appendObjectToJsonArray(exportOut, objJson);

                    // Mark as successfully scraped
                    status.markPlayerScraped(playerId);

                    System.out.println("    ✓ Retry successful for player " + playerId);
                    retrySuccess++;

                } catch (Exception ex) {
                    System.err.println("    ❌ Retry failed for " + playerId + ": " + ex.getMessage());
                    stillFailing.add(line);
                    retryFailed++;
                }
            }

            System.out.println("\n[RETRY SUMMARY]");
            System.out.println("  ✓ Successful retries: " + retrySuccess);
            System.out.println("  ❌ Still failing: " + retryFailed);

            // Update failed_players.txt with only the players that still failed
            if (stillFailing.isEmpty()) {
                Files.deleteIfExists(failedFile);
                System.out.println("  🗑️  All failures resolved! Deleted failed_players.txt");
            } else {
                Files.write(failedFile, stillFailing, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("  📝 Updated failed_players.txt with " + stillFailing.size() + " remaining failures");
            }

        } catch (Exception ex) {
            System.err.println("Error during failed player retry: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // All static methods below remain inside the class

    private static Document fetchDocument(String url) throws IOException {
        // Only ensure cookies if cookiesStore is empty (shouldn't happen if login was successful)
        if (cookiesStore.isEmpty()) {
            System.out.println("  ⚠️  cookiesStore empty, attempting to collect cookies from fresh fetch...");
            ensureCookies(url);
        }

        System.out.println("  📡 Fetching: " + url);
        Connection conn = Jsoup.connect(url)
                .userAgent(
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
                .timeout(60000)
                .maxBodySize(0)
                .followRedirects(true);

        // add cookies collected
        if (!cookiesStore.isEmpty()) {
            conn.cookies(cookiesStore);
            // also set header formed from env COOKIE_HEADER + collected cookies
            StringBuilder ch = new StringBuilder();
            if (COOKIE_HEADER != null && !COOKIE_HEADER.isEmpty())
                ch.append(COOKIE_HEADER);
            cookiesStore.forEach((k, v) -> ch.append(k).append("=").append(v).append("; "));
            conn.header("cookie", ch.toString());
        } else if (COOKIE_HEADER != null && !COOKIE_HEADER.isEmpty()) {
            conn.header("cookie", COOKIE_HEADER);
        }

        Connection.Response resp = conn.execute();
        // update stored cookies
        cookiesStore.putAll(resp.cookies());
        return resp.parse();
    }

    /**
     * Ensure cookiesStore is populated. Cookies should already be set from API login
     * in main() method. This is kept as a safety fallback only.
     */
    private static void ensureCookies(String exampleUrl) {
        if (!cookiesStore.isEmpty())
            return;

        // If we reach here, it means API login failed or wasn't attempted.
        // Fallback: try anonymous fetch to collect basic session cookies
        System.err.println("Warning: cookiesStore is empty. Attempting anonymous fetch for basic cookies...");
        try {
            Connection.Response r = Jsoup.connect(exampleUrl)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .method(Connection.Method.GET)
                    .execute();
            cookiesStore.putAll(r.cookies());
        } catch (Exception e) {
            // ignore — we'll try with COOKIE_HEADER only
            System.err.println("Anonymous fetch failed: " + e.getMessage() + " — using EP_COOKIE_HEADER only");
        }
    }

    private static void collectFromTeam(String teamHref, Map<String, String> idToSlug, Map<String, String> idToUrl) {
        try {
            String teamUrl = teamHref.startsWith("http") ? teamHref : BASE + teamHref;
            System.out.println("Visiting team: " + teamUrl);
            // Persist this team immediately so interruptions still leave team list
            try {
                Path teamsAppend = Path.of("team.txt");
                Files.writeString(teamsAppend, teamUrl + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ex) {
                // non-fatal
            }
            Document doc = fetchDocument(teamUrl);

            // Find rows that include the Sweden flag image
            Elements swedenPlayerRows = doc.select("tr:has(img[alt='Sweden flag'])");
            System.out.println("  Sweden-flag rows found: " + swedenPlayerRows.size());

            for (Element row : swedenPlayerRows) {
                Element playerLink = row.selectFirst("a.TextLink_link__RhSiC[href^='/player/']");
                if (playerLink == null)
                    playerLink = row.selectFirst("a[href^='/player/']");
                if (playerLink != null) {
                    String href = playerLink.attr("href");
                    String fullUrl = href.startsWith("http") ? href : BASE + href;
                    String playerId = extractPlayerId(fullUrl);
                    String slug = extractPlayerSlug(fullUrl);
                    if (playerId != null) {
                        // store first-seen slug/url for the id
                        boolean isNew = !idToUrl.containsKey(playerId);
                        idToSlug.putIfAbsent(playerId, slug != null ? slug : playerLink.text().trim());
                        idToUrl.putIfAbsent(playerId, fullUrl);
                        System.out.println("    Swedish player: " + playerLink.text().trim() + " -> id=" + playerId
                                + " slug=" + slug + " url=" + fullUrl);

                        // Persist newly discovered URL and id line immediately so progress isn't lost
                        if (isNew) {
                            try {
                                Path urlsOut = Path.of("recent_swedish_players_urls.txt");
                                Files.writeString(urlsOut, fullUrl + System.lineSeparator(), StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (Exception ex) {
                                System.err.println("Failed to append url for " + fullUrl + " : " + ex.getMessage());
                            }

                            try {
                                Path idsOut = Path.of("recent_swedish_players_ids.txt");
                                String slugVal = idToSlug.getOrDefault(playerId, "");
                                String line = playerId + "," + slugVal + "," + fullUrl + System.lineSeparator();
                                Files.writeString(idsOut, line, StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (Exception ex) {
                                System.err.println("Failed to append id line for " + fullUrl + " : " + ex.getMessage());
                            }

                            // Immediately attempt to scrape and persist this player's profile (best-effort)
                            try {
                                Gson localGson = new GsonBuilder().serializeNulls().create();
                                PlayerProfile scraped = ProfileScapper.getProfile(fullUrl, playerId);
                                if (scraped != null) {
                                    String jsonLine = localGson
                                            .toJson(buildExportObject(scraped, playerId, fullUrl, idToSlug));
                                    Path profilesOut = Path.of("recent_swedish_players_profiles.jsonl");
                                    Files.writeString(profilesOut, jsonLine + System.lineSeparator(),
                                            StandardCharsets.UTF_8,
                                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                                    // append to array file
                                    Path exportOut = Path.of("recent_swedish_players_data.json");
                                    try {
                                        appendObjectToJsonArray(exportOut, jsonLine);
                                    } catch (Exception ex) {
                                        System.err.println("Failed to append to recent_swedish_players_data.json for "
                                                + fullUrl + " : " + ex.getMessage());
                                    }
                                }
                            } catch (Exception ex) {
                                // non-fatal; log and continue
                                System.err.println(
                                        "Failed to fetch+persist profile for " + fullUrl + " : " + ex.getMessage());
                            }
                        }
                    } else {
                        System.out.println(
                                "    Swedish player (no id found): " + playerLink.text().trim() + " -> " + fullUrl);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error visiting team page " + teamHref + " : " + e.getMessage());
        }
    }

    /**
     * Finds all game rows that belong to the given date header row.
     * Stops when it reaches another date header (title row or date td).
     */
    /**
     * Scan rows from the top of the table (body) until a date header is found.
     * Used for continuation pages where the date block started on previous page.
     */
    public static List<Element> collectGamesFromTop(Document doc) {
        List<Element> rows = new ArrayList<>();
        // Select the table. Usually it is a table with class "table" or "table-striped"
        // inside the main content.
        Element table = doc.selectFirst("table.table");
        if (table == null)
            return rows;

        Elements trs = table.select("tbody > tr");
        for (Element tr : trs) {
            // Stop if we hit a date header (indicating a new date block)
            // A date header typically has class "title" or contains data-date attribute
            boolean isDateHeader = tr.hasClass("title")
                    || tr.selectFirst("td[data-date-type]") != null
                    || tr.hasAttr("data-date");

            if (isDateHeader) {
                break;
            }

            // Collecting game rows
            Elements teams = tr.select("td.team");
            Element resultCell = tr.selectFirst("td.result");
            if (teams.size() >= 2 && resultCell != null) {
                rows.add(tr);
            }
        }
        return rows;
    }

    public static List<Element> getGameRowsForDate(Element dateHeaderRow) {
        List<Element> gameRows = new ArrayList<>();

        if (dateHeaderRow == null) {
            return gameRows;
        }

        Element current = dateHeaderRow.nextElementSibling();
        while (current != null) {
            // Stop condition: we've reached the next date header row.
            // The header row uses a `td` with `data-date-type="text"` (visible date text).
            boolean isNextDateHeader = current.hasClass("title")
                    || current.selectFirst("td[data-date-type=text]") != null
                    || current.selectFirst("td[data-date-type=\"text\"]") != null;

            if (isNextDateHeader) {
                break;
            }

            // Typical game row detection (adjust if needed)
            Elements teams = current.select("td.team");
            Element resultCell = current.selectFirst("td.result");

            if (teams.size() >= 2 && resultCell != null) {
                gameRows.add(current);
            }

            current = current.nextElementSibling();
        }

        return gameRows;
    }

    private static String extractPlayerId(String playerUrl) {
        if (playerUrl == null)
            return null;
        Matcher m = PLAYER_ID_PATTERN.matcher(playerUrl);
        if (m.find())
            return m.group(1);
        // fallback: look for query param player=
        try {
            int idx = playerUrl.indexOf("player=");
            if (idx >= 0) {
                String rest = playerUrl.substring(idx + "player=".length());
                String digits = rest.replaceAll("[^0-9].*", "");
                if (!digits.isEmpty())
                    return digits;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private static String extractPlayerSlug(String playerUrl) {
        if (playerUrl == null)
            return null;
        // pattern: /player/{id}/{slug}
        try {
            Pattern p = Pattern.compile("/player(?:\\.php\\?player=|/)(?:\\d+)/(.*)$");
            Matcher m = p.matcher(playerUrl);
            if (m.find()) {
                String slug = m.group(1);
                // strip query params
                int q = slug.indexOf('?');
                if (q >= 0)
                    slug = slug.substring(0, q);
                return slug;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * Escapes a string for CSV format by wrapping in quotes if it contains commas,
     * quotes, or newlines.
     */
    private static String escapeCSV(String value) {
        if (value == null)
            return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }
        return value;
    }

    /**
     * Escape '%' characters for safe formatting (mirrors TableScapper helper).
     */
    private static String escapeForFormat(String value) {
        if (value == null)
            return "";
        return value.replace("%", "%%");
    }

    /**
     * Append a JSON object (string) into a file that stores wrapped in recentlyUpdatedPlayers key.
     * Format: { "recentlyUpdatedPlayers": [ {...}, {...} ] }
     */
    private static void appendObjectToJsonArray(Path file, String jsonObject) throws IOException {
        Gson compactGson = new GsonBuilder().serializeNulls().create();
        
        if (!Files.exists(file)) {
            // Create new structure with wrapper object
            JsonArray arr = new JsonArray();
            arr.add(JsonParser.parseString(jsonObject));
            
            JsonObject wrapper = new JsonObject();
            wrapper.add("recentlyUpdatedPlayers", arr);
            
            String formatted = compactGson.toJson(wrapper);
            Files.writeString(file, formatted, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            return;
        }

        // Read current file and parse
        String content = Files.readString(file, StandardCharsets.UTF_8).trim();
        JsonArray arr;
        
        if (content.isEmpty()) {
            arr = new JsonArray();
        } else {
            try {
                JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                if (root.has("recentlyUpdatedPlayers")) {
                    arr = root.getAsJsonArray("recentlyUpdatedPlayers");
                } else {
                    arr = new JsonArray();
                }
            } catch (Exception e) {
                // Corrupt or old format, start fresh
                arr = new JsonArray();
            }
        }
        
        // Add new object and write back with wrapper
        arr.add(JsonParser.parseString(jsonObject));
        
        JsonObject wrapper = new JsonObject();
        wrapper.add("recentlyUpdatedPlayers", arr);
        
        String formatted = compactGson.toJson(wrapper);
        Files.writeString(file, formatted, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Build the export object map for a given PlayerProfile using the requested
     * keys.
     */
    private static Map<String, Object> buildExportObject(PlayerProfile profile, String playerId, String fullUrl,
            Map<String, String> idToSlug) {
        Map<String, Object> obj = new LinkedHashMap<>();
        Object uid;
        try {
            String effectiveId = (profile.userId != null && !profile.userId.trim().isEmpty()) ? profile.userId.trim()
                    : playerId;
            uid = Integer.parseInt(effectiveId);
        } catch (Exception ex) {
            uid = (profile.userId != null && !profile.userId.trim().isEmpty()) ? profile.userId : playerId;
        }
        obj.put("user_id", uid);
        obj.put("nation", profile.nation);
        obj.put("name", profile.name);
        obj.put("birthdate", profile.dateOfBirth);
        obj.put("latest_team", profile.latest_team);
        obj.put("profile_link", fullUrl);
        obj.put("player_username", profile.userName != null ? profile.userName : idToSlug.getOrDefault(playerId, ""));
        obj.put("dob_profile", profile.dateOfBirth);
        obj.put("age", profile.age);
        
        // Replace dashes with empty strings for API compatibility (API rejects "-")
        String placeOfBirth = profile.placeOfBirth != null && !profile.placeOfBirth.equals("-") 
            ? profile.placeOfBirth : "";
        obj.put("place_of_birth", placeOfBirth);
        
        // Fix dual nationality - extract first country only
        String nationNormalized = profile.nation;
        if (nationNormalized != null && nationNormalized.contains("/")) {
            // Extract first country from "USA / Sweden" -> "USA"
            nationNormalized = nationNormalized.split("/")[0].trim();
        }
        obj.put("nation_profile", nationNormalized);
        obj.put("youth_team", profile.youthTeam);

        // Try to collect stats JSON into the position field. Use the provided playerId
        // if profile.userId is missing to ensure we fetch the correct data.
        try {
            String effectiveId = (profile.userId != null && !profile.userId.trim().isEmpty()) ? profile.userId.trim()
                    : playerId;
            String statsResponse = EliteProspectsAPI.fetchStatsFromAPI(effectiveId);
            String parsed = EliteProspectsAPI.parsePlayerStats(profile.position, statsResponse);
            // Send position as string (not parsed JSON object)
            obj.put("position", parsed);
        } catch (Exception ex) {
            obj.put("position", profile.position != null ? profile.position : "");
        }

        obj.put("height", profile.height);
        obj.put("weight", profile.weight);
        
        // Always include shoots field (actual value or empty string)
        String shoots = profile.shoots;
        if (shoots == null || (shoots != null && (shoots.trim().isEmpty() || shoots.equals("-")))) {
            shoots = "";
        }
        obj.put("shoots", shoots != null ? shoots : "");
        
        obj.put("contract", profile.contract);
        
        // Player type as string (empty if no data)
        String playerTypeStr = "";
        if (profile.playerType != null && profile.playerType.length > 0) {
            playerTypeStr = String.join("; ", profile.playerType);
        }
        obj.put("player_type", playerTypeStr);
        
        obj.put("cap_hit", profile.capHit);
        obj.put("cap_hit_image", profile.capHitImage);
        obj.put("nhl_rights", profile.nhlRights);
        obj.put("drafted", profile.drafted);
        obj.put("agency", profile.agency);
        obj.put("profile_picture", profile.imageUrl);
        obj.put("relation", profile.relation);

        // Skills as string (empty if no data)
        String skillsStr = "";
        if (profile.skills != null && profile.skills.length > 0) {
            List<String> skillsList = new ArrayList<>();
            for (int i = 0; i < profile.skills.length; i++)
                skillsList.add(profile.skills[i].toFormattedString());
            skillsStr = String.join("; ", skillsList);
        }
        obj.put("skills", skillsStr);

        // Highlights as string (empty if no data)
        String highlightsStr = "";
        if (profile.highlights != null && profile.highlights.length > 0) {
            highlightsStr = String.join("; ", profile.highlights);
        }
        obj.put("highlights", highlightsStr);
        
        obj.put("status", profile.status);
        
        // Award as string (same as highlights)
        obj.put("award", highlightsStr);
        obj.put("latest_team_position", profile.latest_team_position);
        obj.put("season", profile.season);

        return obj;
    }
}