package br.com.manager.utils;

import br.com.manager.models.dto.ClubEventDtos.*;
import java.time.*;
import java.util.*;

public final class ClubEventRules {

    private ClubEventRules() {}

    public static final List<Kind> KINDS = List.of(
        new Kind(
            "FRIENDLY",
            "Amistoso",
            "Taxa de organização e renda de bilheteria dividida igualmente entre os clubes.",
            7,
            false
        ),
        new Kind(
            "CAMP",
            "Concentração",
            "Treinos em outro local, com focos técnicos próprios e desgaste físico diário.",
            14,
            false
        ),
        new Kind(
            "CHARITY",
            "Evento de caridade",
            "Ação social que aproxima o clube da comunidade e da torcida.",
            30,
            false
        ),
        new Kind("MARKETING", "Ação de marketing", "Encontro com torcedores e divulgação do clube.", 21, false),
        new Kind(
            "YOUTH_DAY",
            "Dia dos juniores",
            "Integração da base e do elenco profissional; estimula o desenvolvimento dos juniores.",
            0,
            true
        ),
        new Kind(
            "ANNIVERSARY",
            "Aniversário do clube",
            "Celebração na data de fundação cadastrada para o clube.",
            0,
            true
        ),
        new Kind(
            "TEAM_PARTY",
            "Festa da equipe",
            "Convivência para fortalecer a relação entre os jogadores e o treinador.",
            30,
            false
        ),
        new Kind(
            "SEASON_PARTY",
            "Festa de fim de temporada",
            "Celebração após os compromissos competitivos da temporada.",
            0,
            true
        ),
        new Kind(
            "PLAYER_AWARDS",
            "Prêmio dos melhores jogadores",
            "Reconhecimento do desempenho da temporada, aproximando o elenco do treinador.",
            0,
            true
        )
    );
    public static final List<Venue> VENUES = List.of(
        new Venue(
            "ATIBAIA",
            "Atibaia",
            "Brasil",
            -23.12,
            -46.55,
            55000,
            10,
            List.of("stamina", "teamwork"),
            "Preparação física e trabalho coletivo."
        ),
        new Venue(
            "TERESOPOLIS",
            "Teresópolis",
            "Brasil",
            -22.41,
            -42.97,
            60000,
            10,
            List.of("short_passing", "technique"),
            "Controle de bola e passes curtos."
        ),
        new Venue(
            "ALGARVE",
            "Algarve",
            "Portugal",
            37.02,
            -7.93,
            85000,
            9,
            List.of("finishing", "off_ball"),
            "Finalização e movimentação ofensiva."
        ),
        new Venue(
            "MARBELLA",
            "Marbella",
            "Espanha",
            36.51,
            -4.89,
            100000,
            10,
            List.of("dribbling", "vision"),
            "Criatividade e condução de bola."
        ),
        new Venue(
            "ALPS",
            "Alpes austríacos",
            "Áustria",
            47.45,
            12.39,
            110000,
            12,
            List.of("strength", "stamina"),
            "Treinamento físico intenso."
        ),
        new Venue(
            "ANTALYA",
            "Antalya",
            "Turquia",
            36.89,
            30.71,
            75000,
            10,
            List.of("tackling", "marking"),
            "Organização e fundamentos defensivos."
        ),
        new Venue(
            "DOHA",
            "Doha",
            "Catar",
            25.28,
            51.53,
            130000,
            11,
            List.of("acceleration", "pace"),
            "Explosão e velocidade."
        ),
        new Venue(
            "ORLANDO",
            "Orlando",
            "Estados Unidos",
            28.54,
            -81.38,
            115000,
            9,
            List.of("composure", "teamwork"),
            "Entrosamento e controle emocional."
        ),
        new Venue(
            "CHIBA",
            "Chiba",
            "Japão",
            35.61,
            140.11,
            120000,
            10,
            List.of("concentration", "decisions"),
            "Concentração e tomada de decisão."
        )
    );

    public static Kind kind(String key) {
        return KINDS.stream()
            .filter(k -> k.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Tipo de evento inválido."));
    }

    public static Venue venue(String key) {
        return VENUES.stream()
            .filter(v -> v.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Selecione um local de concentração."));
    }

    public static LocalDate anniversary(LocalDate foundation, LocalDate current) {
        var date = MonthDay.from(foundation).atYear(current.getYear());
        return date.isBefore(current) ? MonthDay.from(foundation).atYear(current.getYear() + 1) : date;
    }

    public static long friendlyFee(int rating) {
        return 25000L + rating * rating * 40L;
    }

    public static long cost(String type, int rating, int opponentRating, int days, String venue) {
        return switch (type) {
            case "FRIENDLY" -> friendlyFee(opponentRating);
            case "CAMP" -> Math.multiplyExact(venue(venue).dailyCost(), days);
            case "CHARITY" -> 50000L + rating * 1000L;
            case "MARKETING" -> 30000L + rating * 750L;
            case "YOUTH_DAY" -> 25000L;
            case "ANNIVERSARY" -> 100000L + rating * 1500L;
            case "TEAM_PARTY" -> 15000L + rating * 500L;
            case "SEASON_PARTY" -> 75000L + rating * 1000L;
            case "PLAYER_AWARDS" -> 50000L + rating * 750L;
            default -> throw new IllegalArgumentException("Tipo de evento inválido.");
        };
    }

    public static long gate(int homeRating, int awayRating) {
        long attendance = Math.min(65000, Math.max(2000, (homeRating + awayRating) * 180));
        return attendance * 60L;
    }
}
