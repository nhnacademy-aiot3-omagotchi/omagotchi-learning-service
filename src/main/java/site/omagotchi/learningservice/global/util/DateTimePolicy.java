package site.omagotchi.learningservice.global.util;

import java.time.LocalTime;
import java.time.ZoneId;

public final class DateTimePolicy {
    // 최소 MVP에서는 모든 시스템이 한국 기준, 새벽 4시를 기점으로 하루를 변경한다고 가정한다.
    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final LocalTime DAILY_RESET_TIME = LocalTime.of(4, 0);

    private DateTimePolicy() {
    }
}
