import { useCallback, useEffect, useRef, useState } from 'react';

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

const defaultEventIcon = (eventType: string): string => {
  if (eventType === 'game.invitation') return '⚔️';
  if (eventType.startsWith('game.')) return '♟';
  if (eventType.startsWith('tournament.')) return '🏆';
  if (eventType.startsWith('player.')) return '✅';
  if (eventType.startsWith('registration.')) return '📋';
  if (eventType === 'elo.updated') return '📈';
  if (eventType === 'user.registered') return '👋';
  return '🔔';
};

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

/**
 * Campana de notificaciones in-app, parametrizable por app. Polling cada 8s;
 * las notificaciones nuevas se acumulan en la bandeja (badge de no-leídas). La
 * bandeja arranca vacía en cada sesión (baseline en sessionStorage). Sin toasts
 * emergentes: la única superficie es la campana + su dropdown.
 */
export const NotificationBell = ({
  listNotifications,
  markAllRead,
  resolveLink,
  onNavigate,
  eventIcon,
}: NotificationBellProps) => {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [unread, setUnread] = useState(0);
  const [loading, setLoading] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const baselineRef = useRef<number | null>(null);
  const icon = eventIcon ?? defaultEventIcon;

  const poll = useCallback(async () => {
    try {
      const list = await listNotifications().catch(() => [] as NotificationItem[]);

      if (baselineRef.current == null) {
        const stored = readSessionBaseline();
        if (stored != null) {
          baselineRef.current = stored;
        } else {
          const maxId = list.reduce((m, n) => (n.id > m ? n.id : m), 0);
          baselineRef.current = maxId;
          try { sessionStorage.setItem(SESSION_BASELINE_KEY, String(maxId)); } catch { /* ignore */ }
        }
      }
      const baseline = baselineRef.current ?? 0;
      const sessionList = list.filter((n) => n.id > baseline);
      setItems(sessionList);
      setUnread(sessionList.filter((n) => !n.readAt).length);
    } catch {
      /* backend caído: ignorar */
    }
  }, [listNotifications]);

  useEffect(() => {
    poll();
    const interval = setInterval(poll, POLL_MS);
    return () => clearInterval(interval);
  }, [poll]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const openDropdown = async () => {
    setOpen(true);
    setLoading(true);
    try {
      const list = await listNotifications();
      const baseline = baselineRef.current ?? readSessionBaseline() ?? 0;
      const sessionList = list.filter((n) => n.id > baseline);
      setItems(sessionList);
      if (sessionList.some((n) => !n.readAt)) {
        await markAllRead();
        setUnread(0);
        setItems((prev) => prev.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
      }
    } catch {
      /* ignore */
    } finally {
      setLoading(false);
    }
  };

  return (
    <div ref={wrapperRef} style={{ position: 'fixed', top: 14, right: 18, zIndex: 900 }}>
      <button
        onClick={() => (open ? setOpen(false) : openDropdown())}
        title="Notificaciones"
        aria-label={`Notificaciones${unread > 0 ? ` (${unread} sin leer)` : ''}`}
        style={{
          position: 'relative',
          background: 'var(--surface-2, #15171a)',
          border: '1px solid var(--border, #2a2d27)',
          borderRadius: '50%',
          width: 38, height: 38,
          fontSize: 18,
          cursor: 'pointer',
          color: 'var(--text, #e8ead4)',
        }}
      >
        🔔
        {unread > 0 && (
          <span
            style={{
              position: 'absolute', top: -4, right: -4,
              minWidth: 18, height: 18, padding: '0 5px',
              borderRadius: 10,
              background: 'var(--cq-error, #e05a5a)', color: '#fff',
              fontSize: 12, fontWeight: 700, lineHeight: '18px',
              border: '2px solid var(--bg, #111210)',
            }}
          >
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div
          style={{
            position: 'absolute', right: 0, top: 46,
            width: 340, maxHeight: 460, overflow: 'auto',
            background: 'var(--surface, #1c1f1a)',
            border: '1px solid var(--border, #2a2d27)',
            borderRadius: 10,
            boxShadow: '0 20px 50px rgba(0,0,0,0.4)',
            padding: '6px 0',
          }}
        >
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '8px 14px', borderBottom: '1px solid var(--border, #2a2d27)',
            fontSize: 12, color: 'var(--text-muted)', fontWeight: 600,
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
            const isInvitation = n.eventType === 'game.invitation';
            const go = () => { if (link && onNavigate) { setOpen(false); onNavigate(link); } };
            const rowClickable = !isInvitation && !!link && !!onNavigate;
            return (
              <div
                key={n.id}
                onClick={rowClickable ? go : undefined}
                style={{
                  padding: '10px 14px',
                  borderBottom: '1px solid var(--border, #2a2d27)',
                  background: n.readAt ? 'transparent' : 'var(--accent-dim)',
                  display: 'flex', gap: 10, alignItems: 'flex-start',
                  cursor: rowClickable ? 'pointer' : 'default',
                }}
              >
                <span style={{ fontSize: 16, lineHeight: 1.2, flexShrink: 0 }}>{icon(n.eventType)}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text, #e8ead4)' }}>
                    {n.subject || n.eventType}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 2 }}>
                    {formatRelative(n.createdAt)}
                  </div>
                  {isInvitation && link && onNavigate && (
                    <button
                      onClick={(e) => { e.stopPropagation(); go(); }}
                      style={{
                        marginTop: 8, padding: '6px 12px', borderRadius: 6, border: 'none',
                        background: 'var(--cq-accent, #6abf74)', color: 'var(--cq-input-bg, #0e100d)',
                        fontSize: 12.5, fontWeight: 700, cursor: 'pointer',
                      }}
                    >
                      Aceptar y unirse
                    </button>
                  )}
                  {!isInvitation && link && onNavigate && (
                    <div style={{ marginTop: 4, fontSize: 12, color: 'var(--cq-accent, #6abf74)', fontWeight: 600 }}>
                      Ver detalle →
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
