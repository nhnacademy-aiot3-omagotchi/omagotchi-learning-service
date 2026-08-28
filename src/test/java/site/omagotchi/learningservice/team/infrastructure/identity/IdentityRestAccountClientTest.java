package site.omagotchi.learningservice.team.infrastructure.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.http.RestClientCallExecutor;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.infrastructure.identity.request.IdentityAccountBatchRequest;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountResponse;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountSearchResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IdentityRestAccountClientTest {

    private final IdentityAccountHttpService httpService =
            mock(IdentityAccountHttpService.class);
    private final IdentityAccountErrorResolver errorResolver =
            mock(IdentityAccountErrorResolver.class);
    private final IdentityRestAccountClient accountClient = new IdentityRestAccountClient(
            httpService,
            new RestClientCallExecutor(),
            errorResolver
    );

    @ParameterizedTest
    @EnumSource(IdentityAccountState.class)
    @DisplayName("Identity 계정 상태의 손실 없는 반환")
    void preservesIdentityAccountState(IdentityAccountState state) {
        // Given: Identity가 반환한 실제 계정 상태
        UUID accountId = UUID.randomUUID();
        given(httpService.getAccount(accountId)).willReturn(ResponseEntity.ok(
                new IdentityAccountResponse(accountId, "사용자", state)
        ));

        // When: 계정 상태 조회
        IdentityAccountState result = accountClient.getState(accountId);

        // Then: 별도 상태로 축약하지 않은 동일 상태
        assertThat(result).isEqualTo(state);
    }

    @Test
    @DisplayName("Identity 계정 미존재 오류의 Learning 계약 변환")
    void mapsMissingAccountError() {
        // Given: Identity의 계정 미존재 응답과 Resolver가 확정한 Learning 오류
        UUID accountId = UUID.randomUUID();
        RestClientResponseException exception = clientError(HttpStatus.NOT_FOUND);
        BusinessException expected = new BusinessException(TeamErrorCode.ACCOUNT_NOT_FOUND);
        given(httpService.getAccount(accountId)).willThrow(exception);
        given(errorResolver.resolveAccountLookupError(exception)).willReturn(expected);

        // When & Then: 상태가 아닌 오류 계약으로 전파
        assertThatThrownBy(() -> accountClient.getState(accountId)).isSameAs(expected);
    }

    @Test
    @DisplayName("Identity 표시 이름 일괄 조회의 요청 중복 제거")
    void getsDisplayNamesWithOneDeduplicatedRequest() {
        // Given: 중복 계정 ID와 Identity 일괄 응답
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        IdentityAccountBatchRequest expectedRequest = new IdentityAccountBatchRequest(
                List.of(firstId, secondId)
        );
        given(httpService.getAccounts(expectedRequest)).willReturn(ResponseEntity.ok(List.of(
                new IdentityAccountResponse(firstId, "첫 사용자", IdentityAccountState.ACTIVE),
                new IdentityAccountResponse(secondId, "둘째 사용자", IdentityAccountState.LOCKED)
        )));

        // When: 표시 이름 일괄 조회
        Map<UUID, String> result = accountClient.findDisplayNames(
                List.of(firstId, secondId, firstId)
        );

        // Then: 중복 제거된 단일 요청과 계정별 표시 이름
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                firstId, "첫 사용자",
                secondId, "둘째 사용자"
        ));
        verify(httpService).getAccounts(expectedRequest);
    }

    @Test
    @DisplayName("Identity 이름 이메일 검색 응답을 Application 값으로 변환")
    void searchesAccounts() {
        UUID accountId = UUID.randomUUID();
        given(httpService.searchAccounts("사용자")).willReturn(ResponseEntity.ok(List.of(
                new IdentityAccountSearchResponse(
                        accountId,
                        "검색 사용자",
                        "search@example.com",
                        IdentityAccountState.ACTIVE
                )
        )));

        assertThat(accountClient.search("사용자")).singleElement().satisfies(account -> {
            assertThat(account.accountId()).isEqualTo(accountId);
            assertThat(account.displayName()).isEqualTo("검색 사용자");
            assertThat(account.email()).isEqualTo("search@example.com");
            assertThat(account.status()).isEqualTo(IdentityAccountState.ACTIVE);
        });
    }

    @Test
    @DisplayName("Identity 성공 응답의 요청 계정 불일치 거절")
    void rejectsMismatchedAccountResponse() {
        // Given: 요청과 다른 계정 ID를 포함한 성공 응답
        UUID requestedId = UUID.randomUUID();
        given(httpService.getAccount(requestedId)).willReturn(ResponseEntity.ok(
                new IdentityAccountResponse(
                        UUID.randomUUID(),
                        "다른 사용자",
                        IdentityAccountState.ACTIVE
                )
        ));

        // When & Then: 성공 응답 계약 위반의 502 오류
        assertThatThrownBy(() -> accountClient.getState(requestedId))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE)
                );
    }

    private static RestClientResponseException clientError(HttpStatus status) {
        RestClientResponseException exception = mock(RestClientResponseException.class);
        given(exception.getStatusCode()).willReturn(status);
        return exception;
    }
}
