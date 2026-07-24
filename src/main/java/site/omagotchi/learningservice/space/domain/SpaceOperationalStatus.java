package site.omagotchi.learningservice.space.domain;

public enum SpaceOperationalStatus {

    /**
     * 활성 상태.
     *
     * <p>신규 회의실 점유 및 공간 재실 신청이 가능합니다.</p>
     */
    ACTIVE,

    /**
     * 비활성 상태.
     *
     * <p>신규 회의실 점유 및 공간 재실 신청을 받을 수 없습니다.</p>
     */
    INACTIVE
}
