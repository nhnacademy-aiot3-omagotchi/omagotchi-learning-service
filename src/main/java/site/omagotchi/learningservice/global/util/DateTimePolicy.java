package site.omagotchi.learningservice.global.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.ZoneId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DateTimePolicy {

    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final LocalTime DAILY_RESET_TIME = LocalTime.of(4, 0);
}
