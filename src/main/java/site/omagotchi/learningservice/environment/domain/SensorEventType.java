package site.omagotchi.learningservice.environment.domain;

/** 품질 메세지 타입 */
public enum SensorEventType {
    /// 물리범위 밖 - <b> 범위초과 </b>
    ANOMALY,

    /// fCnt 갭 프레임이 영영 없음 - <b> 결측 </b>
    MISSING,

    /// 같은 프레임 재도착 - <b> 중복 </b>
    DUPLICATE,

    /// 늦은 도착 - <b> 지연 </b>
    DELAYED,

    /// 값 고정 - <b> 무변동 </b>
    STUCK,

    /// 룰 조건 충족 - <b> 룰적중 </b>
    RULE_HIT,

    /// 판독 불가 - <b> 무효 </b>
    INVALID,

    /// 주기 3배 침묵 상태 - <b> 끊김 시작/종료 </b>
    DISCONNECTED

}
