import { fireEvent, render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import type { Profile } from './api/types';
import { FederationCard } from './components/FederationCard';
import { usersApi } from './api/users';

vi.mock('./api/users', () => ({ usersApi: { linkFederation: vi.fn(), claim: vi.fn(), claimSuggestions: vi.fn() } }));

const profile = { federationId: null, ratings: { national: null } } as unknown as Profile;
const renderCard = (p: Profile) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
    <FederationCard profile={p} />
  </QueryClientProvider>,
);

describe('FederationCard', () => {
  it('si la ficha ya existe sin dueño pide el RUT y la reclama', async () => {
    vi.mocked(usersApi.linkFederation).mockRejectedValue({ status: 409, error: 'CLAIM_REQUIRED', message: 'Reclámala', timestamp: '' });
    vi.mocked(usersApi.claim).mockResolvedValue(profile);
    renderCard(profile);
    fireEvent.change(screen.getByLabelText('Mi id federativo'), { target: { value: '5555' } });
    fireEvent.click(screen.getByRole('button', { name: 'Vincular mi ficha' }));
    fireEvent.change(await screen.findByLabelText('RUT'), { target: { value: '22.222.222-2' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reclamar mi ficha' }));
    await vi.waitFor(() => expect(usersApi.claim).toHaveBeenCalledWith({ federationId: '5555', rut: '22.222.222-2' }));
  });

  it('muestra la ficha vinculada y su ELO nacional', () => {
    renderCard({ ...profile, federationId: '738', ratings: { national: 1650 } } as unknown as Profile);
    expect(screen.getByText('738')).toBeInTheDocument();
    expect(screen.getByText('ELO nacional 1650')).toBeInTheDocument();
  });
});
