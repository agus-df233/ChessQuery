package cl.chessquery.common.events;

/** Constantes del bus de eventos (ADR-0002: tópico SNS + una cola SQS por consumidor). */
public final class ChessEvents {

    /** Nombre lógico del tópico SNS; el ARN real llega por {@code chessquery.events.topic-arn}. */
    public static final String TOPIC = "chess-events";

    /**
     * Atributo de mensaje SNS con el routing key ({@code eventType}). Las suscripciones SQS filtran
     * por él (filter policy), igual que antes lo hacían los bindings del exchange topic.
     */
    public static final String EVENT_TYPE_ATTRIBUTE = "eventType";

    private ChessEvents() {}
}
