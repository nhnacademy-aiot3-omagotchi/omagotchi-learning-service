package site.omagotchi.learningservice.support;

import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.operation.OperationRequest;
import org.springframework.restdocs.operation.OperationRequestFactory;
import org.springframework.restdocs.operation.preprocess.OperationRequestPreprocessor;
import org.springframework.restdocs.operation.preprocess.OperationResponsePreprocessor;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.web.servlet.ResultHandler;

/** REST Docs helpers shared by controller contract tests. */
public final class RestDocs {

    private static final OperationRequestFactory REQUEST_FACTORY = new OperationRequestFactory();
    private static final OperationRequestPreprocessor AUTHORIZATION_REDACTOR = RestDocs::redactAuthorization;
    private static final String REDACTED_BASIC_CREDENTIALS = "W1JFREFDVEVEXTpbUkVEQUNURURd";

    private RestDocs() {}

    public static ResultHandler document(String identifier, Snippet... snippets) {
        return MockMvcRestDocumentation.document(identifier, AUTHORIZATION_REDACTOR, snippets);
    }

    public static ResultHandler document(
            String identifier,
            OperationRequestPreprocessor requestPreprocessor,
            Snippet... snippets) {
        return MockMvcRestDocumentation.document(
                identifier, compose(requestPreprocessor, AUTHORIZATION_REDACTOR), snippets);
    }

    public static ResultHandler document(
            String identifier,
            OperationResponsePreprocessor responsePreprocessor,
            Snippet... snippets) {
        return MockMvcRestDocumentation.document(
                identifier, AUTHORIZATION_REDACTOR, responsePreprocessor, snippets);
    }

    public static ResultHandler document(
            String identifier,
            OperationRequestPreprocessor requestPreprocessor,
            OperationResponsePreprocessor responsePreprocessor,
            Snippet... snippets) {
        return MockMvcRestDocumentation.document(
                identifier,
                compose(requestPreprocessor, AUTHORIZATION_REDACTOR),
                responsePreprocessor,
                snippets);
    }

    private static OperationRequestPreprocessor compose(
            OperationRequestPreprocessor first, OperationRequestPreprocessor second) {
        return request -> second.preprocess(first.preprocess(request));
    }

    private static OperationRequest redactAuthorization(OperationRequest request) {
        List<String> values = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (values == null || values.isEmpty()) {
            return request;
        }

        List<String> redacted = values.stream().map(RestDocs::redactAuthorizationValue).toList();
        if (redacted.equals(values)) {
            return request;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.putAll(request.getHeaders());
        headers.put(HttpHeaders.AUTHORIZATION, redacted);
        return REQUEST_FACTORY.createFrom(request, headers);
    }

    private static String redactAuthorizationValue(String value) {
        String trimmed = value.trim();
        int separator = trimmed.indexOf(' ');
        if (separator <= 0) {
            return value;
        }

        String scheme = trimmed.substring(0, separator);
        if (scheme.equalsIgnoreCase("bearer")) {
            return "Bearer [REDACTED]";
        }
        if (scheme.equalsIgnoreCase("basic")) {
            return "Basic " + REDACTED_BASIC_CREDENTIALS;
        }
        return value;
    }
}
