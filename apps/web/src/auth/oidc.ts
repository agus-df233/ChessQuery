import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';
import { safeReturnPath } from './returnTo';

/**
 * Configuración OIDC de la SPA contra el IdP (Cognito en el Learner Lab o Entra External ID; Google entra federado,
 * así que hay un único emisor). Authorization Code + PKCE, sin secretos en el navegador. Al volver del login se
 * limpian los parámetros de la URL. Las diferencias entre IdP están en provider.ts.
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? '',
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? '',
  redirect_uri: `${window.location.origin}/app`,
  post_logout_redirect_uri: window.location.origin,
  scope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email',
  response_type: 'code',
  automaticSilentRenew: true,
  // sessionStorage: la sesión no sobrevive al cierre de la pestaña ni se comparte entre pestañas.
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  // Al volver del login: a la página que se pidió (p. ej. el QR de una sala) o, si no hay, se limpia la URL de /app
  onSigninCallback: (user) => {
    const target = safeReturnPath(user?.state);
    if (target && target !== window.location.pathname) window.location.replace(target);
    else window.history.replaceState({}, document.title, window.location.pathname);
  },
};
