package cl.chessquery.users;

import cl.chessquery.common.events.ChessEvents;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.common.events.ProcessedEventRepository;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Punta a punta del bus en users: {@link EventPublisher} → SNS → cola SQS con filter policy →
 * {@code @SqsListener} → PostgreSQL. Verifica la deserialización real del envelope (incluido el
 * timestamp) y la idempotencia ante una entrega repetida. Requiere Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@Testcontainers(disabledWithoutDocker = true)
class UsersEventsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Singleton (sin @Container): sigue vivo hasta que se cierra el contexto (@DirtiesContext), así los
     * listeners SQS no quedan haciendo polling contra un contenedor ya detenido. Ryuk lo elimina al salir.
     */
    static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    static {
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) localstack.start();
    }

    /** Misma topología que infra/localstack y el módulo Terraform de mensajería. */
    @DynamicPropertySource
    static void bus(DynamicPropertyRegistry registry) {
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());
        try (SnsClient sns = SnsClient.builder().endpointOverride(localstack.getEndpoint())
                     .credentialsProvider(creds).region(region).build();
             SqsClient sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint())
                     .credentialsProvider(creds).region(region).build()) {
            String topicArn = sns.createTopic(b -> b.name(ChessEvents.TOPIC)).topicArn();
            for (var q : Map.of("users-elo", UsersEvents.ELO_UPDATED, "users-rating", UsersEvents.RATING_UPDATED).entrySet()) {
                String url = sqs.createQueue(b -> b.queueName(q.getKey())).queueUrl();
                String arn = sqs.getQueueAttributes(b -> b.queueUrl(url).attributeNames(QueueAttributeName.QUEUE_ARN))
                        .attributes().get(QueueAttributeName.QUEUE_ARN);
                sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(arn).attributes(Map.of(
                        "RawMessageDelivery", "true",
                        "FilterPolicy", "{\"eventType\":[\"" + q.getValue() + "\"]}")));
            }
            registry.add("chessquery.events.topic-arn", () -> topicArn);
        }
        registry.add("spring.cloud.aws.sns.enabled", () -> "true");
        registry.add("spring.cloud.aws.sqs.enabled", () -> "true");
        registry.add("spring.cloud.aws.endpoint", () -> localstack.getEndpoint().toString());
        registry.add("spring.cloud.aws.region.static", localstack::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", localstack::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", localstack::getSecretKey);
    }

    @MockitoBean JwtDecoder jwtDecoder;

    @Autowired EventPublisher events;
    @Autowired PlayerRepository players;
    @Autowired ProcessedEventRepository processed;

    @Test
    void eloUpdatedTravelsThroughSnsAndSqsIntoThePlayerRating() {
        Player p = players.save(Player.builder().firstName("Tomás").lastName("Rivas").eloPlatformBlitz(1200).build());

        events.publish(UsersEvents.ELO_UPDATED, Map.of("playerId", p.getId(), "oldElo", 1200, "newElo", 1216,
                "delta", 16, "ratingType", "PLATFORM_BLITZ", "gameId", 42));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(players.findById(p.getId()).orElseThrow().getEloPlatformBlitz()).isEqualTo(1216));
        assertThat(processed.count()).isEqualTo(1);
    }
}
