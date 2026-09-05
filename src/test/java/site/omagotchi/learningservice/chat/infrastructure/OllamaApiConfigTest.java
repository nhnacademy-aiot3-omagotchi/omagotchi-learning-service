package site.omagotchi.learningservice.chat.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpCookieHandling;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.http.client.InetAddressFilter;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Ollama API HTTP 설정")
class OllamaApiConfigTest {

    private static final Duration OLLAMA_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration OLLAMA_READ_TIMEOUT = Duration.ofSeconds(60);

    private RestClient.Builder restClientBuilder;
    private WebClient.Builder webClientBuilder;
    private ClientHttpConnectorBuilder<ClientHttpConnector> connectorBuilder;
    private ClientHttpRequestFactoryBuilder<ClientHttpRequestFactory> requestFactoryBuilder;
    private ClientHttpConnector connector;
    private ClientHttpRequestFactory requestFactory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.restClientBuilder = mock(RestClient.Builder.class, RETURNS_SELF);
        this.webClientBuilder = mock(WebClient.Builder.class, RETURNS_SELF);
        this.connectorBuilder = mock(ClientHttpConnectorBuilder.class);
        this.requestFactoryBuilder = mock(ClientHttpRequestFactoryBuilder.class);
        this.connector = mock(ClientHttpConnector.class);
        this.requestFactory = mock(ClientHttpRequestFactory.class);

        when(this.restClientBuilder.clone()).thenReturn(this.restClientBuilder);
        when(this.webClientBuilder.clone()).thenReturn(this.webClientBuilder);
        when(this.restClientBuilder.build()).thenReturn(mock(RestClient.class));
        when(this.webClientBuilder.build()).thenReturn(mock(WebClient.class));
        when(this.connectorBuilder.build(any(HttpClientSettings.class))).thenReturn(this.connector);
        when(this.requestFactoryBuilder.build(any(HttpClientSettings.class))).thenReturn(this.requestFactory);
    }

    @Test
    @DisplayName("Boot HTTP 빌더로 Ollama 전용 커넥터와 팩토리를 구성한다")
    void usesBootHttpBuildersAndContextClientBuilders() {
        HttpClientSettings bootSettings = HttpClientSettings.defaults();

        this.createConfig().ollamaApi(
                this.restClientBuilder,
                this.webClientBuilder,
                bootSettings,
                this.connectorBuilder,
                this.requestFactoryBuilder
        );

        verify(this.connectorBuilder).build(any(HttpClientSettings.class));
        verify(this.requestFactoryBuilder).build(any(HttpClientSettings.class));
        verify(this.webClientBuilder).clientConnector(this.connector);
        verify(this.restClientBuilder).requestFactory(this.requestFactory);
    }

    @Test
    @DisplayName("Boot HTTP 설정을 유지하고 Ollama 타임아웃만 교체한다")
    void keepsBootHttpSettingsAndOverridesOnlyTimeouts() {
        SslBundle sslBundle = mock(SslBundle.class);
        InetAddressFilter inetAddressFilter = mock(InetAddressFilter.class);
        HttpClientSettings bootSettings = HttpClientSettings.defaults()
                .withCookieHandling(HttpCookieHandling.DISABLE)
                .withRedirects(HttpRedirects.DONT_FOLLOW)
                .withConnectTimeout(Duration.ofMillis(300))
                .withReadTimeout(Duration.ofSeconds(5))
                .withSslBundle(sslBundle)
                .withInetAddressFilter(inetAddressFilter);

        this.createConfig().ollamaApi(
                this.restClientBuilder,
                this.webClientBuilder,
                bootSettings,
                this.connectorBuilder,
                this.requestFactoryBuilder
        );

        HttpClientSettings connectorSettings = this.captureConnectorSettings();
        HttpClientSettings requestFactorySettings = this.captureRequestFactorySettings();

        assertThat(connectorSettings).isEqualTo(requestFactorySettings);
        assertThat(connectorSettings.connectTimeout()).isEqualTo(OLLAMA_CONNECT_TIMEOUT);
        assertThat(connectorSettings.readTimeout()).isEqualTo(OLLAMA_READ_TIMEOUT);
        assertThat(connectorSettings.cookieHandling()).isEqualTo(HttpCookieHandling.DISABLE);
        assertThat(connectorSettings.redirects()).isEqualTo(HttpRedirects.DONT_FOLLOW);
        assertThat(connectorSettings.sslBundle()).isSameAs(sslBundle);
        assertThat(connectorSettings.inetAddressFilter()).isSameAs(inetAddressFilter);
    }

    private OllamaApiConfig createConfig() {
        return new OllamaApiConfig(
                "http://localhost:11434",
                OLLAMA_CONNECT_TIMEOUT,
                OLLAMA_READ_TIMEOUT
        );
    }

    private HttpClientSettings captureConnectorSettings() {
        ArgumentCaptor<HttpClientSettings> captor = ArgumentCaptor.forClass(HttpClientSettings.class);
        verify(this.connectorBuilder).build(captor.capture());
        return captor.getValue();
    }

    private HttpClientSettings captureRequestFactorySettings() {
        ArgumentCaptor<HttpClientSettings> captor = ArgumentCaptor.forClass(HttpClientSettings.class);
        verify(this.requestFactoryBuilder).build(captor.capture());
        return captor.getValue();
    }
}
