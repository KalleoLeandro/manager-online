package br.com.manager;

import static org.assertj.core.api.Assertions.*;

import br.com.manager.utils.FinanceRules;
import org.junit.jupiter.api.Test;

class FinancialScaleTests {

    @Test
    void leadingBrazilianClubsHaveBillionScaleRevenueButOnlyPartFundsFootball() {
        long palmeiras = FinanceRules.annualRevenue("Palmeiras", "Brasil", 1, 81);
        long flamengo = FinanceRules.annualRevenue("Flamengo", "Brasil", 1, 84);
        assertThat(palmeiras).isEqualTo(1100000000L);
        assertThat(flamengo).isEqualTo(1550000000L);
        assertThat(FinanceRules.forecast(palmeiras, 20000000)).isBetween(500000000L, 900000000L);
        assertThat(FinanceRules.annualRevenue("Menor clube", "Brasil", 3, 55)).isLessThan(50000000);
    }

    @Test
    void notablePlayersCanCostHundredsOfMillionsWithoutUnlimitedSalaries() {
        assertThat(FinanceRules.marketValue(85, 20)).isBetween(100000000L, 300000000L);
        assertThat(FinanceRules.marketValue(95, 24)).isBetween(300000000L, 1000000000L);
        assertThat(FinanceRules.marketValue(70, 24)).isLessThan(FinanceRules.marketValue(85, 24));
        assertThat(FinanceRules.marketValue(85, 34)).isLessThan(FinanceRules.marketValue(85, 20));
        assertThat(FinanceRules.marketValue(50, 24)).isLessThan(2000000);
        assertThat(FinanceRules.salary(FinanceRules.marketValue(99, 18))).isLessThanOrEqualTo(4000000);
    }
}
