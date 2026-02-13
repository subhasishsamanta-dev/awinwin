package com.brainium.schema;

/**
 * Represents a hockey player profile with detailed facts and attributes.
 */
public class PlayerProfile {
    // Basic info
    public String name;
    public String dateOfBirth;
    public String age;
    public String placeOfBirth;
    public String nation;
    public String youthTeam;
    public String latest_team_position;
    public String latest_team;
    public String season;
    public String position;
    public String height;
    public String weight;
    public String shoots;
    public String contract;
    /**
     * List of player types (e.g., "Heavy Shooter", "Power Forward", "PP
     * Specialist")
     */
    public String[] playerType;
    public String capHit;
    /**
     * Cap hit image URL
     */
    public String capHitImage;
    public String nhlRights;
    public String drafted;

    /**
     * List of player highlights (e.g., awards, achievements, notable facts)
     */
    public String[] highlights;

    /**
     * UserID and UserName from the URL
     */
    public String userId;
    public String userName;
    /**
     * Agency
     */
    public String agency;
    /**
     * Player profile image URL
     */
    public String imageUrl;

    public Skill[] skills;
    
    /**
     * Player relations (e.g., "Brother: 12345 | Son: 67890")
     */
    public String relation;
    
    /**
     * Player status (e.g., "Active", "Retired", etc.)
     */
    public String status;
    
    /**
     * Returns skills formatted for CSV: "Skill1 : URL1 ; Skill2 : URL2"
     */
    public String getSkillsFormatted() {
        if (skills == null || skills.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < skills.length; i++) {
            if (i > 0) {
                sb.append(" ; ");
            }
            sb.append(skills[i].toFormattedString());
        }
        return sb.toString();
    }
    
    /**
     * Default constructor
     */
    public PlayerProfile() {
    }

    /**
     * Full constructor for all fields
     */
    public PlayerProfile(String name, String dateOfBirth, String age, String placeOfBirth, String nation,
            String youthTeam, String latest_team_position, String latest_team, String season, String position, String height, String weight,
            String shoots, String contract, String[] playerType, String capHit, String capHitImage,
            String nhlRights, String drafted, String[] highlights, String agency, String imageUrl, String relation, Skill[] skills, String status) {
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.age = age;
        this.placeOfBirth = placeOfBirth;
        this.nation = nation;
        this.youthTeam = youthTeam;
        this.latest_team_position = latest_team_position;
        this.latest_team = latest_team;
        this.season = season;
        this.position = position;
        this.height = height;
        this.weight = weight;
        this.shoots = shoots;
        this.contract = contract;
        this.playerType = playerType;
        this.capHit = capHit;
        this.capHitImage = capHitImage;
        this.nhlRights = nhlRights;
        this.drafted = drafted;
        this.highlights = highlights;
        this.agency = agency;
        this.imageUrl = imageUrl;
        this.relation = relation;
        this.skills = skills;
        this.status = status;
    }

}
