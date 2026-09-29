import { type RefObject, useCallback, useEffect, useRef, useState } from 'react';

export interface NotificationItem {
  id: number;
  eventType: string;
  subject: string;
  payload: string | null;
  createdAt: string | null;
  readAt: string | null;
}

export interface NotificationBellProps {
  /** Trae las notificaciones in-app del usuario (ordenadas desc por id). */
  listNotifications: () => Promise<NotificationItem[]>;
  /** Marca todas como leídas. */
  markAllRead: () => Promise<void>;
  /** Ruta destino si la notificación es accionable; null si no. */
  resolveLink?: (n: NotificationItem) => string | null;
  /** Navegación SPA al accionar una notificación con link. */
  onNavigate?: (path: string) => void;
  /** Emoji por tipo de evento (override del default). */
  eventIcon?: (eventType: string) => string;
}

const POLL_MS = 8_000;

// Baseline de sesión: id máximo al iniciar la sesión. Solo se muestran/cuentan
// las posteriores. Vive en sessionStorage → sesión nueva = bandeja vacía; se
// limpia en el logout (las apps borran la key).
const SESSION_BASELINE_KEY = 'cq-notif-baseline';
const readSessionBaseline = (): number | null => {
  try {
    const v = sessionStorage.getItem(SESSION_BASELINE_KEY);
    return v != null ? Number(v) : null;
  } catch {
    return null;
  }
};

// Ícono por tipo de evento: primero coincidencias exactas, luego por prefijo; si nada calza, campana.
const EXACT_ICONS: Record<string, string> = { 'game.invitation': '⚔️', 'elo.updated': '📈', 'user.registered': '👋' };
const PREFIX_ICONS: [string, string][] = [['game.', '♟'], ['tournament.', '🏆'], ['player.', '✅'], ['registration.', '📋']];

const defaultEventIcon = (eventType: string): string =>
  EXACT_ICONS[eventType] ?? PREFIX_ICONS.find(([prefix]) => eventType.startsWith(prefix))?.[1] ?? '🔔';

const formatRelative = (iso: string | null): string => {
  if (!iso) return '';
  const ms = Date.now() - new Date(iso).getTime();
  if (Number.isNaN(ms) || ms < 0) return '';
  const mins = Math.floor(ms / 60_000);
  if (mins < 1) return 'ahora';
  if (mins < 60) return `hace ${mins} min`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `hace ${hrs} h`;
  return `hace ${Math.floor(hrs / 24)} d`;
};

/** Id máximo visto al iniciar la sesión: solo cuenta lo posterior. Se guarda para sobrevivir recargas. */
const ensureSessionBaseline = (ref: { current: number | null }, list: NotificationItem[]): number => {
  if (ref.current == null) {
    ref.current = readSessionBaseline() ?? list.reduce((m, n) => (n.id > m ? n.id : m), 0);
    try { sessionStorage.setItem(SESSION_BASELINE_KEY, String(ref.current)); } catch { /* sin storage: solo memoria */ }
  }
  return ref.current;
};

/** Sondea la bandeja cada POLL_MS y expone solo las notificaciones de esta sesión. */
const useSessionNotifications = (listNotifications: () => Promise<NotificationItem[]>) => {
  const [items, setItems] = useState<NotificationItem[]>([]);
  const baselineRef = useRef<number | null>(null);

  const load = useCallback(async () => {
    const list = await listNotifications();
    const sessionList = list.filter((n) => n.id > ensureSessionBaseline(baselineRef, list));
    setItems(sessionList);
    return sessionList;
  }, [listNotifications]);

  useEffect(() => {
    const poll = () => { load().catch(() => { /* backend caído: se reintenta en el próximo ciclo */ }); };
    poll();
    const interval = setInterval(poll, POLL_MS);
    return () => clearInterval(interval);
  }, [load]);

  return { items, setItems, load };
};

/** Cierra el desplegable al hacer clic fuera de `ref` mientras está abierto. */
const useDismissOnOutsideClick = (ref: RefObject<HTMLDivElement | null>, open: boolean, close: () => void) => {
  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open, ref, close]);
};

const BellButton = ({ unread, onClick }: { unread: number; onClick: () => void }) => (
  <button
    onClick={onClick}
    title="Notificaciones"
    aria-label={`Notificaciones${unread > 0 ? ` (${unread} sin leer)` : ''}`}
    style={{
      position: 'relative', background: 'var(--surface-2, #15171a)', border: '1px solid var(--border, #2a2d27)',
      borderRadius: '50%', width: 38, height: 38, fontSize: 18, cursor: 'pointer', color: 'var(--text, #e8ead4)',
    }}
  >
    🔔
    {unread > 0 && (
      <span style={{
        position: 'absolute', top: -4, right: -4, minWidth: 18, height: 18, padding: '0 5px', borderRadius: 10,
        background: 'var(--cq-error, #e05a5a)', color: '#fff', fontSize: 12, fontWeight: 700, lineHeight: '18px',
        border: '2px solid var(--bg, #111210)',
      }}>
        {unread > 9 ? '9+' : unread}
      </span>
    )}
  </button>
);

const InvitationButton = ({ go }: { go: () => void }) => (
  <button onClick={(e) => { e.stopPropagation(); go(); }} style={{
    marginTop: 8, padding: '6px 12px', borderRadius: 6, border: 'none', background: 'var(--cq-accent, #6abf74)',
    color: 'var(--cq-input-bg, #0e100d)', fontSize: 12.5, fontWeight: 700, cursor: 'pointer',
  }}>
    Aceptar y unirse
  </button>
);

const rowStyle = (unread: boolean, clickable: boolean) => ({
  padding: '10px 14px', borderBottom: '1px solid var(--border, #2a2d27)', display: 'flex', gap: 10,
  alignItems: 'flex-start', background: unread ? 'var(--accent-dim)' : 'transparent', cursor: clickable ? 'pointer' : 'default',
});

/** Una notificación: las invitaciones a partida tienen botón propio; el resto navega al hacer clic. */
const NotificationRow = ({ n, link, icon, go }: {
  n: NotificationItem; link: string | null; icon: string; go: (() => void) | null;
}) => {
  const isInvitation = n.eventType === 'game.invitation';
  const rowAction = !isInvitation && link ? go : null;
  return (
    <div onClick={rowAction ?? undefined} style={rowStyle(!n.readAt, !!rowAction)}>
      <span style={{ fontSize: 16, lineHeight: 1.2, flexShrink: 0 }}>{icon}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text, #e8ead4)' }}>{n.subject || n.eventType}</div>
        <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>{formatRelative(n.createdAt)}</div>
        {isInvitation && go && <InvitationButton go={go} />}
        {rowAction && (
          <div style={{ marginTop: 4, fontSize: 12, color: 'var(--cq-accent, #6abf74)', fontWeight: 600 }}>Ver detalle →</div>
        )}
      </div>
    </div>
  );
};

/**
 * Campana de notificaciones in-app, parametrizable por app. Polling cada 8s;
 * las notificaciones nuevas se acumulan en la bandeja (badge de no-leídas). La
 * bandeja arranca vacía en cada sesión (baseline en sessionStorage). Sin toasts
 * emergentes: la única superficie es la campana + su dropdown.
 */
export const NotificationBell = ({ listNotifications, markAllRead, resolveLink, onNavigate, eventIcon }: NotificationBellProps) => {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const { items, setItems, load } = useSessionNotifications(listNotifications);
  const close = useCallback(() => setOpen(false), []);
  useDismissOnOutsideClick(wrapperRef, open, close);
  const icon = eventIcon ?? defaultEventIcon;
  const unread = items.filter((n) => !n.readAt).length;

  /** Al abrir se recarga y, si hay no leídas, se marcan todas como leídas. */
  const openDropdown = async () => {
    setOpen(true);
    setLoading(true);
    try {
      if ((await load()).some((n) => !n.readAt)) {
        await markAllRead();
        setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
      }
    } catch {
      /* sin conexión: se muestra lo que ya había */
    } finally {
      setLoading(false);
    }
  };

  const goTo = (link: string | null) => (link && onNavigate ? () => { setOpen(false); onNavigate(link); } : null);

  return (
    <div ref={wrapperRef} style={{ position: 'fixed', top: 14, right: 18, zIndex: 900 }}>
      <BellButton unread={unread} onClick={() => (open ? setOpen(false) : void openDropdown())} />
      {open && (
        <div style={{
          position: 'absolute', right: 0, top: 46, width: 340, maxHeight: 460, overflow: 'auto',
          background: 'var(--surface, #1c1f1a)', border: '1px solid var(--border, #2a2d27)', borderRadius: 10,
          boxShadow: '0 20px 50px rgba(0,0,0,0.4)', padding: '6px 0',
        }}>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 14px',
            borderBottom: '1px solid var(--border, #2a2d27)', fontSize: 12, color: 'var(--text-muted)', fontWeight: 600,
          }}>
            <span>NOTIFICACIONES</span>
            {loading && <span>…</span>}
          </div>
          {items.length === 0 && !loading && (
            <div style={{ padding: '24px 16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: 13 }}>
              Sin notificaciones aún.
            </div>
          )}
          {items.map((n) => {
            const link = resolveLink?.(n) ?? null;
            return <NotificationRow key={n.id} n={n} link={link} icon={icon(n.eventType)} go={goTo(link)} />;
          })}
        </div>
      )}
    </div>
  );
};
