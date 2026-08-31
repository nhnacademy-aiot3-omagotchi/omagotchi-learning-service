package site.omagotchi.learningservice.team.infrastructure.identity;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import site.omagotchi.learningservice.team.infrastructure.identity.request.IdentityAccountBatchRequest;
import site.omagotchi.learningservice.team.infrastructure.identity.request.IdentityAccountSearchRequest;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountResponse;
import site.omagotchi.learningservice.team.infrastructure.identity.response.IdentityAccountSearchResponse;

import java.util.List;
import java.util.UUID;

// Identity 계정 조회 API의 HTTP 요청·응답 계약
@HttpExchange("/api/v1/internal/accounts")
public interface IdentityAccountHttpService {

    @GetExchange("/{account-id}")
    ResponseEntity<IdentityAccountResponse> getAccount(
            @PathVariable("account-id") UUID accountId
    );

    @PostExchange("/batch")
    ResponseEntity<List<IdentityAccountResponse>> getAccounts(
            @RequestBody IdentityAccountBatchRequest request
    );

    @PostExchange("/search")
    ResponseEntity<List<IdentityAccountSearchResponse>> searchAccounts(
            @RequestBody IdentityAccountSearchRequest request
    );
}
