package cl.chessquery.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de prueba que avanza a mano para simular relojes de ajedrez sin esperar. */
class MutableClock extends Clock {

    private Instant now = Instant.parse("2026-10-01T15:00:00Z");

    void advance(Duration d) { now = now.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return now; }
}
