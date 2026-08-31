package site.omagotchi.learningservice.occupancy.presentation.response;

/**
 * 내가 지금 회의실을 쓰고 있는지.
 *
 * @param inMeeting 점유자이거나 참여자면 {@code true}
 */
public record MyOccupancyStatusResponse(boolean inMeeting) {

    public static MyOccupancyStatusResponse of(boolean inMeeting) {
        return new MyOccupancyStatusResponse(inMeeting);
    }
}
