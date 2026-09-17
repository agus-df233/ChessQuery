package cl.chessquery.users;

import cl.chessquery.users.player.LocalPlayerIdentityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Único test que valida el esquema real: aplica Flyway contra PostgreSQL 16 y arranca JPA con
 * ddl-auto=validate. Requiere Docker; en CI corre siempre, en local se salta si no hay Docker.
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean ConnectionFactory rabbitConnectionFactory;

    @Autowired LocalPlayerIdentityResolver resolver;

    @Test
    void migrationsApplyAndJitProvisionWorks() {
        var first = resolver.resolve("entra|abc", Map.of("email", "a@x.cl", "given_name", "Ana", "family_name", "Soto"));
        var again = resolver.resolve("entra|abc", Map.of());
        assertThat(first.playerId()).isEqualTo(again.playerId());
        assertThat(first.organizationId()).isNull();
        assertThat(resolver.find("entra|abc").playerId()).isEqualTo(first.playerId());
    }
}
