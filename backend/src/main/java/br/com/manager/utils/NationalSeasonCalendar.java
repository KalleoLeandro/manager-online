package br.com.manager.utils;

import java.time.LocalDate;

/** Calendar windows are independent of the save creation date and repeat for each game year. */
public final class NationalSeasonCalendar {

    private NationalSeasonCalendar() {}

    public record Window(LocalDate first, LocalDate last) {}

    public static Window window(String country, int division, int year) {
        if (!country.equalsIgnoreCase("Brasil") && !country.equalsIgnoreCase("Brazil")) return new Window(
            LocalDate.of(year, 8, 1),
            LocalDate.of(year + 1, 5, 30)
        );
        return switch (division) {
            case 2 -> new Window(LocalDate.of(year, 3, 20), LocalDate.of(year, 11, 14));
            case 3 -> new Window(LocalDate.of(year, 4, 5), LocalDate.of(year, 10, 25));
            default -> new Window(LocalDate.of(year, 1, 28), LocalDate.of(year, 12, 2));
        };
    }
}
