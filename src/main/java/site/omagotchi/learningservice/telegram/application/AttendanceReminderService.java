package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.application.dto.result.AttendanceReminderResponse;
import site.omagotchi.learningservice.telegram.domain.AttendanceReminder;
import site.omagotchi.learningservice.telegram.domain.ReminderChannel;
import site.omagotchi.learningservice.telegram.domain.ReminderType;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.infrastructure.AttendanceReminderRepository;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceReminderService {

    private final AttendanceReminderRepository reminderRepository;
    private final CohortMembershipRepository membershipRepository;

    /**
     * 동일 소속, 날짜, 알림 유형, 채널 조합의 알림 이력을 하나만 유지한다.
     * 스케줄러가 중복 실행되어도 같은 알림 row를 재사용하기 위한 멱등 생성 메서드다.
     */
    @Transactional
    public AttendanceReminderResponse getOrCreatePending(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        if (!membershipRepository.existsById(cohortMembershipId)) {
            throw new BusinessException(TelegramErrorCode.ATTENDANCE_REMINDER_MEMBERSHIP_NOT_FOUND);
        }

        return reminderRepository.findByCohortMembershipIdAndAttendanceDateAndReminderTypeAndChannel(
                        cohortMembershipId,
                        attendanceDate,
                        reminderType,
                        channel
                )
                .map(AttendanceReminderResponse::from)
                .orElseGet(() -> createPending(cohortMembershipId, attendanceDate, reminderType, channel));
    }

    private AttendanceReminderResponse createPending(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        try {
            AttendanceReminder reminder = AttendanceReminder.pending(
                    cohortMembershipId,
                    attendanceDate,
                    reminderType,
                    channel
            );
            return AttendanceReminderResponse.from(reminderRepository.save(reminder));
        } catch (DataIntegrityViolationException exception) {
            return reminderRepository.findByCohortMembershipIdAndAttendanceDateAndReminderTypeAndChannel(
                            cohortMembershipId,
                            attendanceDate,
                            reminderType,
                            channel
                    )
                    .map(AttendanceReminderResponse::from)
                    .orElseThrow(() -> exception);
        }
    }
}
