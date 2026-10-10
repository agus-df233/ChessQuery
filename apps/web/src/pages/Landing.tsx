import { Link, Navigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Button, Card } from '@chessquery/ui-lib';
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

interface RoleAccess { key: string; eyebrow: string; title: string; icon: string; points: string[]; cta: string; to: string }

/** Los dos caminos de entrada. Ambos usan el mismo login; cambia adónde vuelve la persona (state → returnTo). */
const ROLES: RoleAccess[] = [
  {
    key: 'jugador', eyebrow: 'Para jugadores', title: 'Juega y sigue tu progreso', icon: '♞', to: '/app', cta: 'Entrar como jugador',
    points: ['Partidas 1 vs 1 con reloj y ELO por ritmo', 'Tus ratings de la Federación, FIDE, Lichess y Chess.com juntos',
      'Desafía a un amigo con un enlace o un QR'],
  },
  {
    key: 'organizador', eyebrow: 'Para organizadores', title: 'Tu club y tus torneos, sin papel', icon: '♜', to: '/club',
    cta: 'Entrar como organizador',
    points: ['Roster por CSV e inscripciones con cupo y aprobación', 'Acreditación con QR, rondas y TRF para homologar',
      'Pantalla en vivo para la sala y salas de clase'],
  },
];

const RoleCard = ({ role, onEnter, loading }: { role: RoleAccess; onEnter: () => void; loading: boolean }) => (
  <article aria-labelledby={`rol-${role.key}`}>
    <Card className="cq-role-card">
      <p className="cq-eyebrow">{role.eyebrow}</p>
      <h2 id={`rol-${role.key}`}><span aria-hidden="true">{role.icon}</span> {role.title}</h2>
      <ul>{role.points.map((point) => <li key={point}>{point}</li>)}</ul>
      <Button size="lg" fullWidth onClick={onEnter} loading={loading}>{role.cta} con Google</Button>
    </Card>
  </article>
);

/** Portada pública: qué es ChessQuery y por dónde entra cada rol. El login lo hace el IdP (Cognito o Entra, con Google). */
export const Landing = () => {
  const auth = useAuth();
  if (auth.isAuthenticated) return <Navigate to="/app" replace />;
  const error = loginErrorMessage(auth.error);
  const enter = (to: string) => void auth.signinRedirect({ ...googleSignin(), state: to });
  return (
    <main className="cq-landing">
      <header className="cq-landing-hero">
        <p className="cq-eyebrow">Plataforma chilena de ajedrez</p>
        <h1>♔ ChessQuery</h1>
        <p className="cq-landing-lead">Compite, organiza y juega en tiempo real: partidas en vivo, torneos completos y tus ratings siempre al día.</p>
        {error && <p role="alert" className="cq-error-text">{error}</p>}
      </header>
      <section className="cq-role-cards" aria-label="Elige cómo entrar">
        {ROLES.map((role) => <RoleCard key={role.key} role={role} onEnter={() => enter(role.to)} loading={auth.isLoading} />)}
      </section>
      {!isCognito() && (
        <p className="cq-landing-alt">
          <Button variant="secondary" onClick={() => void auth.signinRedirect()} disabled={auth.isLoading}>Entrar con mi correo</Button>
        </p>
      )}
      <p className="cq-muted cq-landing-note">ChessQuery no guarda contraseñas: tu identidad la verifica Google. Si ya tienes club, cambias de modo cuando quieras.</p>
      <nav className="cq-landing-public" aria-label="Sin cuenta">
        <span className="cq-muted">Sin cuenta:</span> <Link to="/ranking">Ranking de Chile</Link> · <Link to="/torneos">Torneos y calendario</Link>
      </nav>
    </main>
  );
};
