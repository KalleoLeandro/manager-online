package br.com.manager.utils;

import br.com.manager.models.dto.PlayerTechnicalDto;
import br.com.manager.models.enums.YouthTalent;
import java.util.*;

public final class YouthGeneration {

    private YouthGeneration() {}

    public record Profile(int ability, PlayerTechnicalDto technical) {}

    public static List<YouthTalent> talents(Random random) {
        int roll = random.nextInt(100),
            count = roll < 5 ? 2 : roll < 40 ? 1 : 0;
        var talents = new ArrayList<>(Collections.nCopies(18, YouthTalent.REGULAR));
        for (int i = 0; i < count; i++) talents.set(
            i,
            random.nextInt(100) < 10 ? YouthTalent.EXCEPTIONAL : YouthTalent.PROMISING
        );
        Collections.shuffle(talents, random);
        return List.copyOf(talents);
    }

    public static Profile profile(
        YouthTalent talent,
        String position,
        int age,
        double average,
        int lowest,
        Random random
    ) {
        int ceiling = Math.max(1, lowest - 1);
        int target = Math.min(Math.max(1, ceiling - 5), (int) Math.round(average * .65));
        int ability = switch (talent) {
            case REGULAR -> Math.clamp(target + random.nextInt(11) - 5, 1, ceiling);
            case PROMISING -> Math.clamp(
                (int) Math.round(average * .80) + random.nextInt(6),
                1,
                Math.max(1, (int) Math.floor(average) - 2)
            );
            case EXCEPTIONAL -> Math.clamp(
                (int) Math.round(average * .92) + random.nextInt(5),
                1,
                Math.max(1, (int) Math.floor(average) - 1)
            );
        };
        var estimated = EstimatedPlayerProfile.estimate(
            random.nextInt(1000000),
            position,
            ability,
            age,
            "",
            ability,
            "",
            ability
        );
        int potential = switch (talent) {
            case REGULAR -> estimated.potential();
            case PROMISING -> Math.clamp(
                Math.max(ability + 12, (int) Math.round(average) + 8 + random.nextInt(7)),
                ability,
                95
            );
            case EXCEPTIONAL -> Math.clamp(
                Math.max(ability + 18, (int) Math.round(average) + 18 + random.nextInt(8)),
                Math.max(ability, 88),
                99
            );
        };
        return new Profile(ability, new PlayerTechnicalDto(estimated.attributes(), potential, List.of(), null, null));
    }
}
