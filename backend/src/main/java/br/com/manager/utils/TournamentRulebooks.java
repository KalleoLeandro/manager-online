package br.com.manager.utils;

import java.time.LocalDate;
import java.util.*;

/** Published competition structures. Kept separate from participants, draws and simulation. */
public final class TournamentRulebooks {

    private TournamentRulebooks() {}

    public record Stage(
        String key,
        String label,
        int entrants,
        int newEntrants,
        int legs,
        boolean extraTime,
        boolean neutral,
        LocalDate firstDate,
        LocalDate secondDate,
        String drawRule,
        String format,
        int groups,
        List<LocalDate> matchDates
    ) {
        public Stage(
            String key,
            String label,
            int entrants,
            int newEntrants,
            int legs,
            boolean extraTime,
            boolean neutral,
            LocalDate firstDate,
            LocalDate secondDate,
            String drawRule
        ) {
            this(
                key,
                label,
                entrants,
                newEntrants,
                legs,
                extraTime,
                neutral,
                firstDate,
                secondDate,
                drawRule,
                "KNOCKOUT",
                0,
                secondDate == null ? List.of(firstDate) : List.of(firstDate, secondDate)
            );
        }

        public Stage {
            format = format == null ? "KNOCKOUT" : format;
            matchDates =
                matchDates == null
                    ? secondDate == null
                        ? List.of(firstDate)
                        : List.of(firstDate, secondDate)
                    : List.copyOf(matchDates);
        }
    }

    public record Rulebook(
        String key,
        String name,
        String country,
        int startYear,
        String version,
        String source,
        int participants,
        List<Stage> stages
    ) {}

    private static Stage stage(
        String key,
        String label,
        int entrants,
        int newEntrants,
        int legs,
        boolean extraTime,
        boolean neutral,
        int year,
        int month,
        int day,
        String drawRule
    ) {
        var date = LocalDate.of(year, month, day);
        return new Stage(
            key,
            label,
            entrants,
            newEntrants,
            legs,
            extraTime,
            neutral,
            date,
            legs == 2 ? date.plusDays(7) : null,
            drawRule
        );
    }

    private static Stage secondDate(Stage stage, LocalDate date) {
        return new Stage(
            stage.key(),
            stage.label(),
            stage.entrants(),
            stage.newEntrants(),
            stage.legs(),
            stage.extraTime(),
            stage.neutral(),
            stage.firstDate(),
            date,
            stage.drawRule()
        );
    }

    public static Rulebook copaBrasil(int year) {
        int regional = year >= 2027 ? 6 : 4;
        // The 2026 REC explicitly changes the third-round entry pool from four to six in 2027.
        // Later opening round counts must be published/confirmed, rather than inferred as a 64-team cup.
        if (year != 2026) {
            var reference = copaBrasil(2026);
            return new Rulebook(
                reference.key(),
                reference.name(),
                reference.country(),
                year,
                "CBF-2026-FORMAT-PROJECTED",
                reference.source(),
                reference.participants(),
                reference
                    .stages()
                    .stream()
                    .map(s -> shift(s, year - 2026))
                    .toList()
            );
        }
        return new Rulebook(
            "BRA_CUP",
            "Copa do Brasil",
            "Brasil",
            year,
            "CBF-2026-REC",
            "https://stcbfsiteprdimgbrs.blob.core.windows.net/img-site/cdn/REC_Copa_do_Brasil_2026_66989a5426.pdf",
            122 + regional,
            List.of(
                stage("R1", "Primeira fase", 28, 28, 1, false, false, year, 2, 18, "RNC_COMPLEMENTARY_PAIRS"),
                stage("R2", "Segunda fase", 88, 74, 1, false, false, year, 2, 25, "DRAW_FIXED_BRACKET_TO_R4"),
                stage("R3", "Terceira fase", 48, 4, 1, false, false, year, 3, 11, "PREVIOUS_BRACKET"),
                stage("R4", "Quarta fase", 24, 0, 1, false, false, year, 3, 18, "PREVIOUS_BRACKET"),
                secondDate(
                    stage("R32", "Quinta fase", 32, 20, 2, false, false, year, 4, 22, "RNC_TWO_POTS"),
                    LocalDate.of(year, 5, 13)
                ),
                secondDate(
                    stage("R16", "Oitavas de final", 16, 0, 2, false, false, year, 8, 1, "OPEN_DRAW"),
                    LocalDate.of(year, 8, 5)
                ),
                stage("QF", "Quartas de final", 8, 0, 2, false, false, year, 8, 26, "DRAW_FIXED_BRACKET_TO_FINAL"),
                stage("SF", "Semifinais", 4, 0, 2, false, false, year, 11, 1, "PREVIOUS_BRACKET"),
                stage("FINAL", "Final", 2, 0, 1, false, true, year, 12, 6, "PREVIOUS_BRACKET")
            )
        );
    }

    public static Rulebook copaRey(int year) {
        return new Rulebook(
            "ESP_CUP",
            "Copa del Rey",
            "Espanha",
            year,
            "RFEF-2026-27",
            "https://rfef.es/es/noticias/conocida-la-distribucion-de-los-grupos-para-el-sorteo-de-la-primera-eliminatoria",
            126,
            List.of(
                stage("PRELIM", "Eliminatória territorial", 20, 20, 2, true, false, year, 9, 26, "GEOGRAPHIC"),
                stage(
                    "R1",
                    "Primeira eliminatória",
                    112,
                    102,
                    1,
                    true,
                    false,
                    year,
                    10,
                    28,
                    "REGIONAL_WINNERS_LOWER_HOSTS"
                ),
                stage("R2", "Segunda eliminatória", 56, 0, 1, true, false, year, 12, 2, "LOWER_HOSTS"),
                stage(
                    "R32",
                    "Dezesseis-avos de final",
                    32,
                    4,
                    1,
                    true,
                    false,
                    year + 1,
                    1,
                    6,
                    "SUPERCUP_ENTRANTS_LOWER_HOSTS"
                ),
                stage("R16", "Oitavas de final", 16, 0, 1, true, false, year + 1, 1, 13, "LOWER_HOSTS"),
                stage("QF", "Quartas de final", 8, 0, 1, true, false, year + 1, 2, 3, "OPEN_DRAW"),
                stage("SF", "Semifinais", 4, 0, 2, true, false, year + 1, 2, 10, "OPEN_DRAW"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 4, 24, "PREVIOUS_BRACKET")
            )
        );
    }

    public static Rulebook coppaItalia(int year) {
        return new Rulebook(
            "ITA_CUP",
            "Coppa Italia",
            "Itália",
            year,
            "LEGA-2024-2027",
            "https://img.legaseriea.it/vimages/66b3723c/253%20-%20Regolamento%20Coppa%20Italia%20Frecciarossa%202024-2027%20%281%29.pdf",
            44,
            List.of(
                stage("PRELIM", "Turno preliminar", 8, 8, 1, false, false, year, 8, 8, "SPORTING_RANK_BRACKET"),
                stage("R32", "Primeira eliminatória", 32, 28, 1, false, false, year, 8, 15, "PREVIOUS_BRACKET"),
                stage("R16Q", "Segunda eliminatória", 16, 0, 1, false, false, year, 9, 23, "PREVIOUS_BRACKET"),
                stage("R16", "Oitavas de final", 16, 8, 1, false, false, year, 12, 2, "SPORTING_RANK_BRACKET"),
                stage("QF", "Quartas de final", 8, 0, 1, false, false, year + 1, 2, 3, "PREVIOUS_BRACKET"),
                stage("SF", "Semifinais", 4, 0, 2, true, false, year + 1, 3, 3, "PREVIOUS_BRACKET"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 5, 19, "PREVIOUS_BRACKET")
            )
        );
    }

    public static Rulebook dfbPokal(int year) {
        return new Rulebook(
            "GER_CUP",
            "DFB-Pokal",
            "Alemanha",
            year,
            "DFB-2026-27",
            "https://www.dfb.de/maenner/wettbewerbe/dfb-pokal/modus",
            64,
            List.of(
                stage("R64", "Primeira fase", 64, 64, 1, true, false, year, 8, 21, "PRO_AMATEUR_POTS_AMATEUR_HOSTS"),
                stage("R32", "Segunda fase", 32, 0, 1, true, false, year, 10, 27, "PRO_AMATEUR_POTS_AMATEUR_HOSTS"),
                stage("R16", "Oitavas de final", 16, 0, 1, true, false, year, 12, 1, "AMATEUR_HOSTS"),
                stage("QF", "Quartas de final", 8, 0, 1, true, false, year + 1, 2, 2, "AMATEUR_HOSTS"),
                stage("SF", "Semifinais", 4, 0, 1, true, false, year + 1, 4, 20, "OPEN_DRAW"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 5, 22, "PREVIOUS_BRACKET")
            )
        );
    }

    public static void validate(Rulebook rules) {
        if (rules.key().equals("INTERCONTINENTAL")) {
            if (
                rules.participants() != 6 ||
                rules.stages().size() != 5 ||
                !rules
                    .stages()
                    .stream()
                    .map(Stage::newEntrants)
                    .toList()
                    .equals(List.of(2, 1, 2, 0, 1)) ||
                rules
                    .stages()
                    .stream()
                    .anyMatch(s -> s.entrants() != 2 || s.legs() != 1)
            ) throw new IllegalArgumentException("Chave da Copa Intercontinental inválida.");
            return;
        }
        int previousWinners = 0,
            newTotal = 0;
        int bye = 0;
        var keys = new HashSet<String>();
        for (var stage : rules.stages()) {
            if (stage.format().equals("UEFA_LEAGUE")) {
                if (
                    stage.entrants() != 36 || stage.newEntrants() != 36 || stage.matchDates().size() != 8
                ) throw new IllegalArgumentException("Fase de liga inválida.");
                previousWinners = 16;
                bye = 8;
                newTotal += stage.newEntrants();
                continue;
            }
            if (stage.format().equals("GROUPS")) {
                if (
                    stage.entrants() != previousWinners + stage.newEntrants() ||
                    stage.groups() < 1 ||
                    stage.entrants() % stage.groups() != 0
                ) throw new IllegalArgumentException("Fase de grupos inválida.");
                previousWinners = stage.groups() * 2;
                newTotal += stage.newEntrants();
                continue;
            }
            int direct = stage.key().equals("R16") ? bye : 0;
            if (direct > 0) bye = 0;
            if (
                !keys.add(stage.key()) ||
                stage.entrants() % 2 != 0 ||
                stage.entrants() != previousWinners + stage.newEntrants() + direct ||
                stage.legs() < 1 ||
                stage.legs() > 2
            ) throw new IllegalArgumentException("Estrutura de competição inválida: " + stage.key());
            previousWinners = stage.entrants() / 2;
            newTotal += stage.newEntrants();
        }
        if (previousWinners != 1 || newTotal != rules.participants()) throw new IllegalArgumentException(
            "Participantes ou final inconsistentes."
        );
    }

    public static Rulebook championsLeague(int year) {
        var dates = List.of(
            LocalDate.of(year, 9, 8),
            LocalDate.of(year, 10, 13),
            LocalDate.of(year, 10, 20),
            LocalDate.of(year, 11, 3),
            LocalDate.of(year, 11, 24),
            LocalDate.of(year, 12, 8),
            LocalDate.of(year + 1, 1, 19),
            LocalDate.of(year + 1, 1, 27)
        );
        return new Rulebook(
            "CHAMPIONS",
            "UEFA Champions League",
            "Europa",
            year,
            "UEFA-2026-27-LEAGUE-PHASE",
            "https://documents.uefa.com/r/Regulations-of-the-UEFA-Champions-League-2026/27-Online",
            36,
            List.of(
                new Stage(
                    "LEAGUE",
                    "Fase de liga",
                    36,
                    36,
                    1,
                    false,
                    false,
                    dates.getFirst(),
                    dates.getLast(),
                    "UEFA_FOUR_POTS",
                    "UEFA_LEAGUE",
                    0,
                    dates
                ),
                stage("PLAYOFF", "Play-off eliminatório", 16, 0, 2, true, false, year + 1, 2, 16, "UEFA_PLAYOFF"),
                stage("R16", "Oitavas de final", 16, 0, 2, true, false, year + 1, 3, 9, "UEFA_R16"),
                stage("QF", "Quartas de final", 8, 0, 2, true, false, year + 1, 4, 6, "UEFA_FIXED_BRACKET"),
                stage("SF", "Semifinais", 4, 0, 2, true, false, year + 1, 4, 27, "UEFA_FIXED_BRACKET"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 6, 5, "UEFA_FIXED_BRACKET")
            )
        );
    }

    public static Rulebook libertadores(int year) {
        var dates = List.of(
            LocalDate.of(year, 4, 7),
            LocalDate.of(year, 4, 14),
            LocalDate.of(year, 4, 28),
            LocalDate.of(year, 5, 5),
            LocalDate.of(year, 5, 19),
            LocalDate.of(year, 5, 26)
        );
        return new Rulebook(
            "LIBERTADORES",
            "CONMEBOL Libertadores",
            "América do Sul",
            year,
            "CONMEBOL-2026",
            "https://cdn.conmebol.com/wp-content/uploads/2025/12/CL-2026-Manual-de-Clubes-ESP-Feb26.pdf",
            47,
            List.of(
                stage("PRE1", "Primeira fase", 6, 6, 2, false, false, year, 2, 3, "CONMEBOL_PRELIM_POTS"),
                stage("PRE2", "Segunda fase", 16, 13, 2, false, false, year, 2, 17, "CONMEBOL_PRELIM_POTS"),
                stage("PRE3", "Terceira fase", 8, 0, 2, false, false, year, 3, 3, "PREVIOUS_BRACKET"),
                new Stage(
                    "GROUPS",
                    "Fase de grupos",
                    32,
                    28,
                    1,
                    false,
                    false,
                    dates.getFirst(),
                    dates.getLast(),
                    "CONMEBOL_FOUR_POTS",
                    "GROUPS",
                    8,
                    dates
                ),
                stage("R16", "Oitavas de final", 16, 0, 2, false, false, year, 8, 11, "GROUP_WINNERS_V_RUNNERS"),
                stage("QF", "Quartas de final", 8, 0, 2, false, false, year, 9, 8, "PREVIOUS_BRACKET"),
                stage("SF", "Semifinais", 4, 0, 2, false, false, year, 10, 13, "PREVIOUS_BRACKET"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year, 11, 28, "PREVIOUS_BRACKET")
            )
        );
    }

    private static Stage shift(Stage s, int years) {
        return new Stage(
            s.key(),
            s.label(),
            s.entrants(),
            s.newEntrants(),
            s.legs(),
            s.extraTime(),
            s.neutral(),
            s.firstDate().plusYears(years),
            s.secondDate() == null ? null : s.secondDate().plusYears(years),
            s.drawRule(),
            s.format(),
            s.groups(),
            s
                .matchDates()
                .stream()
                .map(d -> d.plusYears(years))
                .toList()
        );
    }

    public static Rulebook faCup(int year) {
        return new Rulebook(
            "ENG_CUP",
            "FA Cup",
            "Inglaterra",
            year,
            "FA-2026-27-PROPER-ROUNDS",
            "https://www.thefa.com/competitions/thefacup/round-dates",
            124,
            List.of(
                stage("R1", "Primeira fase", 80, 80, 1, true, false, year, 11, 7, "OPEN_DRAW"),
                stage("R2", "Segunda fase", 40, 0, 1, true, false, year, 12, 5, "OPEN_DRAW"),
                stage("R64", "Terceira fase", 64, 44, 1, true, false, year + 1, 1, 9, "OPEN_DRAW"),
                stage("R32", "Quarta fase", 32, 0, 1, true, false, year + 1, 2, 6, "OPEN_DRAW"),
                stage("R16", "Oitavas de final", 16, 0, 1, true, false, year + 1, 3, 6, "OPEN_DRAW"),
                stage("QF", "Quartas de final", 8, 0, 1, true, false, year + 1, 4, 3, "OPEN_DRAW"),
                stage("SF", "Semifinais", 4, 0, 1, true, true, year + 1, 4, 24, "OPEN_DRAW"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 5, 22, "PREVIOUS_BRACKET")
            )
        );
    }

    public static Rulebook coupeFrance(int year) {
        return new Rulebook(
            "FRA_CUP",
            "Coupe de France",
            "França",
            year,
            "FFF-2026-27-FEDERAL-ROUNDS",
            "https://media.fff.fr/uploads/files/d48416da392dbe756680d72a42723538.pdf",
            198,
            List.of(
                stage("R7", "Sétima fase", 176, 176, 1, false, false, year, 11, 14, "GEOGRAPHIC_LOWER_HOSTS"),
                stage("R8", "Oitava fase", 92, 4, 1, false, false, year, 11, 28, "OVERSEAS_ENTRANTS_LOWER_HOSTS"),
                stage("R64", "Trinta e dois avos de final", 64, 18, 1, false, false, year, 12, 19, "LOWER_HOSTS"),
                stage("R32", "Dezesseis avos de final", 32, 0, 1, false, false, year + 1, 1, 9, "OPEN_DRAW"),
                stage("R16", "Oitavas de final", 16, 0, 1, false, false, year + 1, 2, 3, "OPEN_DRAW"),
                stage("QF", "Quartas de final", 8, 0, 1, false, false, year + 1, 3, 3, "OPEN_DRAW"),
                stage("SF", "Semifinais", 4, 0, 1, false, false, year + 1, 4, 20, "OPEN_DRAW"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year + 1, 5, 29, "PREVIOUS_BRACKET")
            )
        );
    }

    public static Rulebook intercontinental(int year) {
        return new Rulebook(
            "INTERCONTINENTAL",
            "Copa Intercontinental da FIFA",
            "Mundo",
            year,
            "FIFA-2026",
            "https://www.fifa.com/en/tournaments/mens/intercontinentalcup/2026/articles/teams-dates-venues-history",
            6,
            List.of(
                stage(
                    "PLAYOFF",
                    "Play-off África–Ásia–Pacífico",
                    2,
                    2,
                    1,
                    true,
                    false,
                    year,
                    8,
                    26,
                    "INTERCONTINENTAL_PATH"
                ),
                stage(
                    "AFRICA_ASIA_PACIFIC",
                    "Copa África–Ásia–Pacífico",
                    2,
                    1,
                    1,
                    true,
                    false,
                    year,
                    9,
                    19,
                    "INTERCONTINENTAL_PATH"
                ),
                stage("AMERICAS", "Derby das Américas", 2, 2, 1, true, true, year, 12, 9, "INTERCONTINENTAL_PATH"),
                stage("CHALLENGER", "Copa Challenger", 2, 0, 1, true, true, year, 12, 12, "INTERCONTINENTAL_PATH"),
                stage("FINAL", "Final", 2, 1, 1, true, true, year, 12, 16, "INTERCONTINENTAL_PATH")
            )
        );
    }

    public static Rulebook clubWorldCup(int year) {
        if ((year - 2025) % 4 != 0) throw new IllegalArgumentException(
            "O Mundial de Clubes ocorre a cada quatro anos."
        );
        var dates = List.of(LocalDate.of(year, 6, 15), LocalDate.of(year, 6, 20), LocalDate.of(year, 6, 25));
        return new Rulebook(
            "CLUB_WORLD_CUP",
            "Mundial de Clubes da FIFA",
            "Mundo",
            year,
            "FIFA-2025-FORMAT-PROJECTED-DATES",
            "https://www.fifa.com/en/tournaments/mens/club-world-cup/usa-2025/articles/teams-dates-venue-groups-draw-matches-tickets",
            32,
            List.of(
                new Stage(
                    "GROUPS",
                    "Fase de grupos",
                    32,
                    32,
                    1,
                    false,
                    true,
                    dates.getFirst(),
                    dates.getLast(),
                    "FIFA_FOUR_POTS",
                    "GROUPS",
                    8,
                    dates
                ),
                stage("R16", "Oitavas de final", 16, 0, 1, true, true, year, 6, 29, "FIFA_GROUP_BRACKET"),
                stage("QF", "Quartas de final", 8, 0, 1, true, true, year, 7, 5, "PREVIOUS_BRACKET"),
                stage("SF", "Semifinais", 4, 0, 1, true, true, year, 7, 9, "PREVIOUS_BRACKET"),
                stage("FINAL", "Final", 2, 0, 1, true, true, year, 7, 13, "PREVIOUS_BRACKET")
            )
        );
    }
}
