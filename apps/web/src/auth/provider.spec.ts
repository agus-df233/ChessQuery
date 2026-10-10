import { afterEach, describe, expect, it, vi } from 'vitest';
import type { User } from 'oidc-client-ts';
import type { AuthContextProps } from 'react-oidc-context';
import { bearerOf, cognitoLogoutUrl, googleSignin, isCognito, signOut } from './provider';

const user = { access_token: 'acceso', id_token: 'identidad' } as User;

describe('IdP de la web', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('Entra y el IdP simulado usan el access token; Cognito, el ID token (trae aud y email)', () => {
    expect(bearerOf(user, false)).toBe('acceso');
    expect(bearerOf(user, true)).toBe('identidad');
    expect(bearerOf(null, true)).toBeNull();
    expect(bearerOf(undefined, false)).toBeNull();
  });

  it('va directo a Google con el parámetro de cada IdP', () => {
    expect(googleSignin(false)).toEqual({ extraQueryParams: { domain_hint: 'google' } });
    expect(googleSignin(true)).toEqual({ extraQueryParams: { identity_provider: 'Google' } });
  });

  it('el proveedor sale de VITE_OIDC_PROVIDER (por defecto, Entra)', () => {
    expect(isCognito(undefined)).toBe(false);
    expect(isCognito('entra')).toBe(false);
    expect(isCognito('cognito')).toBe(true);
  });

  it('el logout de Cognito lleva client id y vuelta codificados', () => {
    expect(cognitoLogoutUrl('https://x.auth.us-east-1.amazoncognito.com/logout', 'abc', 'https://app.example/'))
      .toBe('https://x.auth.us-east-1.amazoncognito.com/logout?client_id=abc&logout_uri=https%3A%2F%2Fapp.example%2F');
  });

  it('cerrar sesión: estándar en Entra; en Cognito borra la sesión local y va a su /logout', async () => {
    const auth = { signoutRedirect: vi.fn(), removeUser: vi.fn().mockResolvedValue(undefined) } as unknown as AuthContextProps;
    await signOut(auth);
    expect(auth.signoutRedirect).toHaveBeenCalled();

    vi.stubEnv('VITE_OIDC_PROVIDER', 'cognito');
    vi.stubEnv('VITE_OIDC_LOGOUT_URL', 'https://x.auth.us-east-1.amazoncognito.com/logout');
    vi.stubEnv('VITE_OIDC_CLIENT_ID', 'abc');
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign, origin: 'https://app.example' });
    await signOut(auth);
    expect(auth.removeUser).toHaveBeenCalled();
    expect(assign).toHaveBeenCalledWith(
      'https://x.auth.us-east-1.amazoncognito.com/logout?client_id=abc&logout_uri=https%3A%2F%2Fapp.example');
    vi.unstubAllGlobals();
  });
});
