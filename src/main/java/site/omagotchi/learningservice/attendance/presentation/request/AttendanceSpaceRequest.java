package site.omagotchi.learningservice.attendance.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 체크인과 실습실 이동에서 선택한 공간. */
public record AttendanceSpaceRequest(
        @NotNull @Positive Long spaceId
) {
}
