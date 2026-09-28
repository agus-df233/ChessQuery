package cl.chessquery.common.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del bus contra SNS/SQS reales (LocalStack): un evento publicado llega a la cola cuyo
 * filter policy coincide con su {@code eventType}, como JSON crudo del envelope (raw delivery), y a
 * ninguna otra. Es la misma topología que crean {@code infra/localstack} y el módulo Terraform.
 * LocalStack se fija en 4.14.0: desde 2026.x la imagen exige token de licencia.
 */
@Testcontainers(disabledWithoutDocker = true)
class SnsSqsContractTest {

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    static SnsClient sns;
    static SqsClient sqs;
    static String topicArn;
    static String eloQueue;
    static String ratingQueue;

    @BeforeAll
    static void topology() {
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));
        Region region = Region.of(localstack.getRegion());
        sns = SnsClient.builder().endpointOverride(localstack.getEndpoint()).credentialsProvider(creds).region(region).build();
        sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint()).credentialsProvider(creds).region(region).build();

        topicArn = sns.createTopic(b -> b.name(ChessEvents.TOPIC)).topicArn();
        eloQueue = subscribedQueue("users-elo", "elo.updated");
        ratingQueue = subscribedQueue("users-rating", "rating.updated");
    }

    private static String subscribedQueue(String name, String eventType) {
        String url = sqs.createQueue(b -> b.queueName(name)).queueUrl();
        String arn = sqs.getQueueAttributes(b -> b.queueUrl(url).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        sns.subscribe(b -> b.topicArn(topicArn).protocol("sqs").endpoint(arn).attributes(Map.of(
                "RawMessageDelivery", "true",
                "FilterPolicy", "{\"" + ChessEvents.EVENT_TYPE_ATTRIBUTE + "\":[\"" + eventType + "\"]}")));
        return url;
    }

    private static List<Message> receive(String queueUrl, int waitSeconds) {
        return sqs.receiveMessage(b -> b.queueUrl(queueUrl).waitTimeSeconds(waitSeconds).maxNumberOfMessages(10))
                .messages();
    }

    @Test
    void eventReachesOnlyTheMatchingQueueAsRawEnvelope() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        new EventPublisher(sns, json, topicArn).publish("elo.updated", Map.of("playerId", 7, "newElo", 1510));

        List<Message> elo = receive(eloQueue, 10);
        assertThat(elo).hasSize(1);
        ChessEvent event = json.readValue(elo.get(0).body(), ChessEvent.class);
        assertThat(event.eventType()).isEqualTo("elo.updated");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.payload()).containsEntry("playerId", 7).containsEntry("newElo", 1510);

        assertThat(receive(ratingQueue, 2)).isEmpty();
    }
}
