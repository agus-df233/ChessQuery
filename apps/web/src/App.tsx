import { Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { RequireAuth } from './auth/guards';
import { Landing } from './pages/Landing';
import { Dashboard } from './pages/Dashboard';
import { ProfileEdit } from './pages/ProfileEdit';
import { PlayerDetail, PlayerSearch } from './pages/Players';
import { Ranking } from './pages/Ranking';
import { PublicRanking } from './pages/PublicRanking';
import { Friends } from './pages/Friends';
import { Club } from './pages/Club';

/** Rutas: `/` y `/ranking` públicas; `/app/**` jugador; `/club` organizador (o su creación). */
export const App = () => (
  <Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/ranking" element={<PublicRanking />} />
    <Route path="/app/*" element={<RequireAuth><Layout><Routes>
      <Route index element={<Dashboard />} />
      <Route path="perfil" element={<ProfileEdit />} />
      <Route path="jugadores" element={<PlayerSearch />} />
      <Route path="jugadores/:id" element={<PlayerDetail />} />
      <Route path="ranking" element={<Ranking />} />
      <Route path="amigos" element={<Friends />} />
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes></Layout></RequireAuth>} />
    <Route path="/club" element={<RequireAuth><Layout><Club /></Layout></RequireAuth>} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);
