import { useEffect, useState } from 'react';

/** mm:ss (y décimas bajo 10 s, cuando importan). */
export const formatClock = (ms: number) => {
  const safe = Math.max(0, ms);
  const minutes = Math.floor(safe / 60_000);
  const seconds = Math.floor((safe % 60_000) / 1000);
  const base = `${minutes}:${String(seconds).padStart(2, '0')}`;
  return safe < 10_000 ? `${base}.${Math.floor((safe % 1000) / 100)}` : base;
};

/**
 * Reloj de un jugador. El servidor manda el tiempo restante en cada respuesta; acá solo se descuenta localmente
 * mientras corre, para que se vea fluido. La verdad (y el fin por tiempo) la tiene el servidor.
 */
export const ChessClock = ({ ms, running, label }: { ms: number; running: boolean; label: string }) => {
  const [shown, setShown] = useState(ms);
  useEffect(() => {
    setShown(ms);
    if (!running) return undefined;
    const started = Date.now();
    const timer = setInterval(() => setShown(ms - (Date.now() - started)), 200);
    return () => clearInterval(timer);
  }, [ms, running]);
  return (
    <div className={`cq-clock${running ? ' cq-clock-running' : ''}${shown < 10_000 ? ' cq-clock-low' : ''}`}
         role="timer" aria-label={`Reloj de ${label}`}>
      {formatClock(shown)}
    </div>
  );
};
