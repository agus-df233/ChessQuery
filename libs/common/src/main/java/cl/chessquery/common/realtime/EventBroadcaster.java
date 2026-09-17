package cl.chessquery.common.realtime;

import java.util.Map;

/**
 * Puerto para empujar eventos a clientes conectados (tablero en vivo, torneo en vivo).
 * La implementación vive en el servicio game (WebSocket/STOMP con relay a RabbitMQ);
 * otros servicios publican a través del bus y game reenvía. Es una optimización:
 * la fuente de verdad siempre es la API REST y el cliente re-consulta si pierde eventos.
 */
public interface EventBroadcaster {

    /** @param topic p. ej. {@code game.42} o {@code tournament.7} */
    void broadcast(String topic, String event, Map<String, Object> payload);

    /** Implementación nula para tests y servicios sin realtime. */
    EventBroadcaster NOOP = (topic, event, payload) -> { };
}
