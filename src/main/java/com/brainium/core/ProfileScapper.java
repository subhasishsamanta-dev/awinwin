package com.brainium.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.brainium.schema.PlayerProfile;
import com.brainium.schema.Skill;
import com.brainium.schema.SkillResponse;
import com.brainium.service.ApiService;
import com.google.gson.Gson;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Utility class for scraping hockey player profiles from EliteProspects.
 */
public class ProfileScapper {

    private static final String API_BASE_URL = System.getenv("API_BASE_URL") != null
            ? System.getenv("API_BASE_URL")
            : "https://gql.eliteprospects.com";

    private static ApiService apiService;
    private static final Gson gson = new Gson();

    /**
     * Initialize the API service with Retrofit
     */
    private static ApiService getApiService() {
        if (apiService == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(API_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    /**
     * POJO for GraphQL request body serialization
     */
    private static class GraphQLRequest {
        @SuppressWarnings("unused")
        final Map<String, Object> variables;
        @SuppressWarnings("unused")
        final String query;

        GraphQLRequest(Map<String, Object> variables, String query) {
            this.variables = variables;
            this.query = query;
        }
    }

    /**
     * Fetch endorsement skills using GraphQL via Retrofit.
     * Uses Gson to serialize the request body to avoid String.format issues.
     */
    private static List<Skill> fetchSkills(String playerId) throws Exception {
        ApiService service = getApiService();

        // GraphQL query (kept as a Java string)
        String query = "query Endorsements($profileId: ID!, $type: String!) {"
                + " endorsements(id: $profileId, type: $type) {"
                + "   id"
                + "   upvotes"
                + "   userUpvoted"
                + "   type { id name __typename }"
                + "   members { id displayName url avatarUrl hockeyRelations { id name __typename } __typename }"
                + "   __typename"
                + " }"
                + "}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("profileId", playerId);
        variables.put("type", "player");

        GraphQLRequest graphQLRequest = new GraphQLRequest(variables, query);
        String jsonBody = gson.toJson(graphQLRequest);
        RequestBody requestBody = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                jsonBody);

        Response<SkillResponse> response = service.getSkills(requestBody).execute();

        if (response.isSuccessful() && response.body() != null) {
            SkillResponse skillResponse = response.body();
            System.out.println("fetchSkills response raw: " + response.raw().toString());

            if (skillResponse.data == null || skillResponse.data.endorsements == null) {
                return new ArrayList<>();
            }

            return skillResponse.data.endorsements.stream()
                    .map(e -> new Skill(e.type.name))
                    .collect(Collectors.toList());
        } else {
            String err = response.errorBody() != null ? response.errorBody().string() : "unknown error";
            throw new Exception("Failed to fetch skills: " + err);
        }
    }

    /**
     * Scrapes a player profile from the given EliteProspects URL.
     *
     * @param url      The URL of the player's EliteProspects page
     * @param playerId Player numeric id used for GraphQL lookups
     * @return PlayerProfile object populated with scraped data
     * @throws Exception if the page cannot be loaded or parsed
     */
    public static PlayerProfile getProfile(String url, String playerId) throws Exception {
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;

        // Define cookie header once and reuse for both main and fallback requests
        String cookieHeader = "d_s_i=1; _ga=GA1.1.152131760.1767172627; _scid=UIjaj-w3hUhSXMB_YLKN4z6dlgJsJyOr; _fbp=fb.1.1767172627831.76938125457577469; _tt_enable_cookie=1; _ttp=01KDSV4QDNZ30XXNB5T8XK68P5_.tt.1; fpestid=jMLGKFOwSO1RxDfeIjxG-031FISc9hDPzNCc6qPewIQ-E3PtQi6QIxqdJfhWr6AP12Wk2g; hubspotutk=1346c7b3759bcb9f657ed7b1eb448a8a; ext_name=ojplmecpdpgccookcobabopnaifgidhf; muxData==undefined&mux_viewer_id=70b3befa-83c8-4699-b7bf-c17bb11c6950&msn=0.954179924005161&sid=6b06c268-d5d5-40bd-881d-611cb11c6956&sst=1768202987862&sex=1768204612090; _ScCbts=%5B%22126%3Bchrome.2%3A2%3A5%22%2C%22317%3Bchrome.2%3A2%3A5%22%5D; _sctr=1%7C1769106600000; _clck=1ovt09h%5E2%5Eg34%5E0%5E2191; __hssrc=1; aws-waf-token=00202d34-d912-4e46-ac39-4c15481914aa:HgoAdztL2jCSAAAA:jt/+UDmeNb0vLbUCj6+wN7R64iLLMy78K87FHWkdoaYmqsH3e2wucSufFLlvTZxGpuTn6P+qKwQEVHdYcfcBgWTqpiqYzZXPR0D1t7DwLAoYdvNs21bpf6wjuCSkq1bMhB1jDpIbpSN4AciYi98/8b4XyioPAW0Mlq2dqmY/jofTaTY5tdJFTmpQrhxiofVS8vi3LHwVUXApf5F6R3jjdj9136SYCdpKSz0p5xxtgyA+yrs8mJ4TTO6dZf995aUbvVi2TvZAbyOlRQrPfw==; frequencyWall=%7B%22enabled%22%3Atrue%2C%22reached%22%3Afalse%2C%22reachedDate%22%3Anull%2C%22reachedBefore%22%3Afalse%7D; __Host-next-auth.csrf-token=969f8150e42205319970606a9d0d816ca2c8769747f55557ff4b302e6cc62c2f%7C442290e003aa8595089b3531b448e765c50bb7f549abbcecae2acd08124768d3; __Secure-next-auth.callback-url=https%3A%2F%2Fwww.eliteprospects.com%2F; __hstc=92623682.1346c7b3759bcb9f657ed7b1eb448a8a.1767172677666.1769692515508.1769695001974.25; ajs_anonymous_id=280068c8-0524-421d-b028-44efadac2b7a; _rdt_uuid=1768217690828.122d4ab8-7f92-4059-8aa8-2bdb77bb5949; _scid_r=ZQjaj-w3hUhSXMB_YLKN4z6dlgJsJyOrK6qa4g; __hssc=92623682.3.1769695001974; g_state={\"i_l\":0,\"i_ll\":1769695255180,\"i_b\":\"sXc/7ANauT7KIMaXD0d860sHUh39IQRg6Url58HUhlM\",\"i_e\":{\"enable_itp_optimization\":3}}; _gcl_au=1.1.649570095.1767172626.2058622957.1769695273.1769695287; ep_next_token=eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJhdWQiOiIxIiwianRpIjoiZmZhMTdkOTkwMThjMjkyYzg1YThkNzVlNWVlNjE2NjUzZjY4YmFhYTU3YTMzYTkyZmUzZmE0YTUzODk0YzEyODIzMTg0OGQyZjM2NDFmZDAiLCJpYXQiOjE3Njk2OTUyODguMjcwMTUxLCJuYmYiOjE3Njk2OTUyODguMjcwMTU0LCJleHAiOjE3NzIzNzM2ODguMjY0MzA3LCJzdWIiOiI2Mzg5NDIiLCJzY29wZXMiOltdfQ.BIDi8G2NHcRsqQPOnjnQCF32j-DMAQVSgvowp9MQIOzTkUz_Ws6fcty5KNpX5aGr0vW9Jmfk1CQWsR0GAZjh9FzJkLuH31i9uvGn5tf4jsYQbFqhXiHvjXxzj425cT191LYLfSZ9nWyXZ-DHvwU0SVV5F5qMH6q0m92SsCq6dJQkZo6wMmJN3LoP8lxWsNIW2cajECkXcWMFncz6Yq9H5cHUwfDWoxn4SpaQjjgA9Ra-i4whrzw37zbX4oOEbCquHWkcX3Z0xwUk8V7ro7-lEiGqp7cKNYHRPORBfbvZw440UmwHf80jYB4CkmRcW-Ra9YbA9e2ie-I9uN_fBUlWidh2lbYzTzsTKHRjARtRWyOwvZi0ShF-XHYFLLSpBu13r5KT6QUv2vZNiWcFo_1c-IImHqLpgm6wK3DhVEqEFfrQl2qLO6dLRmbPLJvtJvd5LTigFNDbSYgk1Dt-2bmOjGEvcRfjqJKc2LzlnpMG2s2r-5tTcYKl-ddX0hX8ksQo0K-9K0i-8KuWfLL4DC8p0iFZrSXWosrX0MH_YmIxMRlLlakgyELqg-XMFr_gqhjrGdt51nT0nk9Ojqq7oQACQlDzDE6qgRJqKzuSDTo02qPx-ZrgaPBX8rcaURKyvHKAmEgHQkJZuKyK6y-DZBaAsYEaSFMAPjDypzNqRXhILc4; ttcsid_CAVB9F3C77U5TKNG85B0=1769694994436::R-cGNV-yHMelUmyjiw3g.24.1769695291050.1; ttcsid=1769694994468::oGBXm8nOzsOFgbe0YCmG.24.1769695291051.0; _clsk=kmu1v4%5E1769695291953%5E7%5E0%5Ev.clarity.ms%2Fcollect; _ga_HR081V9N2K=GS2.1.s1769694977$o32$g1$t1769695292$j27$l0$h1812225356; ajs_user_id=638942; __gads=ID=0adf47fdff643741:T=1767172647:RT=1769695292:S=ALNI_MauHOSGlkydjaXJhTQRrZqXdvuYAg; __gpi=UID=000011d87f9dddae:T=1767172647:RT=1769695292:S=ALNI_MbmHTNttCo1swxyCBGeLkZWrXfVcQ; __eoi=ID=3d1448c37e4a6bf7:T=1767172647:RT=1769695292:S=AA-AfjbwKZcHBjMCgadvygQ3UaSR";

        while (retryCount < maxRetries) {
            try {
                Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
                    .header("cookie", cookieHeader)
                        .timeout(60000) // Increased to 60 seconds
                        .maxBodySize(0) // No limit on body size
                        .get();

                // If successful, process and return. Ensure returned PlayerProfile includes userId and userName
                PlayerProfile profile = extractProfileData(doc, playerId);
                if (profile == null) return null;
                // set userId from parameter
                profile.userId = playerId;
                // try to extract username/slug from the URL: /player/{id}/{slug}
                try {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("/player(?:\\.php\\?player=|/)(?:\\d+)/(.*?)(?:$|\\?)");
                    java.util.regex.Matcher m = p.matcher(url);
                    if (m.find()) {
                        String slug = m.group(1);
                        // strip any trailing fragments or query
                        int q = slug.indexOf('?');
                        if (q >= 0) slug = slug.substring(0, q);
                        profile.userName = slug;
                    } else {
                        // fallback: if no slug segment, leave userName null
                        profile.userName = profile.userName != null ? profile.userName : null;
                    }
                } catch (Exception ex) {
                    // ignore; leave userName as-is
                }

                return profile;
            } catch (java.net.SocketTimeoutException e) {
                lastException = e;
                retryCount++;
                System.out.println("Timeout on attempt " + retryCount + " for player " + playerId + ". Retrying...");

                if (retryCount < maxRetries) {
                    // Exponential backoff: wait 2^retryCount seconds
                    Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                }
            } catch (org.jsoup.HttpStatusException e) {
                lastException = e;
                retryCount++;
                System.out.println("HTTP error " + e.getStatusCode() + " on attempt " + retryCount + " for player " + playerId + " (url: " + url + ")");

                // If we get a 404 when URL is of the form /player/{id} (no slug), try a fallback
                // by appending the playerId as a slug segment: /player/{id}/{id}
                if (e.getStatusCode() == 404 && url.matches(".*/player/\\d+$")) {
                    String altUrl = url + "/" + playerId;
                    try {
                        System.out.println("Attempting fallback URL: " + altUrl);
                        Document doc = Jsoup.connect(altUrl)
                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/117.0.0.0 Safari/537.36")
                            .header("cookie", cookieHeader)
                                .timeout(60000)
                                .maxBodySize(0)
                                .get();
                        // If fallback succeeds, extract and return
                        return extractProfileData(doc, playerId);
                    } catch (Exception exFallback) {
                        System.out.println("Fallback URL failed: " + exFallback.getMessage());
                        // continue with retry handling below
                    }
                }

                if (retryCount < maxRetries) {
                    Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                }
            } catch (Exception e) {
                // For other exceptions, don't retry - throw immediately
                throw e;
            }
        }

        // If all retries failed, throw the last exception
        throw new Exception("Failed to fetch profile for player " + playerId + " after " + maxRetries + " attempts", lastException);
    }

    /**
     * Extracts profile data from the parsed HTML document.
     */
    private static PlayerProfile extractProfileData(Document doc, String playerId) throws Exception {
        // Build a map of label -> dd element for flexible extraction.
        // The site uses <dl><dt>Label</dt><dd>Value</dd> structure inside #player-facts.
        Elements dts = doc.select("#player-facts dt, #player-facts .FactsList_factsListItem__B5TFr dt");
        java.util.Map<String, Element> factsByLabel = new java.util.HashMap<>();
        for (Element dt : dts) {
            String label = dt.text() != null ? dt.text().trim() : null;
            if (label == null || label.isEmpty()) continue;
            // prefer the sibling <dd>; fallback to searching within parent
            Element dd = dt.nextElementSibling();
            if (dd == null || !"dd".equalsIgnoreCase(dd.tagName())) {
                Element parent = dt.parent();
                if (parent != null) dd = parent.selectFirst("dd");
            }
            if (dd != null) {
                factsByLabel.put(label.replaceAll("\\s+", " ").trim(), dd);
            } else {
                // as a last resort, put the dt element itself so callers can try
                factsByLabel.put(label.replaceAll("\\s+", " ").trim(), dt);
            }
        }

        // Helper to extract the value text from the mapped element (prefer dd text)
        java.util.function.BiFunction<String, String, String> extractValue = (lbl, def) -> {
            if (lbl == null) return def;
            Element e = factsByLabel.get(lbl);
            if (e == null) return def;
            // If this is a dd, return its text; if it's a dt, try to get sibling
            if ("dd".equalsIgnoreCase(e.tagName())) {
                return e.text().trim();
            }
            // try sibling dd
            Element dd = e.nextElementSibling();
            if (dd != null && "dd".equalsIgnoreCase(dd.tagName())) return dd.text().trim();
            // fallback: return element's own text without the label
            String full = e.text();
            if (full == null) return def;
            String value = full.replaceFirst("(?i)" + java.util.regex.Pattern.quote(lbl), "").replaceFirst(":", "").trim();
            return value.isEmpty() ? def : value;
        };

        // Name
        String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : "";

        // Common labels we expect
        String dob = extractValue.apply("Date of Birth", "");
        if (dob.isEmpty()) dob = extractValue.apply("Born", "");
        String age = extractValue.apply("Age", "");
        String agency = extractValue.apply("Agency", "");
        String pob = extractValue.apply("Place of Birth", "");
        if (pob.isEmpty()) pob = extractValue.apply("Born in", "");
        String nation = extractValue.apply("Nation", "");
        String youth = extractValue.apply("Youth Team", "");

        // Position / Latest team / season — split into three parts per requested rules
        String latest_team_position = ""; // e.g. "#21" or "#92"
        String latest_team = ""; // e.g. "SK Iron / Division 2" or "Fargo Force / USHL"
        String season = ""; // e.g. "23/24" or "25/26"
        
        Element h2 = doc.selectFirst("h2.Profile_subTitle__MJ_YS, h2");
        if (h2 != null) {
            // Extract latest_team_position: the #number at the beginning
            try {
                String own = h2.ownText() != null ? h2.ownText().trim() : "";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("#\\d+").matcher(own);
                if (m.find()) {
                    latest_team_position = m.group(0);
                }
            } catch (Exception ex) {
                latest_team_position = "";
            }

            // Extract latest_team: combine all <a> links with " / " separator
            // Example: "SK Iron / Division 2" or "Fargo Force / USHL"
            try {
                Elements links = h2.select("a.TextLink_link__RhSiC");
                if (links.size() > 0) {
                    StringBuilder teamBuilder = new StringBuilder();
                    for (int i = 0; i < links.size(); i++) {
                        if (i > 0) teamBuilder.append(" / ");
                        teamBuilder.append(links.get(i).text().trim());
                    }
                    latest_team = teamBuilder.toString();
                }
            } catch (Exception ex) {
                latest_team = "";
            }

            // Extract season: text after the last '-' character
            // Example: "23/24" from "... - 23/24"
            try {
                String fullText = h2.text() != null ? h2.text().trim() : "";
                if (!fullText.isEmpty() && fullText.contains("-")) {
                    season = fullText.substring(fullText.lastIndexOf('-') + 1).trim();
                } else {
                    // fallback: try to find pattern like 25/26 or 2023-2024
                    java.util.regex.Matcher seasonMatcher = java.util.regex.Pattern.compile("(\\d{2}\\/\\d{2,4}|\\d{4}[\\-/]\\d{2,4})").matcher(fullText);
                    if (seasonMatcher.find()) season = seasonMatcher.group(1);
                }
            } catch (Exception ex) {
                season = "";
            }
        }

        // Other fields from facts
        String position = extractValue.apply("Position", "");
        String height = extractValue.apply("Height", "");
        String weight = extractValue.apply("Weight", "");
        String shoots = extractValue.apply("Shoots", "");
        if (shoots.isEmpty()) shoots = extractValue.apply("Catches", "");
        String contract = extractValue.apply("Contract", "");

        // Player Type may be tags within the li
        List<String> playerType = new ArrayList<>();
        Element ptEl = factsByLabel.get("Player Type");
        if (ptEl != null) {
            Elements tags = ptEl.select("div[class*='Tag'], span[class*='Tag'], a[class*='Tag']");
            for (Element t : tags) {
                String txt = t.text().trim();
                if (!txt.isEmpty() && !playerType.contains(txt)) playerType.add(txt);
            }
            if (playerType.isEmpty()) {
                // fallback: the whole li may contain comma-separated types
                String v = extractValue.apply("Player Type", "");
                if (!v.isEmpty()) playerType.addAll(java.util.Arrays.asList(v.split("\\s*[;,]\\s*")));
            }
        }

        String capHit = extractValue.apply("Cap Hit", "");
        String nhlRights = extractValue.apply("NHL Rights", "");
        String drafted = extractValue.apply("Drafted", "");

        List<String> highlights = new ArrayList<>();
        Element hiEl = factsByLabel.get("Highlights");
        if (hiEl != null) {
            // Prefer only elements that carry the data-tooltip-content attribute.
            Elements tips = hiEl.select("[data-tooltip-content]");
            for (Element t : tips) {
                String v = t.attr("data-tooltip-content");
                if (v != null && !v.trim().isEmpty()) {
                    highlights.add(v.trim());
                }
            }
            // As a fallback, if no data-tooltip-content attributes were found,
            // fall back to extracting the whole Highlights text, but still strip
            // leading numeric tokens (e.g., "1 1 2 1 ...") if present.
            if (highlights.isEmpty()) {
                String v = extractValue.apply("Highlights", "");
                if (v != null && !v.trim().isEmpty()) {
                    // Remove any leading runs of numbers and whitespace like "1 1 2 1 "
                    v = v.replaceFirst("^[0-9](?:\\s+[0-9])*\\s*", "").trim();
                    if (!v.isEmpty()) highlights.add(v);
                }
            }
        }

        // Cap hit image: pick first image inside player-facts or a sensible selector
        String capHitImage = "";
        Element capHitImageElement = doc.selectFirst("#player-facts img, .PlayerFacts_factsList__Xw_ID img, img[src*='cap']");
        if (capHitImageElement != null) capHitImage = capHitImageElement.attr("src");

        // Player profile image
        String imageUrl = "";
        Element imageElement = doc.selectFirst("figure img, .Player_profileImage img, img.player-image");
        if (imageElement == null) imageElement = doc.selectFirst("img[src*='/player/']");
        if (imageElement != null) imageUrl = imageElement.attr("src");

        // Relation parsing: try to find player relation container and extract ids
        String relation = "";
        Element relationDiv = doc.selectFirst("div.PlayerFacts_description__ujmxU, div.Relations");
        if (relationDiv != null) {
            StringBuilder relationBuilder = new StringBuilder();
            String htmlContent = relationDiv.html();
            String[] lines = htmlContent.split("<br\\s*/?>", -1);
            
            // Valid relationship types to extract (only family/blood relations, not profiles)
            java.util.Set<String> validRelations = new java.util.HashSet<>(java.util.Arrays.asList(
                // Direct family
                "Father", "Mother", "Brother", "Sister", "Son", "Daughter",
                "Brothers", "Sisters", "Sons", "Daughters", // plural forms
                
                // Grandparents
                "Grandfather", "Grandmother", "Grandparents",
                
                // Uncles/Aunts/Nephews/Nieces
                "Uncle", "Aunt", "Nephew", "Niece", 
                "Uncles", "Aunts", "Nephews", "Nieces", // plural forms
                
                // Cousins
                "Cousin", "Cousins", "Kusin", // Kusin is Swedish for cousin
                "Second Cousin", "Second Cousins", "Second cousin", "Second cousins",
                "Third Cousin", "Third Cousins", "Third cousin", "Third cousins",
                
                // Twins
                "Twin-brother", "Twin-sister", "Twin brother", "Twin sister",
                "Twin", "Twins",
                
                // Great relations
                "Great Uncle", "Great Aunt", "Great-Uncle", "Great-Aunt",
                "Great Grandfather", "Great Grandmother", "Great-Grandfather", "Great-Grandmother",
                
                // Step/Half family (common in blended families)
                "Stepfather", "Stepmother", "Stepbrother", "Stepsister",
                "Half-brother", "Half-sister", "Half brother", "Half sister",
                
                // In-laws (sometimes listed for spouse connections)
                "Father-in-law", "Mother-in-law", "Brother-in-law", "Sister-in-law",
                
                // Spouse/Partner
                "Husband", "Wife", "Spouse", "Partner"
            ));
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                // Only process lines that contain player links
                if (line.contains(": <a href=\"/player.php?player=") || line.contains(": <a href=\"/player/")) {
                    String cleanLine = line.replaceAll("<[^>]*>", "").trim();
                    if (cleanLine.contains(":")) {
                        String[] parts = cleanLine.split(":", 2);
                        String relationType = parts[0].trim();
                        
                        // Only include valid family relationships, exclude "Goalie profile", "Skater profile", etc.
                        if (validRelations.contains(relationType)) {
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/player(?:\\.php\\?player=|/)(\\d+)");
                            java.util.regex.Matcher matcher = pattern.matcher(line);
                            while (matcher.find()) {
                                String playerId2 = matcher.group(1);
                                if (relationBuilder.length() > 0) relationBuilder.append(" ; ");
                                relationBuilder.append(relationType).append(": ").append(playerId2);
                            }
                        }
                    }
                }
            }
            relation = relationBuilder.toString().replaceAll("\"", "").trim();
        }

        // Fetch skills
        Skill[] skillsArr = fetchSkills(playerId).toArray(new Skill[0]);

        // Status
        String status = extractValue.apply("Status", "");

        // Build PlayerProfile
        PlayerProfile profile = new PlayerProfile(
                name,
                dob,
                age,
                pob,
                nation,
                youth,
                latest_team_position,
                latest_team,
                season,
                position,
                height,
                weight,
                shoots,
                contract,
                playerType.toArray(new String[0]),
                capHit,
                capHitImage,
                nhlRights,
                drafted,
                highlights.toArray(new String[0]),
                agency,
                imageUrl,
                relation,
                skillsArr,
                status);

        // Debug: if many expected fields are empty, dump the #player-facts HTML to help diagnosis
        int emptyCount = 0;
        if (dob == null || dob.isEmpty()) emptyCount++;
        if (position == null || position.isEmpty()) emptyCount++;
        if (height == null || height.isEmpty()) emptyCount++;
        if (weight == null || weight.isEmpty()) emptyCount++;
        if (shoots == null || shoots.isEmpty()) emptyCount++;
        if (agency == null || agency.isEmpty()) emptyCount++;
        if (emptyCount >= 4) {
            Element factsContainer = doc.selectFirst("#player-facts");
            System.out.println("--- DEBUG: #player-facts HTML ---");
            if (factsContainer != null) {
                System.out.println(factsContainer.outerHtml());
            } else {
                System.out.println("(no #player-facts element found)");
            }
            System.out.println("--- END DEBUG ---");
        }

        return profile;
    }
}
