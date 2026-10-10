import { ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Shell, type ShellNavItem, type ShellUser } from '@chessquery/ui-lib';
import type { Me } from '../api/types';
import { useMe } from '../api/hooks';
import { signOut } from '../auth/provider';
import { useConfirm } from './useConfirm';

type MenuEntry = [id: string, label: string, href: string, icon: string];

/** Íconos monocromos con piezas de ajedrez (un solo estilo en todo el menú). */
const PLAYER_MENU: MenuEntry[] = [
  ['inicio', 'Inicio', '/app', '♔'],
  ['partidas', 'Partidas', '/app/partidas', '♞'],
  ['torneos', 'Torneos', '/app/torneos', '♜'],
  ['salas', 'Salas', '/app/salas', '♟'],
  ['jugadores', 'Jugadores', '/app/jugadores', '♙'],
  ['ranking', 'Ranking', '/app/ranking', '♕'],
  ['amigos', 'Amigos', '/app/amigos', '♘'],
  ['perfil', 'Mi perfil', '/app/perfil', '♗'],
];

const ORGANIZER_MENU: MenuEntry[] = [
  ['club', 'Mi club', '/club', '♖'],
  ['club-torneos', 'Torneos del club', '/club/torneos', '♜'],
  ['club-salas', 'Salas de juego', '/club/salas', '♟'],
];

/** El modo sale de la ruta: todo lo que vive bajo /club es del organizador. */
export const organizerMode = (pathname: string) => pathname === '/club' || pathname.startsWith('/club/');

/** Menú según el modo; quien aún no tiene club ve «Crear mi club» al final del menú del jugador. */
export const menuFor = (organizer: boolean, inOrganizerMode: boolean): MenuEntry[] => {
  if (organizer && inOrganizerMode) return ORGANIZER_MENU;
  return organizer ? PLAYER_MENU : [...PLAYER_MENU, ['club', 'Crear mi club', '/club', '♖']];
};

/** Jugador | Organizador: enlaces reales (navegables con teclado), el activo con aria-current. */
const ModeSwitch = ({ organizerActive }: { organizerActive: boolean }) => (
  <div className="mode-switch" role="group" aria-label="Modo">
    <Link to="/app" aria-current={organizerActive ? undefined : 'page'}>Jugador</Link>
    <Link to="/club" aria-current={organizerActive ? 'page' : undefined}>Organizador</Link>
  </div>
);

/** Las raíces de cada modo (/app, /club) solo se marcan en su ruta exacta; el resto también en sus subrutas. */
const ROOTS = ['/app', '/club'];
export const isActive = (pathname: string, href: string) =>
  pathname === href || (!ROOTS.includes(href) && pathname.startsWith(`${href}/`));

/** Datos del usuario para el pie del menú (sin perfil cargado todavía, no se muestra). */
const shellUser = (me: Me | undefined): ShellUser | undefined => {
  const p = me?.profile;
  if (!p) return undefined;
  return { name: p.displayName ?? `${p.firstName} ${p.lastName}`, email: p.email ?? undefined, role: me.organizer ? 'Organizador' : 'Jugador' };
};

/**
 * Marco de navegación de la app autenticada. Una sola app con dos modos: jugador (/app) y organizador (/club). Quien
 * tiene club cambia de modo con el selector de arriba; quien no, ve «Crear mi club» (crearlo es lo que lo hace
 * organizador).
 */
export const Layout = ({ children }: { children: ReactNode }) => {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const auth = useAuth();
  const me = useMe();
  const organizer = !!me.data?.organizer;
  const inOrganizerMode = organizerMode(pathname);

  const { ask, dialog } = useConfirm();
  const logout = async () => {
    if (await ask({ title: '¿Cerrar sesión?', message: 'Para volver a entrar usarás tu cuenta de Google.', confirmLabel: 'Cerrar sesión' })) {
      void signOut(auth);
    }
  };
  const items: ShellNavItem[] = menuFor(organizer, inOrganizerMode)
    .map(([id, label, href, icon]) => ({ id, label, icon, href, active: isActive(pathname, href), onClick: () => navigate(href) }));
  return (
    <Shell subtitle={inOrganizerMode ? 'Organizador' : 'Jugador'} items={items} user={shellUser(me.data)}
           switcher={organizer ? <ModeSwitch organizerActive={inOrganizerMode} /> : undefined}
           onLogout={() => void logout()}>
      {children}
      {dialog}
    </Shell>
  );
};
