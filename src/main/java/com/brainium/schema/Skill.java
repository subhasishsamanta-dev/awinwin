package com.brainium.schema;

import io.github.cdimascio.dotenv.Dotenv;

public class Skill {
    public String name;
    public String image;
    private Dotenv dotenv;

    public Skill(String name) {
        this.dotenv = Dotenv.load();
        this.name = name;
        this.image = this.createImageUrl(name);
    }

    private String createImageUrl(String skillName) {
        String formattedName = skillName.toLowerCase().replace(" ", "-");
        return this.dotenv.get("IMAGE_BASE_URL") + formattedName + ".svg";
    }

    /**
     * Returns the skill in the format "Skill Name : Icon URL"
     */
    public String toFormattedString() {
        return name + " : " + image;
    }
}
