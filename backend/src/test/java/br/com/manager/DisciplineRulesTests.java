package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.DisciplineRules;
import org.junit.jupiter.api.Test;

class DisciplineRulesTests {

    @Test
    void bookedPlayersBecomeMoreCarefulWithoutImmunityToAnotherBooking() {
        for (int aggression : new int[] { 1, 50, 99 })
            for (int control : new int[] { 1, 50, 99 }) {
                double first = DisciplineRules.yellowChance(aggression, control, control, false);
                double booked = DisciplineRules.yellowChance(aggression, control, control, true);
                assertThat(first).isBetween(.12, .28);
                assertThat(booked).isPositive().isLessThan(first);
                assertThat(DisciplineRules.directRedChance(aggression, control, control))
                    .isPositive()
                    .isLessThan(.005);
            }
    }

    @Test
    void aggressionAndPoorDecisionMakingIncreaseRiskWithoutUsingOverallAsDiscipline() {
        assertThat(DisciplineRules.yellowChance(95, 30, 30, true)).isGreaterThan(
            DisciplineRules.yellowChance(30, 95, 95, true)
        );
        assertThat(DisciplineRules.directRedChance(95, 30, 30)).isGreaterThan(
            DisciplineRules.directRedChance(30, 95, 95)
        );
    }
}
