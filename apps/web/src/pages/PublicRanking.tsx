import { Link } from 'react-router-dom';
import { Ranking } from './Ranking';

/**
 * `/ranking` sin login: lo que se comparte por QR en el pitch y en los clubes. Mismas reglas que
 * con sesión: sin datos personales y con el apellido de menores abreviado (Ley 21.719).
 */
export const PublicRanking = () => (
  <main className="cq-public-main">
    <header className="cq-public-header">
      <Link to="/">♔ ChessQuery</Link>
      <Link to="/">Entrar</Link>
    </header>
    <Ranking publicView />
    <p className="cq-muted">
      Fuente: listas oficiales de rating de FIDE y de la federación nacional. Los menores de edad sin
      cuenta se muestran con el apellido abreviado. ¿Eres tú y quieres corregir o quitar tus datos?
      Entra con tu cuenta o escríbenos.
    </p>
  </main>
);
