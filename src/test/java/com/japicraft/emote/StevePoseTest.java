package com.japicraft.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StevePoseTest {
    @Test
    void standingExplicitlyResetsBothSeatedLegs() throws Exception {
        try (var stream = getClass().getResourceAsStream("/bettermodel/players/steve.bbmodel")) {
            assertNotNull(stream);
            var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject standing = null;
            for (var animation : model.getAsJsonArray("animations")) {
                var object = animation.getAsJsonObject();
                if ("standing".equals(object.get("name").getAsString())) standing = object;
            }
            assertNotNull(standing);
            for (String leg : List.of("prl_right_leg", "pll_left_leg")) {
                JsonObject animator = null;
                for (var entry : standing.getAsJsonObject("animators").entrySet()) {
                    var object = entry.getValue().getAsJsonObject();
                    if (leg.equals(object.get("name").getAsString())) animator = object;
                }
                assertNotNull(animator, leg);
                assertTrue(animator.has("keyframes"), leg + " must actively reset its seated rotation");
                boolean neutralAtStart = false;
                for (var keyframe : animator.getAsJsonArray("keyframes")) {
                    var frame = keyframe.getAsJsonObject();
                    if (!"rotation".equals(frame.get("channel").getAsString())) continue;
                    for (var point : frame.getAsJsonArray("data_points")) {
                        for (String axis : List.of("x", "y", "z")) {
                            assertEquals(0.0, point.getAsJsonObject().get(axis).getAsDouble(), leg + " " + axis);
                        }
                    }
                    if (frame.get("time").getAsDouble() == 0) neutralAtStart = true;
                }
                assertTrue(neutralAtStart, leg + " must reset at the start of standing");
            }
        }
    }
}
