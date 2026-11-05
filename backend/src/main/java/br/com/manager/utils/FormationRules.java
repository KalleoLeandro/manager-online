package br.com.manager.utils;

import br.com.manager.models.dto.PitchPositionDto;
import java.util.*;

public final class FormationRules {

    private FormationRules() {}

    public static final Map<String, List<String>> ROLES;

    static {
        var m = new LinkedHashMap<String, List<String>>();
        m.put("4-3-3", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "VOL", "MC", "MC", "PE", "CA", "PD"));
        m.put("4-4-2", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "ME", "MC", "MC", "MD", "CA", "CA"));
        m.put("3-5-2", List.of("GOL", "ZAG", "ZAG", "ZAG", "ALE", "VOL", "MC", "MEI", "ALD", "CA", "CA"));
        m.put("4-5-1", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "ME", "VOL", "MC", "MC", "MD", "CA"));
        m.put("4-2-3-1", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "VOL", "VOL", "PE", "MEI", "PD", "CA"));
        m.put("4-1-4-1", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "VOL", "ME", "MC", "MC", "MD", "CA"));
        m.put("4-3-1-2", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "VOL", "MC", "MC", "MEI", "CA", "CA"));
        m.put("4-1-2-1-2", List.of("GOL", "LE", "ZAG", "ZAG", "LD", "VOL", "MC", "MC", "MEI", "CA", "CA"));
        m.put("3-4-3", List.of("GOL", "ZAG", "ZAG", "ZAG", "ALE", "MC", "MC", "ALD", "PE", "CA", "PD"));
        m.put("3-4-2-1", List.of("GOL", "ZAG", "ZAG", "ZAG", "ALE", "MC", "MC", "ALD", "MEI", "MEI", "CA"));
        m.put("5-3-2", List.of("GOL", "ALE", "ZAG", "ZAG", "ZAG", "ALD", "VOL", "MC", "MC", "CA", "CA"));
        m.put("5-4-1", List.of("GOL", "ALE", "ZAG", "ZAG", "ZAG", "ALD", "ME", "MC", "MC", "MD", "CA"));
        ROLES = Collections.unmodifiableMap(m);
    }

    public static String family(String role) {
        return switch (role) {
            case "GOL" -> "GOL";
            case "DEF", "ZAG", "LE", "LD", "ALE", "ALD" -> "DEF";
            case "ATA", "PE", "PD", "CA", "SA" -> "ATA";
            default -> "MEI";
        };
    }

    public static List<PitchPositionDto> points(String formation) {
        if (formation.equals("4-3-3")) return List.of(
            new PitchPositionDto(50.0, 89.0),
            new PitchPositionDto(15.0, 65.0),
            new PitchPositionDto(38.0, 73.0),
            new PitchPositionDto(62.0, 73.0),
            new PitchPositionDto(85.0, 65.0),
            new PitchPositionDto(50.0, 56.0),
            new PitchPositionDto(32.0, 42.0),
            new PitchPositionDto(68.0, 42.0),
            new PitchPositionDto(18.0, 22.0),
            new PitchPositionDto(50.0, 15.0),
            new PitchPositionDto(82.0, 22.0)
        );
        var rows = formation.split("-");
        var result = new ArrayList<PitchPositionDto>();
        result.add(new PitchPositionDto(50.0, 89.0));
        for (int row = 0; row < rows.length; row++) {
            int count = Integer.parseInt(rows[row]);
            double y = 70.0 - (row * 48.0) / (rows.length - 1);
            for (int i = 0; i < count; i++) result.add(new PitchPositionDto((100.0 * (i + 1)) / (count + 1), y));
        }
        return result;
    }
}
