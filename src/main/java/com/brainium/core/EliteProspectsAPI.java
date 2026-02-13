package com.brainium.core;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class EliteProspectsAPI {
    /**
     * Logs in to EliteProspects and returns the tokens needed for authenticated requests.
     * @param email The login email/username
     * @param password The login password
     * @return JsonObject containing tokens (e.g., streamToken, token, etc.)
     * @throws IOException
     * @throws InterruptedException
     */
    public static JsonObject loginAndGetTokens(String email, String password) throws IOException, InterruptedException {
        String loginUrl = "https://www.eliteprospects.com/api/next/auth/login";
        JsonObject credentials = new JsonObject();
        credentials.addProperty("email", email);
        credentials.addProperty("password", password);
        credentials.addProperty("originSite", "web"); // Required by API
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/json")
                .header("accept", "application/json, text/plain, */*")
                .POST(HttpRequest.BodyPublishers.ofString(credentials.toString()))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Login failed: HTTP " + response.statusCode() + " - " + response.body());
        }
        // Parse the response JSON for tokens
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json;
    }
    private static final String API_URL = "https://gql.eliteprospects.com/";

    // ---------- MODEL CLASSES (CLEAN OUTPUT STRUCTURE) ----------
    static class CleanStats {
        @SerializedName("seasonSlug")
        String seasonSlug;
        @SerializedName("flagUrl")
        String flagUrl;
        @SerializedName("teamName")
        String teamName;
        @SerializedName("leagueName")
        String leagueName;
        @SerializedName("regularStats")
        JsonObject regularStats;
        @SerializedName("postseasonStats")
        JsonElement postseasonStats;
    }

    static class FinalOutput {
        @SerializedName("position")
        String position;
        @SerializedName("stats")
        List<CleanStats> stats = new ArrayList<>();
    }

    // ---------- GRAPHQL REQUEST ----------
    public static String fetchStatsFromAPI(String playerId) throws IOException, InterruptedException {
        String query = """
                query PlayerStatisticsDefault($player: ID, $statsType: String, $leagueType: LeagueType, $sort: String, $seasonFrom: String) {\n  playerStats(player: $player, statsType: $statsType, leagueType: $leagueType, sort: $sort, seasonFrom: $seasonFrom) {\n    edges {\n      season { slug }\n      team { country { flagUrl { small } } }\n      teamName\n      leagueName\n      regularStats {\n        GP G A PTS PIM PM GAA SVP SVS SO W L T GD GA TOI\n      }\n      postseasonStats {\n        GP G A PTS PIM PM GAA SVP SVS SO W L T GD GA TOI\n      }\n    }\n  }\n}\n""";
        JsonObject payload = new JsonObject();
        payload.addProperty("query", query);
        JsonObject vars = new JsonObject();
        vars.addProperty("player", playerId);
        payload.add("variables", vars);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    // ---------- PARSER ----------
    public static String parsePlayerStats(String position, String jsonResponse) {
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray edges = root.getAsJsonObject("data")
                    .getAsJsonObject("playerStats")
                    .getAsJsonArray("edges");
            FinalOutput output = new FinalOutput();
            output.position = position;
            for (JsonElement e : edges) {
                JsonObject edge = e.getAsJsonObject();
                CleanStats cs = new CleanStats();
                // Defensive null checks for nested fields
                JsonObject season = edge.has("season") && !edge.get("season").isJsonNull() ? edge.getAsJsonObject("season") : null;
                cs.seasonSlug = (season != null && season.has("slug") && !season.get("slug").isJsonNull()) ? season.get("slug").getAsString() : null;

                JsonObject team = edge.has("team") && !edge.get("team").isJsonNull() ? edge.getAsJsonObject("team") : null;
                cs.flagUrl = null;
                if (team != null && team.has("country") && !team.get("country").isJsonNull()) {
                    JsonObject country = team.getAsJsonObject("country");
                    if (country.has("flagUrl") && !country.get("flagUrl").isJsonNull()) {
                        JsonObject flagUrl = country.getAsJsonObject("flagUrl");
                        if (flagUrl.has("small") && !flagUrl.get("small").isJsonNull()) {
                            cs.flagUrl = flagUrl.get("small").getAsString();
                        }
                    }
                }
                cs.teamName = edge.has("teamName") && !edge.get("teamName").isJsonNull() ? edge.get("teamName").getAsString() : null;
                cs.leagueName = edge.has("leagueName") && !edge.get("leagueName").isJsonNull() ? edge.get("leagueName").getAsString() : null;
                // Ensure both regularStats and postseasonStats always have all expected keys,
                // even if the response omits them. This keeps the output structure consistent.
                String[] statsKeys = {"GP","G","A","PTS","PIM","PM","GAA","SVP","SVS","SO","W","L","T","GD","GA","TOI"};

                // Build regularStats object with guaranteed keys
                JsonObject regularStatsObj = new JsonObject();
                if (edge.has("regularStats") && !edge.get("regularStats").isJsonNull() && edge.get("regularStats").isJsonObject()) {
                    JsonObject raw = edge.getAsJsonObject("regularStats");
                    for (String key : statsKeys) {
                        regularStatsObj.add(key, raw.has(key) ? raw.get(key) : JsonNull.INSTANCE);
                    }
                } else {
                    for (String key : statsKeys) {
                        regularStatsObj.add(key, JsonNull.INSTANCE);
                    }
                }
                cs.regularStats = regularStatsObj;

                // Ensure postseasonStats always has all expected keys as well
                JsonObject postseasonStatsObj = new JsonObject();
                if (edge.has("postseasonStats") && !edge.get("postseasonStats").isJsonNull() && edge.get("postseasonStats").isJsonObject()) {
                    JsonObject raw = edge.getAsJsonObject("postseasonStats");
                    for (String key : statsKeys) {
                        postseasonStatsObj.add(key, raw.has(key) ? raw.get(key) : JsonNull.INSTANCE);
                    }
                } else {
                    for (String key : statsKeys) {
                        postseasonStatsObj.add(key, JsonNull.INSTANCE);
                    }
                }
                cs.postseasonStats = postseasonStatsObj;
                output.stats.add(cs);
            }
            return gson.toJson(output);
        } catch (Exception ex) {
            System.err.println("Error parsing player stats JSON: " + ex.getMessage());
            System.err.println("Raw response: " + jsonResponse);
            return position; // fallback
        }
    }
}
