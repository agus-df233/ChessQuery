import { Link, Navigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Button } from '@chessquery/ui-lib';
import { googleSignin, isCognito } from '../auth/provider';

/**
 * Directo a Google, sin pasar por la pantalla del IdP: `domain_hint=google` en Entra External ID ("issuer
 * acceleration") o `identity_provider=Google` en Cognito. Sin hint, Entra ofrece además el correo con código; en
 * Cognito el login es solo con Google.
 */

/** Error devuelto por el IdP (p. ej. el usuario canceló en Google) en palabras simples. */
const loginErrorMessage = (error: Error | undefined): string | null => {
  if (!error) return null;
  return /access_denied|cancel/i.test(error.message)
    ? 'Cancelaste el inicio de sesión. Puedes intentarlo de nuevo cuando quieras.'
    : 'No pudimos iniciar tu sesión. Intenta de nuevo en unos segundos.';
};

/** Portada pública. El login lo hace el IdP (Cognito o Entra External ID, con Google): acá solo hay botones. */
export const Landing = () => {
  const auth = useAuth();
  if (auth.isAuthenticated) return <Navigate to="/app" replace />;
  const error = loginErrorMessage(auth.error);
  return (
    <main className="cq-hero">
      <div>
        <h1>♔ ChessQuery</h1>
        <p className="cq-muted" style={{ maxWidth: 480, margin: '0 auto 20px' }}>
          Torneos presenciales sin papel para tu club, y partidas, ranking y progreso para ti.
        </p>
        {error && <p role="alert" className="cq-error-text">{error}</p>}
        <div className="cq-actions" style={{ justifyContent: 'center' }}>
          <Button size="lg" onClick={() => void auth.signinRedirect(googleSignin())} loading={auth.isLoading}>
            Continuar con Google
          </Button>
          {!isCognito() && (
            <Button size="lg" variant="secondary" onClick={() => void auth.signinRedirect()} disabled={auth.isLoading}>
              Entrar con mi correo
            </Button>
          )}
        </div>
        <p className="cq-muted" style={{ marginTop: 12 }}>
          ChessQuery no guarda contraseñas: tu identidad la verifica Google.
        </p>
        <p style={{ marginTop: 20 }}>
          <Link to="/ranking">Ver el ranking de Chile sin cuenta</Link> · <Link to="/torneos">Torneos</Link>
        </p>
      </div>
    </main>
  );
};
