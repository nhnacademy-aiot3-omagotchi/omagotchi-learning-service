package site.omagotchi.learningservice.environment.domain;

/** 룰 히트 됐을 때 조치할 행동 */
public enum IotAction {
    VENTILATE("창문 개방 환기"),
    COOL("에어컨 냉방"),
    HEAT("난방 가동"),
    DEHUMIDIFY("제습기 가동"),
    HUMIDIFY("가습기 가동");

    private final String label;

    IotAction(String label){
        this.label = label;
    }

    public String label(){
        return label;
    }
}