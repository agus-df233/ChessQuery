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
import { PublicTournamentDetail, PublicTournaments } from './pages/PublicTournaments';
import { TournamentPage, Tournaments } from './pages/Tournaments';
import { Games } from './pages/Games';
import { GamePage } from './pages/GamePage';
import { OrganizerTournament, OrganizerTournaments } from './pages/OrganizerTournaments';
import { OrganizerRoom, OrganizerRooms } from './pages/OrganizerRooms';
import { RoomPage, Rooms } from './pages/Rooms';
import { OpenChallengePage } from './pages/OpenChallengePage';
import { OrganizerCheckin } from './pages/OrganizerCheckin';
import { OrganizerCredentials } from './pages/OrganizerCredentials';

/** Rutas: `/`, `/ranking` y `/torneos` públicas; `/app/**` jugador; `/club/**` organizador (o su creación). */
export const App = () => (
  <Routes>
    <Route path="/" element={<Landing />} />
    <Route path="/ranking" element={<PublicRanking />} />
    <Route path="/torneos" element={<PublicTournaments />} />
    <Route path="/torneos/:id" element={<PublicTournamentDetail />} />
    <Route path="/app/*" element={<RequireAuth><Layout><Routes>
      <Route index element={<Dashboard />} />
      <Route path="perfil" element={<ProfileEdit />} />
      <Route path="jugadores" element={<PlayerSearch />} />
      <Route path="jugadores/:id" element={<PlayerDetail />} />
      <Route path="ranking" element={<Ranking />} />
      <Route path="amigos" element={<Friends />} />
      <Route path="partidas" element={<Games />} />
      <Route path="partidas/:id" element={<GamePage />} />
      <Route path="desafio/:token" element={<OpenChallengePage />} />
      <Route path="torneos" element={<Tournaments />} />
      <Route path="torneos/:id" element={<TournamentPage />} />
      <Route path="salas" element={<Rooms />} />
      <Route path="salas/:id" element={<RoomPage />} />
      <Route path="*" element={<Navigate to="/app" replace />} />
    </Routes></Layout></RequireAuth>} />
    <Route path="/club" element={<RequireAuth><Layout><Club /></Layout></RequireAuth>} />
    <Route path="/club/torneos" element={<RequireAuth><Layout><OrganizerTournaments /></Layout></RequireAuth>} />
    <Route path="/club/torneos/:id" element={<RequireAuth><Layout><OrganizerTournament /></Layout></RequireAuth>} />
    <Route path="/club/torneos/:id/acreditacion" element={<RequireAuth><Layout><OrganizerCheckin /></Layout></RequireAuth>} />
    <Route path="/club/torneos/:id/credenciales" element={<RequireAuth><Layout><OrganizerCredentials /></Layout></RequireAuth>} />
    <Route path="/club/salas" element={<RequireAuth><Layout><OrganizerRooms /></Layout></RequireAuth>} />
    <Route path="/club/salas/:id" element={<RequireAuth><Layout><OrganizerRoom /></Layout></RequireAuth>} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);
