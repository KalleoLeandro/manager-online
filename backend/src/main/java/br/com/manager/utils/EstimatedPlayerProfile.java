package br.com.manager.utils;

import br.com.manager.models.dto.PlayerTechnicalDto;
import br.com.manager.models.enums.PlayerAttribute;
import java.util.*;

public final class EstimatedPlayerProfile {

    private EstimatedPlayerProfile() {}

    public static PlayerTechnicalDto estimate(
        long id,
        String position,
        int ability,
        Integer age,
        String first,
        int value1,
        String second,
        int value2
    ) {
        var values = new LinkedHashMap<String, Integer>();
        for (var attribute : PlayerAttribute.values()) {
            int value = Math.clamp(
                ability + attribute.offset(position) + (int) Math.floorMod(id * 7 + attribute.ordinal() * 17, 11) - 5,
                1,
                99
            );
            if (attribute == PlayerAttribute.specialty(first)) value = value1;
            else if (attribute == PlayerAttribute.specialty(second)) value = value2;
            values.put(attribute.key(), value);
        }
        int growth = age == null ? 8 : age < 21 ? 14 : age < 25 ? 9 : age < 29 ? 4 : 1;
        return new PlayerTechnicalDto(
            Collections.unmodifiableMap(values),
            Math.min(99, ability + growth),
            List.of(),
            null,
            null
        );
    }

    public static int specialty(PlayerTechnicalDto profile, String label, int fallback) {
        var attribute = PlayerAttribute.specialty(label);
        return attribute == null ? fallback : profile.attributes().getOrDefault(attribute.key(), fallback);
    }
}
