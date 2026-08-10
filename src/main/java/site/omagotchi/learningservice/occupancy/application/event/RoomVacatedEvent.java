package site.omagotchi.learningservice.occupancy.application.event;

import java.time.OffsetDateTime;

/**
 * 회의실이 비었다 (MR-03, MR-14).
 *
 * <p>공실 알림 신청자에게 발송을 트리거하는 신호다. 발행 주체는 점유를 종료시킨 흐름이고,
 * 실제 발송은 알림 파트가 리스너로 받아 처리한다 (명세서 04).</p>
 *
 * <p><b>발행하는 종료와 하지 않는 종료가 있다.</b></p>
 * <ul>
 *   <li>{@code RELEASED} — 반납(MR-14), 계정 삭제(MR-26), 기수 종료(CE-03): <b>발행</b></li>
 *   <li>{@code EXPIRED} — 타임아웃(스케줄러 #9): <b>발행</b></li>
 *   <li>{@code FORCE_RELEASED} — 매니저 강제 종료(MR-21): <b>발행하지 않는다</b>.
 *       공간 회수가 목적이라 대기자에게 알리면 안 되고, 대기 신청은 물리 삭제된다</li>
 * </ul>
 *
 * <p>기수를 담지 않는 것이 의도다. 회의실은 여러 기수가 공유하는 자원이라 <b>타 기수
 * 대기자에게도 알림이 가야 한다</b> (CE-03). 수신 대상은 리스너가 {@code vacancy_alerts}를
 * 조회해 정한다.</p>
 *
 * <p>점유자·참여자 정보도 담지 않는다 (MR-36) — 알림 본문에 "누가 쓰던 방인지"가 들어가면
 * 타 기수 사용자의 개인정보가 노출된다.</p>
 *
 * @param spaceId     비워진 회의실
 * @param occupancyId 종료된 점유. 리스너가 중복 처리를 판별할 때 쓴다
 * @param vacatedAt   종료 시각. 발송이 지연돼도 "언제 비었는지"는 이 값이 정본이다
 */
public record RoomVacatedEvent(
        Long spaceId,
        Long occupancyId,
        OffsetDateTime vacatedAt
) {
}
