package site.omagotchi.learningservice.weather.infrastructure;

import java.time.LocalDate;

public record BaseTime(
        LocalDate baseDate,
        String baseTime
) {
}
