import { WebStorageStateStore } from 'oidc-client-ts';
import type { AuthProviderProps } from 'react-oidc-context';

/**
 * Configuración OIDC de la SPA contra Entra External ID (Google entra federado por el
 * mismo tenant, así que hay un único emisor). Authorization Code + PKCE, sin secretos en
 * el navegador. Al volver del login se limpian los parámetros de la URL.
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
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};
