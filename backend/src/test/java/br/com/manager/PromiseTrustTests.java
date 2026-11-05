package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.FinanceRules;
import org.junit.jupiter.api.Test;

class PromiseTrustTests {

    @Test
    void theSameSurvivalPromiseIsDisappointingForFavoritesButReasonableForWeakTeams() {
        var favorite = FinanceRules.promiseReaction(17, 1, 20);
        var weak = FinanceRules.promiseReaction(17, 19, 20);
        assertThat(favorite.board()).isNegative();
        assertThat(favorite.fans()).isLessThan(favorite.board());
        assertThat(weak.board()).isZero();
        assertThat(weak.fans()).isZero();
        assertThat(FinanceRules.promiseReaction(4, 3, 20).board()).isZero();
    }

    @Test
    void ambitionDoesNotGiveUnlimitedConfidenceAndResultsStillMatter() {
        assertThat(FinanceRules.promiseReaction(1, 9, 20).board()).isPositive();
        assertThat(FinanceRules.promiseReaction(1, 19, 20).board()).isZero();
        assertThat(FinanceRules.seasonReaction(17, 17, 1, 20).fans()).isNegative();
        assertThat(FinanceRules.seasonReaction(17, 17, 19, 20).fans()).isPositive();
        assertThat(FinanceRules.seasonReaction(1, 17, 1, 20).fans()).isPositive();
        assertThat(FinanceRules.seasonReaction(5, 1, 1, 20).board()).isNegative();
    }
}
