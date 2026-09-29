import { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Shell, type ShellNavItem, type ShellUser } from '@chessquery/ui-lib';
import type { Me } from '../api/types';
import { useMe } from '../api/hooks';

const MENU: [id: string, label: string, href: string, icon: string][] = [
  ['inicio', 'Inicio', '/app', '♔'],
  ['jugadores', 'Jugadores', '/app/jugadores', '🔍'],
  ['ranking', 'Ranking', '/app/ranking', '🏆'],
  ['partidas', 'Partidas', '/app/partidas', '♞'],
  ['amigos', 'Amigos', '/app/amigos', '👥'],
  ['torneos', 'Torneos', '/app/torneos', '🏁'],
  ['perfil', 'Mi perfil', '/app/perfil', '👤'],
];

/** "/app" solo se marca activo en el inicio exacto; el resto también en sus subrutas. */
const isActive = (pathname: string, href: string) => pathname === href || (href !== '/app' && pathname.startsWith(href));

/** Datos del usuario para el pie del menú (sin perfil cargado todavía, no se muestra). */
const shellUser = (me: Me | undefined): ShellUser | undefined => {
  const p = me?.profile;
  if (!p) return undefined;
  return { name: p.displayName ?? `${p.firstName} ${p.lastName}`, email: p.email ?? undefined, role: me.organizer ? 'ORGANIZER' : 'PLAYER' };
};

/**
 * Marco de navegación de la app autenticada. Una sola app para jugador y organizador:
 * la sección "Mi club" aparece siempre (crear el club es lo que te hace organizador).
 */
export const Layout = ({ children }: { children: ReactNode }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const auth = useAuth();
  const me = useMe();
  const organizer = !!me.data?.organizer;

  const items: ShellNavItem[] = [...MENU, ['club', organizer ? 'Mi club' : 'Crear mi club', '/club', '♜'] as const]
    .map(([id, label, href, icon]) => ({ id, label, icon, href, active: isActive(pathname, href), onClick: () => navigate(href) }));
  return (
    <Shell subtitle={organizer ? 'Organizador' : 'Jugador'} items={items} user={shellUser(me.data)}
           onLogout={() => void auth.signoutRedirect()}>
      {children}
    </Shell>
  );
};
