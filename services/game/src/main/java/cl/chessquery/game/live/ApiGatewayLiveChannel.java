package cl.chessquery.game.live;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;

/**
 * Canal de la nube: envía a las conexiones de la API WebSocket de API Gateway ({@code POST @connections/{id}}) con
 * las credenciales del rol de la task (en el Learner Lab, el LabRole tiene execute-api:ManageConnections; ADR-0002).
 */
@Slf4j
public class ApiGatewayLiveChannel implements LiveChannel {

    private final ApiGatewayManagementApiClient client;

    public ApiGatewayLiveChannel(ApiGatewayManagementApiClient client) {
        this.client = client;
    }

    @Override
    public boolean send(String connectionId, String json) {
        try {
            client.postToConnection(r -> r.connectionId(connectionId).data(SdkBytes.fromUtf8String(json)));
            return true;
        } catch (GoneException gone) {
            return false; // el cliente se fue: la conexión se borra
        } catch (SdkException e) {
            log.warn("No se pudo enviar a la conexión {}: {}", connectionId, e.getMessage());
            return true; // fallo transitorio: se conserva; el cliente igual tiene el long polling
        }
    }
}
