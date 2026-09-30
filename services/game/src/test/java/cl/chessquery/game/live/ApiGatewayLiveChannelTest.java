package cl.chessquery.game.live;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiGatewayLiveChannelTest {

    @SuppressWarnings("unchecked")
    private static Consumer<PostToConnectionRequest.Builder> anyRequest() {
        return any(Consumer.class);
    }

    @Test
    void enviaYDistingueConexionesQueYaNoExisten() {
        ApiGatewayManagementApiClient client = mock(ApiGatewayManagementApiClient.class);
        ApiGatewayLiveChannel channel = new ApiGatewayLiveChannel(client);

        assertThat(channel.send("c1", "{}")).isTrue();
        verify(client).postToConnection(anyRequest());

        when(client.postToConnection(anyRequest())).thenThrow(GoneException.builder().message("se fue").build());
        assertThat(channel.send("c2", "{}")).as("410: la conexión se borra").isFalse();
    }

    @Test
    void unFalloTransitorioNoBorraLaConexion() {
        ApiGatewayManagementApiClient client = mock(ApiGatewayManagementApiClient.class);
        when(client.postToConnection(anyRequest())).thenThrow(SdkClientException.create("sin red"));
        assertThat(new ApiGatewayLiveChannel(client).send("c3", "{}")).isTrue();
    }
}
