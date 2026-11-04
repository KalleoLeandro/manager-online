package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.models.enums.YouthTalent;
import br.com.manager.utils.YouthGeneration;
import java.util.Random;
import org.junit.jupiter.api.Test;

class YouthGenerationTests {

    @Test
    void generationCanHaveNoProspectsOneOrTwoAndRareExceptionalTalent() {
        var random = new Random(42);
        int empty = 0,
            notable = 0,
            exceptional = 0;
        for (int i = 0; i < 10000; i++) {
            var talents = YouthGeneration.talents(random);
            assertThat(talents).hasSize(18);
            long count = talents
                .stream()
                .filter(t -> t != YouthTalent.REGULAR)
                .count();
            assertThat(count).isBetween(0L, 2L);
            if (count == 0) empty++;
            else notable++;
            exceptional += talents
                .stream()
                .filter(t -> t == YouthTalent.EXCEPTIONAL)
                .count();
        }
        assertThat(empty).isBetween(5500, 6500);
        assertThat(notable).isBetween(3500, 4500);
        assertThat(exceptional).isBetween(300, 600);
    }

    @Test
    void prospectsStandOutWithoutStartingAtProfessionalAverageAndPotentialIsBounded() {
        for (var talent : YouthTalent.values())
            for (int age = 15; age <= 18; age++) for (var position : new String[] { "GOL", "DEF", "MEI", "ATA" }) {
                var profile = YouthGeneration.profile(talent, position, age, 75, 55, new Random(7));
                assertThat(profile.ability()).isBetween(1, 74);
                assertThat(profile.technical().potential()).isBetween(profile.ability(), 99);
                assertThat(profile.technical().attributes()).hasSize(40);
                assertThat(profile.technical().attributes().values()).allMatch(v -> v >= 1 && v <= 99);
                if (talent == YouthTalent.REGULAR) assertThat(profile.ability()).isLessThan(55);
                else assertThat(profile.ability()).isGreaterThan(54);
                if (talent == YouthTalent.EXCEPTIONAL) assertThat(
                    profile.technical().potential()
                ).isGreaterThanOrEqualTo(88);
            }
    }

    @Test
    void extremeClubRatingsNeverCreateInvalidAbilityOrPotential() {
        for (int rating : new int[] { 1, 5, 30, 95, 99 })
            for (var talent : YouthTalent.values()) {
                var profile = YouthGeneration.profile(talent, "MEI", 16, rating, rating, new Random(99));
                assertThat(profile.ability()).isBetween(1, 99);
                assertThat(profile.technical().potential()).isBetween(profile.ability(), 99);
            }
    }
}
