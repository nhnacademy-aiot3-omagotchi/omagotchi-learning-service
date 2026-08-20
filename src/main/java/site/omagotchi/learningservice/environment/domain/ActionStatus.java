package site.omagotchi.learningservice.environment.domain;

/** 뷰에 보여주기 위한 상태. 룰 히트 한정 제대로 iot기기가 조치를 취해줬는지 확인 */
public enum ActionStatus {

    /// 제어기가 동작을 확인함
    CONFIRMED,

    /// 제어기가 확인하지 못함(타임아웃 등등)
    FAILED,

    /// 쿨다운 (연속으로 들어올때 굳이 다시 안보내기 위함)
    SKIPPED,

    /// 조치 대상이 아님 (RULE_HIT를 제외한 대상들)
    NONE
}