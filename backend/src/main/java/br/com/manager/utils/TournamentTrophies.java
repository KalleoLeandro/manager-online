package br.com.manager.utils;

public final class TournamentTrophies {

    private TournamentTrophies() {}

    public static int forCompetition(String key) {
        return switch (key) {
            case "BRA_CUP" -> 20;
            case "ENG_CUP" -> 21;
            case "ESP_CUP" -> 22;
            case "ITA_CUP" -> 23;
            case "FRA_CUP" -> 24;
            case "GER_CUP" -> 25;
            case "LIBERTADORES" -> 30;
            case "CHAMPIONS" -> 31;
            case "INTERCONTINENTAL" -> 32;
            case "CLUB_WORLD_CUP" -> 33;
            default -> 3;
        };
    }
}
