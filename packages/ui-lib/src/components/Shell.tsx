import { ReactNode, useCallback, useEffect, useState } from 'react';
import { cn } from '../utils/cn';

export interface ShellNavItem {
  id: string;
  label: string;
  icon?: ReactNode;
  desc?: string;
  href?: string;
  active?: boolean;
  onClick?: () => void;
}

export interface ShellUser {
  name: string;
  role?: string;
  email?: string;
}

export interface ShellProps {
  brand?: ReactNode;
  subtitle?: string;
  items: ShellNavItem[];
  user?: ShellUser;
  onLogout?: () => void;
  /** Control para cambiar de modo (p. ej. Jugador | Organizador): va arriba del menú y en la barra móvil. */
  switcher?: ReactNode;
  children: ReactNode;
}

const BrandMark = ({ brand }: { brand?: ReactNode }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
    <span style={{ fontSize: 24, filter: 'drop-shadow(0 0 12px var(--accent-glow))' }}>♔</span>
    <div
      style={{
        fontFamily: "'Space Grotesk', system-ui, sans-serif",
        fontWeight: 700,
        fontSize: 16,
        letterSpacing: '-0.02em',
        lineHeight: 1,
      }}
    >
      {brand ?? (
        <>
          Chess<span style={{ color: 'var(--accent)' }}>Query</span>
        </>
      )}
    </div>
  </div>
);

const GROTESK = "'Space Grotesk', system-ui, sans-serif";

/** Cierra el menú lateral (móvil) con Escape mientras está abierto. */
const useCloseOnEscape = (open: boolean, close: () => void) => {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, close]);
};

const SidebarBrand = ({ brand, subtitle }: { brand?: ReactNode; subtitle?: string }) => (
  <div style={{ padding: '20px 18px 16px', borderBottom: '1px solid var(--border)' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      <span style={{ fontSize: 26, filter: 'drop-shadow(0 0 12px var(--accent-glow))' }}>♔</span>
      <div>
        <div style={{ fontFamily: GROTESK, fontWeight: 700, fontSize: 16, letterSpacing: '-0.02em', lineHeight: 1 }}>
          {brand ?? <>Chess<span style={{ color: 'var(--accent)' }}>Query</span></>}
        </div>
        {subtitle && (
          <div style={{ fontSize: 9, color: 'var(--text-dim)', letterSpacing: '0.12em', textTransform: 'uppercase', marginTop: 3 }}>
            {subtitle}
          </div>
        )}
      </div>
    </div>
  </div>
);

const NavButton = ({ item, onNavigate }: { item: ShellNavItem; onNavigate: () => void }) => (
  <button
    type="button"
    onClick={() => { item.onClick?.(); onNavigate(); }}
    className={cn('shell-nav-item', item.active && 'active')}
    aria-current={item.active ? 'page' : undefined}
    style={{
      display: 'flex', alignItems: 'center', gap: 10, width: '100%', padding: '9px 10px', borderRadius: 8,
      border: 'none', cursor: 'pointer', marginBottom: 2, transition: 'all 0.13s', textAlign: 'left',
      background: item.active ? 'var(--accent-dim)' : 'transparent',
      color: item.active ? 'var(--accent)' : 'var(--text-muted)',
    }}
  >
    <span style={{ fontSize: 17, width: 22, textAlign: 'center', flexShrink: 0 }}>{item.icon ?? '•'}</span>
    <div style={{ overflow: 'hidden', flex: 1 }}>
      <div style={{ fontFamily: GROTESK, fontWeight: 600, fontSize: 12, lineHeight: 1.2 }}>{item.label}</div>
      {item.desc && (
        <div style={{ fontSize: 10, color: item.active ? 'var(--accent-soft)' : 'var(--text-dim)', marginTop: 1 }}>{item.desc}</div>
      )}
    </div>
    {item.active && (
      <div style={{ marginLeft: 'auto', width: 4, height: 4, borderRadius: '50%', background: 'var(--accent)', flexShrink: 0 }} />
    )}
  </button>
);

const UserFooter = ({ user, onLogout }: { user: ShellUser; onLogout?: () => void }) => (
  <div style={{ padding: 12, borderTop: '1px solid var(--border)' }}>
    <div style={{ display: 'flex', alignItems: 'center', gap: 9, marginBottom: 9 }}>
      <div style={{
        width: 34, height: 34, borderRadius: '50%', flexShrink: 0, background: 'var(--accent-dim)',
        border: '2px solid var(--accent-outline)', display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontFamily: GROTESK, fontWeight: 700, fontSize: 13, color: 'var(--accent)',
      }}>
        {user.name.charAt(0).toUpperCase()}
      </div>
      <div style={{ overflow: 'hidden', flex: 1 }}>
        <div style={{ fontFamily: GROTESK, fontWeight: 600, fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {user.name}
        </div>
        {user.role && <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>{user.role}</div>}
      </div>
    </div>
    {onLogout && (
      <button type="button" className="btn btn-ghost" onClick={onLogout}
              style={{ width: '100%', justifyContent: 'center', fontSize: 12, padding: 6 }}>
        ← Cerrar sesión
      </button>
    )}
  </div>
);

export const Shell = ({ brand, subtitle, items, user, onLogout, switcher, children }: ShellProps) => {
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);
  useCloseOnEscape(open, close);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#contenido">Saltar al contenido</a>
      {/* Topbar móvil con hamburguesa (oculta en desktop vía CSS) */}
      <header className="app-topbar">
        <button type="button" className="hamburger" aria-label="Abrir menú" aria-expanded={open} onClick={() => setOpen(true)}>
          ☰
        </button>
        <BrandMark brand={brand} />
        {switcher && <div className="app-topbar-switcher">{switcher}</div>}
      </header>

      {open && <div className="app-overlay" onClick={close} aria-hidden="true" />}

      <aside className={cn('sidebar', open && 'open')}>
        <SidebarBrand brand={brand} subtitle={subtitle} />
        {switcher && <div className="sidebar-switcher">{switcher}</div>}
        <nav aria-label="Navegación principal" style={{ flex: 1, padding: '10px' }}>
          <div style={{
            fontSize: 9, fontWeight: 700, letterSpacing: '0.12em', textTransform: 'uppercase',
            color: 'var(--text-dim)', padding: '6px 8px 8px',
          }}>
            Navegación
          </div>
          {/* Al navegar se cierra el drawer (móvil). */}
          {items.map((item) => <NavButton key={item.id} item={item} onNavigate={close} />)}
        </nav>
        {user && <UserFooter user={user} onLogout={onLogout} />}
      </aside>

      <main id="contenido" tabIndex={-1} className="main fade-up">{children}</main>
    </div>
  );
};
