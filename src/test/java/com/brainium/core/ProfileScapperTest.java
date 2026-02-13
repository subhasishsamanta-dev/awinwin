package com.brainium.core;

import org.junit.jupiter.api.Test;

import com.brainium.schema.PlayerProfile;
import com.brainium.schema.Skill;

public class ProfileScapperTest {

    @Test
    void testScrape() throws Exception {


        PlayerProfile profile = ProfileScapper
                .getProfile("https://www.eliteprospects.com/player/4230/alexander-ovechkin", "4230");
        // Assert that profile is not null (success)

        for(Skill skill : profile.skills) {
            System.out.println("Skill: " + skill.name);
            System.out.println("Image: " + skill.image);
        }
        assert profile != null;
    }

}
