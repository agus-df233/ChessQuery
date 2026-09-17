import { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Shell, type ShellNavItem } from '@chessquery/ui-lib';
import { useMe } from '../api/hooks';

/**
 * Marco de navegación de la app autenticada. Una sola app para jugador y organizador:
 * la sección "Mi club" aparece siempre (crear el club es lo que te hace organizador).
 */
export const Layout = ({ children }: { children: ReactNode }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const auth = useAuth();
  const me = useMe();

  const item = (id: string, label: string, href: string, icon: string): ShellNavItem => ({
    id, label, icon, href, active: pathname === href || (href !== '/app' && pathname.startsWith(href)),
    onClick: () => navigate(href),
  });
  const items: ShellNavItem[] = [
    item('inicio', 'Inicio', '/app', '♔'),
    item('jugadores', 'Jugadores', '/app/jugadores', '🔍'),
    item('ranking', 'Ranking', '/app/ranking', '🏆'),
    item('amigos', 'Amigos', '/app/amigos', '👥'),
    item('perfil', 'Mi perfil', '/app/perfil', '👤'),
    item('club', me.data?.organizer ? 'Mi club' : 'Crear mi club', '/club', '♜'),
  ];
  const p = me.data?.profile;
  return (
    <Shell
      subtitle={me.data?.organizer ? 'Organizador' : 'Jugador'}
      items={items}
      user={p ? { name: p.displayName ?? `${p.firstName} ${p.lastName}`, email: p.email ?? undefined, role: me.data?.organizer ? 'ORGANIZER' : 'PLAYER' } : undefined}
      onLogout={() => void auth.signoutRedirect()}
    >
      {children}
    </Shell>
  );
};
