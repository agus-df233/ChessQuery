/**
 * Puente entre la sesión OIDC (React) y el cliente HTTP (axios, fuera de React).
 * El AuthProvider deja acá el access token vigente; el interceptor lo lee en cada request.
 */
let accessToken: string | null = null;

export const tokenStore = {
  set(token: string | null) {
    accessToken = token;
  },
  get(): string | null {
    return accessToken;
  },
};
