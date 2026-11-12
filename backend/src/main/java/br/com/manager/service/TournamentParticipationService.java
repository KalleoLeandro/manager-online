package br.com.manager.service;

import br.com.manager.models.dto.TournamentDtos.*;
import br.com.manager.repository.*;
import br.com.manager.utils.TournamentRulebooks.Rulebook;
import java.util.*;
import org.springframework.stereotype.Service;

/** Entirely offline: imported entry lists first, previous in-game results in later seasons. */
@Service
public class TournamentParticipationService {

    private final TournamentParticipationRepository repository;
    private final TournamentRepository tournaments;

    public TournamentParticipationService(
        TournamentParticipationRepository repository,
        TournamentRepository tournaments
    ) {
        this.repository = repository;
        this.tournaments = tournaments;
    }

    public static String confederation(String country) {
        return switch (country) {
            case
                "Brasil",
                "Argentina",
                "Bolívia",
                "Chile",
                "Colômbia",
                "Equador",
                "Paraguai",
                "Peru",
                "Uruguai",
                "Venezuela" -> "CONMEBOL";
            case
                "Estados Unidos",
                "México",
                "Canadá",
                "Costa Rica",
                "Honduras",
                "Panamá",
                "Jamaica",
                "Haiti" -> "CONCACAF";
            case
                "Egito",
                "Marrocos",
                "Tunísia",
                "África do Sul",
                "Argélia",
                "Angola",
                "Nigéria",
                "Congo",
                "Tanzânia",
                "Sudão" -> "CAF";
            case "Nova Zelândia", "Fiji", "Taiti", "Ilhas Salomão", "Vanuatu", "Papua-Nova Guiné" -> "OFC";
            case
                "Arábia Saudita",
                "Japão",
                "Coreia do Sul",
                "Emirados Árabes Unidos",
                "Catar",
                "Irã",
                "Austrália",
                "China",
                "Tailândia",
                "Uzbequistão",
                "Malásia",
                "Índia" -> "AFC";
            default -> "UEFA";
        };
    }

    private Entrant stage(Entrant t, String stage, int rank) {
        return new Entrant(
            t.id(),
            t.name(),
            stage,
            rank,
            t.association(),
            t.region() == null ? confederation(t.association()) : t.region(),
            t.division(),
            t.rating()
        );
    }

    public void historical(String id) {
        for (var reference : repository.references(id)) {
            int target = 2025 + 4 * Math.floorDiv(reference.year() - 2025, 4) + 4;
            tournaments.qualify(
                id,
                "CLUB_WORLD_CUP",
                target,
                reference.team(),
                reference.key(),
                reference.year(),
                "HISTORICAL_CONTINENTAL_CHAMPION",
                java.time.LocalDate.of(reference.year(), 12, 31)
            );
        }
    }

    public List<Entrant> entries(String id, Rulebook rule) {
        var reference = regionalAccess(id, rule, tournaments.seeds(id, rule.key(), rule.startYear()));
        if (reference.size() == rule.participants()) return reference
            .stream()
            .map(t -> stage(t, t.stage(), t.rank()))
            .toList();
        var clubs = new ArrayList<>(repository.clubs(id));
        var ranking = repository.ranking(id, rule.startYear() - 1);
        clubs.sort(
            Comparator.comparingInt((Entrant t) -> t.division())
                .thenComparing(Comparator.comparingInt((Entrant t) -> ranking.getOrDefault(t.id(), -1)).reversed())
                .thenComparing(Comparator.comparingInt(Entrant::rating).reversed())
                .thenComparingLong(Entrant::id)
        );
        if (rule.key().equals("INTERCONTINENTAL")) return intercontinental(id, rule, clubs);
        if (rule.key().equals("CLUB_WORLD_CUP")) return worldCup(id, rule, clubs);
        if (rule.key().equals("CHAMPIONS")) return champions(id, rule, clubs);
        if (rule.key().equals("LIBERTADORES")) return libertadores(id, rule, clubs);
        // Never fabricate an official domestic entry list. Reuse registered access pools in subsequent years.
        var template = regionalAccess(id, rule, tournaments.latestSeeds(id, rule.key(), rule.startYear()));
        if (template.size() != rule.participants()) return List.of();
        var direct = new HashSet<Long>();
        var result = new ArrayList<Entrant>();
        for (var s : rule.stages().reversed()) {
            int count = s.newEntrants();
            if (count == 0) continue;
            var original = template
                .stream()
                .filter(t -> t.stage().equals(s.key()))
                .toList();
            var pool = clubs
                .stream()
                .filter(t -> t.association().equals(rule.country()) && !direct.contains(t.id()))
                .toList();
            var selected =
                s.key().equals("R32") && rule.key().equals("BRA_CUP")
                    ? pool
                          .stream()
                          .filter(t -> t.division() == 1)
                          .limit(count)
                          .toList()
                    : original
                          .stream()
                          .filter(t -> !direct.contains(t.id()))
                          .limit(count)
                          .toList();
            for (var t : selected) {
                direct.add(t.id());
                result.add(stage(t, s.key(), result.size() + 1));
            }
        }
        return result.size() == rule.participants() ? result : List.of();
    }

    private List<Entrant> regionalAccess(String id, Rulebook rule, List<Entrant> entries) {
        if (!rule.key().equals("FRA_CUP")) return entries;
        var result = new ArrayList<>(
            entries
                .stream()
                .filter(t -> !t.stage().equals("REGIONAL"))
                .toList()
        );
        var regions = entries
            .stream()
            .filter(t -> t.stage().equals("REGIONAL"))
            .map(Entrant::region)
            .distinct()
            .toList();
        for (String region : regions) {
            var pair = entries
                .stream()
                .filter(t -> t.stage().equals("REGIONAL") && Objects.equals(t.region(), region))
                .toList();
            if (pair.size() != 2) return List.of();
            var score = br.com.manager.utils.BackgroundMatchRules.play(
                id,
                ((long) rule.startYear() << 32) ^ region.hashCode(),
                pair.get(0).rating(),
                pair.get(1).rating()
            );
            int winner =
                score.home() == score.away()
                    ? new Random(id.hashCode() ^ rule.startYear() ^ region.hashCode()).nextInt(2)
                    : score.home() > score.away()
                      ? 0
                      : 1;
            result.add(stage(pair.get(winner), "R8", pair.get(winner).rank()));
        }
        return result;
    }

    private List<Entrant> champions(String id, Rulebook rule, List<Entrant> clubs) {
        var quotas = new LinkedHashMap<String, Integer>();
        quotas.put("Inglaterra", 5);
        quotas.put("Espanha", 5);
        quotas.put("Itália", 4);
        quotas.put("Alemanha", 4);
        quotas.put("França", 3);
        quotas.put("Países Baixos", 2);
        quotas.put("Portugal", 2);
        quotas.put("Bélgica", 1);
        quotas.put("Turquia", 1);
        quotas.put("Escócia", 1);
        quotas.put("Áustria", 1);
        quotas.put("Noruega", 1);
        quotas.put("Grécia", 1);
        quotas.put("República Tcheca", 1);
        quotas.put("Ucrânia", 1);
        quotas.put("Eslováquia", 1);
        quotas.put("Azerbaijão", 1);
        var chosen = new LinkedHashMap<Long, Entrant>();
        for (long team : repository.qualified(id, "CHAMPIONS", rule.startYear()))
            clubs
                .stream()
                .filter(t -> t.id() == team)
                .findFirst()
                .ifPresent(t -> chosen.put(t.id(), t));
        quotas.forEach((country, number) ->
            clubs
                .stream()
                .filter(t -> t.association().equals(country))
                .limit(number)
                .forEach(t -> chosen.putIfAbsent(t.id(), t))
        );
        clubs
            .stream()
            .filter(t -> confederation(t.association()).equals("UEFA"))
            .forEach(t -> {
                if (
                    chosen
                        .values()
                        .stream()
                        .filter(x -> x.association().equals(t.association()))
                        .count() < 5
                ) chosen.putIfAbsent(t.id(), t);
            });
        return chosen
            .values()
            .stream()
            .limit(36)
            .map(t -> stage(t, "LEAGUE", new ArrayList<>(chosen.keySet()).indexOf(t.id()) + 1))
            .toList();
    }

    private List<Entrant> libertadores(String id, Rulebook rule, List<Entrant> clubs) {
        var allocations = new LinkedHashMap<String, int[]>();
        allocations.put("Brasil", new int[] { 0, 2, 6 });
        allocations.put("Argentina", new int[] { 0, 1, 6 });
        for (String c : List.of("Bolívia", "Chile", "Colômbia", "Equador", "Paraguai", "Peru", "Uruguai", "Venezuela"))
            allocations.put(c, new int[] {
                c.equals("Chile") || c.equals("Colômbia") ? 0 : 1,
                c.equals("Chile") || c.equals("Colômbia") ? 2 : 1,
                2,
            });
        var qualified = repository.qualified(id, "LIBERTADORES", rule.startYear());
        var result = new ArrayList<Entrant>();
        allocations.forEach((country, q) -> {
            var pool = new ArrayList<>(
                clubs
                    .stream()
                    .filter(t -> t.association().equals(country))
                    .toList()
            );
            pool.sort(
                Comparator.comparing((Entrant t) -> !qualified.contains(t.id())).thenComparingInt(clubs::indexOf)
            );
            int count = q[0] + q[1] + q[2];
            if (pool.size() < count) return;
            int i = 0;
            for (var t : pool.subList(0, count)) {
                String stage = i < q[2] ? "GROUPS" : i < q[2] + q[1] ? "PRE2" : "PRE1";
                result.add(stage(t, stage, result.size() + 1));
                i++;
            }
        });
        return result.size() == 47 ? result : List.of();
    }

    private List<Entrant> intercontinental(String id, Rulebook rule, List<Entrant> clubs) {
        var result = new ArrayList<Entrant>();
        int year = rule.startYear();
        for (String confed : List.of("AFC", "OFC", "CAF", "CONCACAF", "CONMEBOL", "UEFA")) {
            String stage =
                confed.equals("OFC") || confed.equals(year % 2 == 0 ? "AFC" : "CAF")
                    ? "PLAYOFF"
                    : confed.equals(year % 2 == 0 ? "CAF" : "AFC")
                      ? "AFRICA_ASIA_PACIFIC"
                      : confed.equals("UEFA")
                        ? "FINAL"
                        : "AMERICAS";
            String key = confed.equals("UEFA")
                ? "CHAMPIONS"
                : confed.equals("CONMEBOL")
                  ? "LIBERTADORES"
                  : confed + "_CHAMPIONS";
            int sourceYear = confed.equals("UEFA") ? year - 1 : year;
            var winners = repository.champions(id, key, sourceYear, sourceYear);
            if (winners.isEmpty() && repository.pending(id, key, sourceYear)) continue;
            clubs
                .stream()
                .filter(
                    t ->
                        confederation(t.association()).equals(confed) && (winners.isEmpty() || winners.contains(t.id()))
                )
                .findFirst()
                .ifPresent(t -> result.add(stage(t, stage, result.size() + 1)));
        }
        return result;
    }

    private List<Entrant> worldCup(String id, Rulebook rule, List<Entrant> clubs) {
        var result = new ArrayList<Entrant>();
        int year = rule.startYear();
        var quotas = Map.of("UEFA", 12, "CONMEBOL", 6, "AFC", 4, "CAF", 4, "CONCACAF", 4, "OFC", 1);
        for (String confed : List.of("UEFA", "CONMEBOL", "AFC", "CAF", "CONCACAF", "OFC")) {
            var winners = repository.qualified(id, "CLUB_WORLD_CUP", year);
            var points = repository.internationalRanking(
                id,
                confed.equals("UEFA")
                    ? "CHAMPIONS"
                    : confed.equals("CONMEBOL")
                      ? "LIBERTADORES"
                      : confed + "_CHAMPIONS",
                year - 4,
                year - 1
            );
            var pool = new ArrayList<>(
                clubs
                    .stream()
                    .filter(t -> confederation(t.association()).equals(confed))
                    .toList()
            );
            pool.sort(
                Comparator.comparing((Entrant t) -> !winners.contains(t.id()))
                    .thenComparing(Comparator.comparingInt((Entrant t) -> points.getOrDefault(t.id(), 0)).reversed())
                    .thenComparing(Comparator.comparingInt(Entrant::rating).reversed())
            );
            var associations = new HashMap<String, Integer>();
            int count = 0;
            for (var t : pool) {
                if (count == quotas.get(confed)) break;
                if (associations.getOrDefault(t.association(), 0) >= 2 && !winners.contains(t.id())) continue;
                result.add(stage(t, "GROUPS", result.size() + 1));
                associations.merge(t.association(), 1, Integer::sum);
                count++;
            }
        }
        // The host is a simulation setting until FIFA publishes the host/access list of the next edition.
        clubs
            .stream()
            .filter(t -> t.association().equals("Estados Unidos") && result.stream().noneMatch(r -> r.id() == t.id()))
            .findFirst()
            .ifPresent(t -> result.add(stage(t, "GROUPS", 32)));
        return result.size() == 32 ? result : List.of();
    }
}
