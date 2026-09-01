package site.omagotchi.learningservice.space.application.result;

/** 학생이 입실 대상으로 고를 수 있는 활성 실습실. */
public record SelectableLabView(
        Long spaceId,
        String name,
        Integer capacity,
        long reservedCount
) {
}
