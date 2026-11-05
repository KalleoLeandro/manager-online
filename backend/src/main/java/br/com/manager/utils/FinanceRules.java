package br.com.manager.utils;

import br.com.manager.models.dto.FinanceDtos.Contract;
import br.com.manager.models.requests.TransferOfferRequest;

public final class FinanceRules {

    private FinanceRules() {}

    public record TrustReaction(int board, int fans, String description) {}

    public static int expectedObjective(int expectedRank, int teams) {
        return expectedRank == 1
            ? 1
            : expectedRank <= 4
              ? Math.min(4, teams)
              : expectedRank <= teams / 2
                ? Math.max(1, teams / 2)
                : Math.max(1, teams - 3);
    }

    public static TrustReaction promiseReaction(int promised, int expectedRank, int teams) {
        int baseline = expectedObjective(expectedRank, teams);
        if (promised > baseline) {
            int penalty = Math.clamp((int) Math.round(((promised - baseline) * 20.0) / Math.max(1, teams)), 3, 16);
            return new TrustReaction(
                -penalty,
                -Math.min(22, penalty + 4),
                "Meta abaixo do potencial do elenco. Diretoria e torcida esperam mais ambição."
            );
        }
        if (promised < baseline) {
            boolean excessive = baseline - promised > teams / 2;
            return new TrustReaction(
                excessive ? 0 : 2,
                excessive ? 2 : 4,
                excessive
                    ? "Promessa muito ousada para a força do elenco; a torcida se anima, mas a diretoria mantém cautela."
                    : "Meta ambiciosa para o elenco. A confiança inicial melhora, mas a cobrança por resultados aumenta."
            );
        }
        return new TrustReaction(0, 0, "Meta compatível com a força do elenco no campeonato.");
    }

    public static TrustReaction seasonReaction(int rank, int promised, int expectedRank, int teams) {
        int baseline = expectedObjective(expectedRank, teams);
        if (rank == 1) return new TrustReaction(12, 16, "Título nacional conquistado.");
        if (rank > promised) return new TrustReaction(
            promised == 1 ? -18 : -10,
            promised == 1 ? -22 : -14,
            "A promessa da temporada não foi cumprida."
        );
        if (rank > baseline + 1) {
            int penalty = Math.clamp((rank - baseline) * 2, 3, 16);
            return new TrustReaction(
                -penalty,
                -Math.min(22, penalty + 4),
                "A promessa foi cumprida, mas o resultado ficou abaixo do potencial do clube."
            );
        }
        return new TrustReaction(6, 8, "A promessa foi cumprida com desempenho compatível com o clube.");
    }

    public static long marketValue(int ability, Integer age) {
        double ageFactor =
            age == null ? 1 : age < 21 ? 1.5 : age < 24 ? 1.25 : age > 30 ? Math.max(.20, 1 - (age - 30) * .09) : 1;
        return Math.max(
            100000,
            Math.min(1500000000L, Math.round((4000000 * Math.exp((ability - 60) / 7.5) * ageFactor) / 10000) * 10000)
        );
    }

    /** Identify only estimates produced by the previous model; preserve imported/manual values. */
    public static long legacyMarketValue(int ability, Integer age) {
        double ageFactor = age == null ? 1 : age < 24 ? 1.2 : age > 30 ? Math.max(.35, 1 - (age - 30) * .09) : 1;
        return Math.max(
            50000,
            Math.min(500000000, Math.round((500000 * Math.exp((ability - 60) / 7.0) * ageFactor) / 1000) * 1000)
        );
    }

    public static long salary(long market) {
        return Math.max(5000, Math.min(4000000, Math.round((150000 * Math.pow(market / 10000000.0, .65)) / 100) * 100));
    }

    public static long commitment(long salary, long appearance, long goal, long title) {
        return salary + appearance * 4 + goal * 2 + (title + 11) / 12;
    }

    public static long commitment(Contract c) {
        return commitment(c.salaryMonthly(), c.appearanceBonus(), c.goalBonus(), c.titleBonus());
    }

    public static long commitment(TransferOfferRequest c) {
        return commitment(c.salaryMonthly(), c.appearanceBonus(), c.goalBonus(), c.titleBonus());
    }

    public static long annualRevenue(String name, String country, int division, int rating) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(java.util.Locale.ROOT);
        boolean brazil = country.equalsIgnoreCase("Brasil") || country.equalsIgnoreCase("Brazil");
        double revenue;
        if (brazil && division == 1 && normalized.equals("palmeiras")) revenue =
            1100000000L * Math.exp(Math.clamp((rating - 81) / 30.0, -.35, .35));
        else if (
            brazil && division == 1 && (normalized.equals("flamengo") || normalized.equals("cr flamengo"))
        ) revenue = 1550000000L * Math.exp(Math.clamp((rating - 84) / 30.0, -.35, .35));
        else revenue =
            600000000 *
            Math.exp((rating - 78) / 9.0) *
            (brazil ? 1 : 2.8) *
            (division >= 3 ? .25 : division == 2 ? .55 : 1);
        return Math.clamp(Math.round(revenue / 100000) * 100000, 3000000, 12000000000L);
    }

    public static long forecast(long annualRevenue, long monthlyWages) {
        return Math.max(3000000, Math.max(Math.round(annualRevenue * .65), monthlyWages * 15));
    }

    public static long ceiling(long base, int trust, boolean champion, Boolean promiseMet, int promised, int expected) {
        double factor =
            1 +
            (trust - 60) * .002 +
            (champion ? .15 : 0) +
            (promiseMet == null ? 0 : promiseMet ? .05 : -.15) +
            (promised < expected ? .08 : promised > expected ? -.04 : 0);
        return Math.round((base * Math.clamp(factor, .65, 1.40)) / 1000) * 1000;
    }

    public static int meter(double ratio) {
        return (int) Math.clamp(Math.round(ratio * 75), 0, 100);
    }

    public static long askingFee(Contract c, java.time.LocalDate date) {
        if (c.teamId() == null) return 0;
        long months = java.time.temporal.ChronoUnit.MONTHS.between(date, c.endDate());
        return Math.round(c.marketValue() * (months <= 6 ? .70 : months <= 12 ? .85 : 1));
    }

    public static long desiredSalary(Contract c, int buyerRating, int sellerRating) {
        return Math.max(
            5000,
            Math.round(
                Math.max(c.salaryMonthly(), salary(c.marketValue())) *
                    (1 + Math.clamp((sellerRating - buyerRating) * .012, -.12, .35))
            )
        );
    }
}
