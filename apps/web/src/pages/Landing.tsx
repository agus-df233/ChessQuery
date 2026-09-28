import { Link, Navigate } from 'react-router-dom';
import { useAuth } from 'react-oidc-context';
import { Button } from '@chessquery/ui-lib';

/** Portada pública. El login lo hace el IdP (Entra/Google): acá solo hay un botón. */
export const Landing = () => {
  const auth = useAuth();
  if (auth.isAuthenticated) return <Navigate to="/app" replace />;
  return (
    <main className="cq-hero">
      <div>
        <h1>♔ ChessQuery</h1>
        <p className="cq-muted" style={{ maxWidth: 480, margin: '0 auto 20px' }}>
          Torneos presenciales sin papel para tu club, y partidas, ranking y progreso para ti.
        </p>
        <Button size="lg" onClick={() => void auth.signinRedirect()} loading={auth.isLoading}>
          Entrar con mi cuenta
        </Button>
        <p className="cq-muted" style={{ marginTop: 12 }}>Puedes entrar con Google o con tu correo.</p>
        <p style={{ marginTop: 20 }}><Link to="/ranking">Ver el ranking de Chile sin cuenta</Link></p>
      </div>
    </main>
  );
};
