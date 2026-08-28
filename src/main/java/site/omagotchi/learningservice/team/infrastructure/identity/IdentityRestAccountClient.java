package site.omagotchi.learningservice.team.infrastructure.identity;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.http.RestClientCallExecutor;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;
import site.omagotchi.learningservice.team.infrastructure.identity.request.IdentityAccountBatchRequest;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountResponse;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountSearchResponse;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Identity HTTP 응답을 Team Application의 계정 상태·표시 이름으로 변환하는 Outbound Adapter
@Component
@RequiredArgsConstructor
public class IdentityRestAccountClient implements IdentityAccountClient {

    private final IdentityAccountHttpService httpService;
    private final RestClientCallExecutor callExecutor;
    private final IdentityAccountErrorResolver errorResolver;

    @Override
    public IdentityAccountState getState(UUID userId) {
        return callExecutor.execute(
                () -> fetchAccountState(userId),
                exception -> {
                    throw errorResolver.resolveAccountLookupError(exception);
                }
        );
    }

    @Override
    public Map<UUID, String> findDisplayNames(Collection<UUID> userIds) {
        Set<UUID> requestedIds = new LinkedHashSet<>(userIds);
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        return callExecutor.execute(
                () -> fetchDisplayNames(requestedIds),
                exception -> {
                    throw errorResolver.resolveBatchLookupError(exception);
                }
        );
    }

    @Override
    public List<IdentityAccountView> search(String query) {
        return callExecutor.execute(
                () -> fetchSearchResults(query),
                exception -> {
                    throw errorResolver.resolveBatchLookupError(exception);
                }
        );
    }

    private IdentityAccountState fetchAccountState(UUID userId) {
        ResponseEntity<IdentityAccountResponse> response = httpService.getAccount(userId);
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            throw invalidResponse(
                    "단건 조회 성공 응답 Status 불일치 expected=200, actual="
                            + response.getStatusCode().value()
            );
        }

        IdentityAccountResponse account = response.getBody();
        if (account == null || !userId.equals(account.accountId())) {
            throw invalidResponse("단건 조회 응답의 계정 식별자 불일치");
        }
        if (!StringUtils.hasText(account.displayName()) || account.status() == null) {
            throw invalidResponse("단건 조회 응답의 필수 필드 누락");
        }
        return account.status();
    }

    private Map<UUID, String> fetchDisplayNames(Set<UUID> requestedIds) {
        ResponseEntity<List<IdentityAccountResponse>> response = httpService.getAccounts(
                new IdentityAccountBatchRequest(List.copyOf(requestedIds))
        );
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            throw invalidResponse(
                    "일괄 조회 성공 응답 Status 불일치 expected=200, actual="
                            + response.getStatusCode().value()
            );
        }

        List<IdentityAccountResponse> accounts = response.getBody();
        if (accounts == null) {
            throw invalidResponse("일괄 조회 성공 응답 Body 누락");
        }

        Map<UUID, String> displayNames = new HashMap<>();
        for (IdentityAccountResponse account : accounts) {
            if (account == null
                    || account.accountId() == null
                    || !requestedIds.contains(account.accountId())) {
                throw invalidResponse("일괄 조회 응답에 요청하지 않은 계정 포함");
            }
            if (!StringUtils.hasText(account.displayName()) || account.status() == null) {
                throw invalidResponse("일괄 조회 응답의 필수 필드 누락");
            }
            String previous = displayNames.put(account.accountId(), account.displayName());
            if (previous != null) {
                throw invalidResponse("일괄 조회 응답의 accountId 중복");
            }
        }
        return Map.copyOf(displayNames);
    }

    private List<IdentityAccountView> fetchSearchResults(String query) {
        ResponseEntity<List<IdentityAccountSearchResponse>> response =
                httpService.searchAccounts(query);
        if (response.getStatusCode().value() != HttpStatus.OK.value()) {
            throw invalidResponse(
                    "검색 성공 응답 Status 불일치 expected=200, actual="
                            + response.getStatusCode().value()
            );
        }
        List<IdentityAccountSearchResponse> accounts = response.getBody();
        if (accounts == null) {
            throw invalidResponse("검색 성공 응답 Body 누락");
        }

        Set<UUID> accountIds = new LinkedHashSet<>();
        return accounts.stream().map(account -> {
            if (account == null
                    || account.accountId() == null
                    || !accountIds.add(account.accountId())
                    || !StringUtils.hasText(account.displayName())
                    || !StringUtils.hasText(account.email())
                    || account.status() == null) {
                throw invalidResponse("검색 응답의 필수 필드 누락 또는 accountId 중복");
            }
            return new IdentityAccountView(
                    account.accountId(),
                    account.displayName(),
                    account.email(),
                    account.status()
            );
        }).toList();
    }

    private static BusinessException invalidResponse(String diagnosticMessage) {
        return new BusinessException(
                CommonErrorCode.DOWNSTREAM_INVALID_RESPONSE,
                diagnosticMessage
        );
    }
}
