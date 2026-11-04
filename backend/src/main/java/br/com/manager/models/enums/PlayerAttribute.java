package br.com.manager.models.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum PlayerAttribute {
    SHOT_POWER("shot_power", 0, -12, -5, 3),
    LONG_SHOTS("long_shots", -30, -18, 1, -3),
    DRIBBLING("dribbling", -30, -17, 2, 1),
    TECHNIQUE("technique", -20, -8, 3, 1),
    CROSSING("crossing", -35, -10, 0, -5),
    VISION("vision", -20, -10, 3, -4),
    FINISHING("finishing", -40, -28, -9, 3),
    SHORT_PASSING("short_passing", -12, -4, 3, -6),
    LONG_PASSING("long_passing", -10, -5, 2, -12),
    TACKLING("tackling", -30, 3, -8, -27),
    MARKING("marking", -35, 3, -12, -30),
    HEADING("heading", -20, 2, -13, 0),
    FIRST_TOUCH("first_touch", -20, -8, 2, 2),
    OFF_BALL("off_ball", -35, -10, 0, 3),
    FREE_KICKS("free_kicks", -35, -22, -9, -12),
    CORNERS("corners", -35, -18, -7, -14),
    PENALTIES("penalties", -30, -18, -8, -2),
    ANTICIPATION("anticipation", 1, 1, 1, 0),
    AGGRESSION("aggression", -6, 1, -2, -3),
    COMPOSURE("composure", 1, 0, 0, 1),
    CONCENTRATION("concentration", 2, 1, 0, -2),
    DECISIONS("decisions", 0, 0, 2, -1),
    DETERMINATION("determination", 0, 0, 0, 0),
    TEAMWORK("teamwork", -2, 1, 2, -3),
    LEADERSHIP("leadership", -4, -5, -5, -6),
    CONSISTENCY("consistency", -2, -2, -2, -2),
    ACCELERATION("acceleration", -12, -3, 0, 2),
    PACE("pace", -12, -3, 0, 3),
    WORK_RATE("work_rate", -10, 0, 2, -2),
    STRENGTH("strength", 1, 3, -6, 1),
    AGILITY("agility", 2, -5, 1, 2),
    JUMPING("jumping", 3, 2, -8, 0),
    STAMINA("stamina", -10, 0, 3, 0),
    INJURY_RESISTANCE("injury_resistance", 0, 0, 0, 0),
    REFLEXES("reflexes", 3, -50, -50, -50),
    HANDLING("handling", 2, -50, -50, -50),
    GK_POSITIONING("gk_positioning", 2, -50, -50, -50),
    ONE_ON_ONES("one_on_ones", 0, -50, -50, -50),
    KICKING("kicking", -2, -45, -45, -45),
    DIVING("diving", 2, -50, -50, -50);

    private final String column;
    private final int[] offsets;

    PlayerAttribute(String column, int g, int d, int m, int a) {
        this.column = column;
        this.offsets = new int[] { g, d, m, a };
    }

    public String key() {
        return column;
    }

    public long mask() {
        return 1L << ordinal();
    }

    public int offset(String position) {
        return offsets[
            (switch (position) {
                case "GOL" -> 0;
                case "DEF" -> 1;
                case "MEI" -> 2;
                default -> 3;
            })
        ];
    }

    public static String columns() {
        return Arrays.stream(values()).map(PlayerAttribute::key).collect(Collectors.joining(","));
    }

    public static String selection(String alias) {
        return Arrays.stream(values())
            .map(a -> alias + "." + a.key())
            .collect(Collectors.joining(","));
    }

    public static PlayerAttribute specialty(String label) {
        return switch (label) {
            case "Reflexos" -> REFLEXES;
            case "Posicionamento" -> GK_POSITIONING;
            case "Desarme" -> TACKLING;
            case "Marcação" -> MARKING;
            case "Passe" -> SHORT_PASSING;
            case "Drible" -> DRIBBLING;
            case "Velocidade" -> PACE;
            case "Finalização" -> FINISHING;
            default -> null;
        };
    }
}
