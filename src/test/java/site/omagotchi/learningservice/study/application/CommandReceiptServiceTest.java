package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.study.application.result.TimerStateResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("명령 영수증")
class CommandReceiptServiceTest {

    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID COMMAND_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID TIMER_RUN_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final String TIMER_START = "TIMER_START";
    private static final String START_REQUEST_HASH =
            "20ee522da2ac2b4798a2fc67801bff04ec323378666ef0887ead814bddc04630";

    @Mock
    private CommandReceiptRepository commandReceiptRepository;

    private CommandReceiptService commandReceiptService;

    @BeforeEach
    void setUp() {
        commandReceiptService = new CommandReceiptService(commandReceiptRepository);
    }

    @Nested
    @DisplayName("최초 명령")
    class FirstCommand {

        @Test
        @DisplayName("정상 처리")
        void executesAndSavesReceipt() {
            given(commandReceiptRepository.find(
                    COHORT_MEMBERSHIP_ID,
                    COMMAND_ID,
                    TIMER_START,
                    START_REQUEST_HASH,
                    TimerStateResult.class
            )).willReturn(Optional.empty());
            TimerStateResult result = TimerStateResult.running(
                    TIMER_RUN_ID,
                    Instant.parse("2000-01-01T00:00:00Z"),
                    0L
            );

            CommandReceiptService.CommandResult<TimerStateResult> returned =
                    commandReceiptService.execute(
                            COHORT_MEMBERSHIP_ID,
                            COMMAND_ID,
                            TIMER_START,
                            "",
                            TimerStateResult.class,
                            () -> new CommandReceiptService.CommandResult<>(
                                    (short) 201,
                                    "TIMER_STARTED",
                                    result,
                                    TIMER_RUN_ID,
                                    null
                            )
                    );

            @SuppressWarnings("unchecked")
            ArgumentCaptor<CommandReceiptRepository.NewReceipt<TimerStateResult>> captor =
                    ArgumentCaptor.forClass(CommandReceiptRepository.NewReceipt.class);
            verify(commandReceiptRepository).save(captor.capture());
            CommandReceiptRepository.NewReceipt<TimerStateResult> receipt = captor.getValue();
            assertAll(
                    () -> assertSame(result, returned.payload()),
                    () -> assertEquals(TIMER_START, receipt.commandCode()),
                    () -> assertEquals(START_REQUEST_HASH, receipt.requestHash()),
                    () -> assertEquals((short) 201, receipt.httpStatus()),
                    () -> assertEquals("TIMER_STARTED", receipt.resultCode()),
                    () -> assertSame(result, receipt.resultPayload()),
                    () -> assertEquals(TIMER_RUN_ID, receipt.targetTimerRunId())
            );
        }
    }

    @Nested
    @DisplayName("재전송")
    class Replay {

        @Test
        @DisplayName("동일 요청 결과 재현")
        void replaysStoredResult() {
            TimerStateResult storedResult = TimerStateResult.running(
                    TIMER_RUN_ID,
                    Instant.parse("2000-01-01T00:00:00Z"),
                    0L
            );
            given(commandReceiptRepository.find(
                    COHORT_MEMBERSHIP_ID,
                    COMMAND_ID,
                    TIMER_START,
                    START_REQUEST_HASH,
                    TimerStateResult.class
            )).willReturn(Optional.of(new CommandReceiptRepository.StoredReceipt<>(
                    TIMER_START,
                    START_REQUEST_HASH,
                    (short) 201,
                    "TIMER_STARTED",
                    storedResult,
                    TIMER_RUN_ID,
                    null
            )));
            @SuppressWarnings("unchecked")
            Supplier<CommandReceiptService.CommandResult<TimerStateResult>> command =
                    org.mockito.Mockito.mock(Supplier.class);

            CommandReceiptService.CommandResult<TimerStateResult> returned =
                    commandReceiptService.execute(
                            COHORT_MEMBERSHIP_ID,
                            COMMAND_ID,
                            TIMER_START,
                            "",
                            TimerStateResult.class,
                            command
                    );

            assertAll(
                    () -> assertSame(storedResult, returned.payload()),
                    () -> assertEquals((short) 201, returned.httpStatus()),
                    () -> assertEquals("TIMER_STARTED", returned.resultCode())
            );
            verify(command, never()).get();
            verify(commandReceiptRepository, never()).save(any());
        }

        @Test
        @DisplayName("다른 요청 충돌")
        void rejectsDifferentRequest() {
            given(commandReceiptRepository.find(
                    COHORT_MEMBERSHIP_ID,
                    COMMAND_ID,
                    "TIMER_DISCARD",
                    "480440ac59bd32e8f5dfe61a969d229abaecf0ced7b2b1cd19f16bc4e9c9e2ce",
                    Void.class
            )).willReturn(Optional.of(new CommandReceiptRepository.StoredReceipt<>(
                    "TIMER_STOP",
                    "bd096d0590be8bac1a48a2f0d21db75627f0340db434c2910ca5c7af13ddbb6b",
                    (short) 204,
                    "TIMER_STOPPED",
                    null,
                    TIMER_RUN_ID,
                    null
            )));
            @SuppressWarnings("unchecked")
            Supplier<CommandReceiptService.CommandResult<Void>> command =
                    org.mockito.Mockito.mock(Supplier.class);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> commandReceiptService.execute(
                            COHORT_MEMBERSHIP_ID,
                            COMMAND_ID,
                            "TIMER_DISCARD",
                            TIMER_RUN_ID.toString(),
                            Void.class,
                            command
                    )
            );

            assertSame(CommandReceiptErrorCode.COMMAND_ID_CONFLICT, exception.getErrorCode());
            verify(command, never()).get();
            verify(commandReceiptRepository, never()).save(any());
        }
    }
}
