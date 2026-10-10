import type { User } from 'oidc-client-ts';
import type { AuthContextProps } from 'react-oidc-context';

/**
 * Diferencias entre los IdP que soporta la web (todo lo demás es OIDC estándar, en oidc.ts):
 *
 * - **Entra External ID** (y el IdP simulado de desarrollo): el access token trae `aud` y el correo; se envía ese.
 *   `domain_hint=google` salta directo a Google y el cierre de sesión es el estándar.
 * - **Amazon Cognito** (`VITE_OIDC_PROVIDER=cognito`, el login del Learner Lab): solo el **ID token** trae `aud`,
 *   `email` y el nombre (el access token lleva `client_id`), así que se envía el ID token, como hace el autorizador de
 *   Cognito de API Gateway. `identity_provider=Google` salta directo a Google, y el cierre de sesión es su `/logout`.
 */
const env = import.meta.env;

export const isCognito = (provider: string | undefined = env.VITE_OIDC_PROVIDER) => provider === 'cognito';

/** El token que va como `Authorization: Bearer` (y en el WebSocket). */
export const bearerOf = (user: User | null | undefined, cognito = isCognito()): string | null =>
  (cognito ? user?.id_token : user?.access_token) ?? null;

/** Parámetros para ir directo a Google sin pasar por la pantalla del IdP. */
export const googleSignin = (cognito = isCognito()): { extraQueryParams: Record<string, string> } => ({
  extraQueryParams: cognito ? { identity_provider: 'Google' } : { domain_hint: 'google' },
});

/** `/logout` de Cognito: borra su sesión y vuelve a la portada (la URL debe estar en logout_urls del cliente). */
export const cognitoLogoutUrl = (logoutUrl: string, clientId: string, returnTo: string) =>
  `${logoutUrl}?client_id=${encodeURIComponent(clientId)}&logout_uri=${encodeURIComponent(returnTo)}`;

/** Marca para avisar «Cerraste sesión» al volver a la portada (sobrevive al paso por el IdP en la misma pestaña). */
export const SIGNED_OUT_FLAG = 'cq-cerro-sesion';

/** Cierra la sesión en la web y en el IdP. */
export const signOut = async (auth: AuthContextProps) => {
  try { sessionStorage.setItem(SIGNED_OUT_FLAG, '1'); } catch { /* sin aviso al volver */ }
  if (!isCognito()) return auth.signoutRedirect();
  await auth.removeUser();
  window.location.assign(cognitoLogoutUrl(env.VITE_OIDC_LOGOUT_URL ?? '', env.VITE_OIDC_CLIENT_ID ?? '', window.location.origin));
};
