package cl.chessquery.game.live;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;

import java.net.URI;
import java.util.concurrent.Executor;

/**
 * Elige el canal en vivo con {@code chessquery.live.mode}:
 * <ul>
 *   <li>{@code local} (por defecto): WebSocket nativo en {@code /ws} (desarrollo, E2E).</li>
 *   <li>{@code apigateway}: la nube; API Gateway WebSocket llama a {@code /internal/ws/*} y las respuestas se envían a
 *       {@code chessquery.live.management-endpoint}.</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class LiveConfig {

    /** Los envíos en vivo corren fuera del hilo de la jugada: una red lenta no la demora. */
    @Bean
    public Executor liveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("en-vivo-");
        executor.initialize();
        return executor;
    }

    @Configuration
    @ConditionalOnProperty(name = "chessquery.live.mode", havingValue = "apigateway")
    static class ApiGatewayMode {
        @Bean
        public LiveChannel apiGatewayLiveChannel(@Value("${chessquery.live.management-endpoint}") String endpoint) {
            return new ApiGatewayLiveChannel(ApiGatewayManagementApiClient.builder().endpointOverride(URI.create(endpoint)).build());
        }
    }

    @Configuration
    @EnableWebSocket
    @ConditionalOnProperty(name = "chessquery.live.mode", havingValue = "local", matchIfMissing = true)
    static class LocalMode {
        @Bean
        public LocalSessions localSessions() {
            return new LocalSessions();
        }

        @Bean
        public WebSocketConfigurer localWebSocket(LiveConnections live, LocalSessions sessions) {
            return registry -> registry.addHandler(new LocalWebSocketHandler(live, sessions), "/ws").setAllowedOrigins("*");
        }
    }
}
