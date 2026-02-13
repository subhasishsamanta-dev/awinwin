package com.brainium.data;

import java.util.List;
import com.brainium.schema.Country;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.file.*;

/**
 * LocationFactory is responsible for loading country data from a JSON file
 * and providing country-related utility methods.
 */
public class LocationFactory {
    // Gson instance for JSON parsing
    private final Gson gson = new Gson();

    // Path to the country JSON file, configurable via environment variable
    private static final String LOCATION_JSON = System.getenv("LOCATION_JSON") != null ? System.getenv("LOCATION_JSON") : "src/main/resources/data/country.json";

    // List of Country objects loaded from JSON file
    private final List<Country> countries = _getCountries();

    /**
     * Returns a list of country codes. This is currently hardcoded.
     * 
     * @return List of country codes (e.g., "US", "UK", "CA")
     */
    public List<String> getCountry() {
        return countries.stream().map(country -> country.code).toList();
    }

    /**
     * Loads the list of Country objects from the country.json file.
     * 
     * @return List of Country objects, or empty list if loading fails
     */
    private List<Country> _getCountries() {
        try {
            // Read the JSON file containing country data
            String jsonString = Files.readString(Path.of(LOCATION_JSON));
            // Define the type for deserialization
            Type listType = new TypeToken<List<Country>>() {
            }.getType();
            // Parse JSON into list of Country objects
            List<Country> locations = gson.fromJson(jsonString, listType);
            return locations;
        } catch (Exception e) {
            // Print stack trace and return empty list on error
            e.printStackTrace();
            return List.of();
        }
    }
}
