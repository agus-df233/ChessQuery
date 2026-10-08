import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { axe } from 'vitest-axe';
import * as axeMatchers from 'vitest-axe/matchers';

expect.extend(axeMatchers);

/**
 * Asistente de bienvenida: caja negra del recorrido (pasos, omitir, vínculo federativo) con axe en cada paso, y
 * valores límite de los campos opcionales con las reglas puras de welcome.ts.
 */
vi.mock('./api/users', () => ({
  usersApi: { updateMyProfile: vi.fn().mockResolvedValue({}), linkFederation: vi.fn().mockResolvedValue({}) },
}));

import { usersApi } from './api/users';
import { WelcomeWizard } from './components/welcome/WelcomeWizard';
import { EMPTY_ANSWERS, accountErrors, examplesOf, welcomeRequest } from './components/welcome/welcome';
import { presetFor } from './lib/timeControl';

const renderWizard = (onClose = vi.fn()) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const view = render(<QueryClientProvider client={qc}><MemoryRouter><WelcomeWizard onClose={onClose} /></MemoryRouter></QueryClientProvider>);
  return { ...view, onClose };
};
const next = () => fireEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

beforeEach(() => vi.clearAllMocks());

describe('bienvenida', () => {
  it('recorre los 3 pasos, guarda solo lo completado y vincula la ficha', async () => {
    const { container } = renderWizard();
    expect(screen.getByText(/paso 1 de 3/)).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
    fireEvent.click(screen.getByRole('radio', { name: /Rápida/ }));
    next();

    expect(await axe(container)).toHaveNoViolations();
    fireEvent.change(screen.getByLabelText('Id federativo (opcional)'), { target: { value: ' 738 ' } });
    fireEvent.change(screen.getByLabelText('Lichess (opcional)'), { target: { value: 'ana_soto' } });
    next();

    expect(await axe(container)).toHaveNoViolations();
    fireEvent.change(screen.getByLabelText('Región'), { target: { value: 'Valparaíso' } });
    fireEvent.click(screen.getByRole('button', { name: 'Terminar' }));

    await screen.findByText('¡Listo! Tu perfil quedó configurado');
    expect(usersApi.updateMyProfile).toHaveBeenCalledWith({ welcomed: true, preferredCategory: 'RAPID', region: 'Valparaíso', lichessUsername: 'ana_soto' });
    expect(usersApi.linkFederation).toHaveBeenCalledWith('738');
    expect(screen.getByText('Estamos trayendo tu ficha de la Federación.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Buscar a alguien para desafiar/ })).toHaveAttribute('href', '/app/jugadores');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('un username inválido no deja avanzar y se puede volver atrás', () => {
    renderWizard();
    next();
    fireEvent.change(screen.getByLabelText('Chess.com (opcional)'), { target: { value: 'a' } });
    expect(screen.getByRole('alert')).toHaveTextContent('Chess.com inválido');
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
    fireEvent.change(screen.getByLabelText('Chess.com (opcional)'), { target: { value: 'ab' } });
    expect(screen.queryByRole('alert')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Atrás' }));
    expect(screen.getByText(/paso 1 de 3/)).toBeInTheDocument();
  });

  it('omitir marca la bienvenida como vista y cierra', async () => {
    const { onClose } = renderWizard();
    fireEvent.click(screen.getByRole('button', { name: 'Omitir por ahora' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(usersApi.updateMyProfile).toHaveBeenCalledWith({ welcomed: true });
  });

  it('si la ficha ya existe sin dueño, pide confirmarla con el RUT sin frenar la bienvenida', async () => {
    vi.mocked(usersApi.linkFederation).mockRejectedValueOnce({ status: 409, error: 'CLAIM_REQUIRED', message: 'Confirma tu RUT' });
    renderWizard();
    next();
    fireEvent.change(screen.getByLabelText('Id federativo (opcional)'), { target: { value: '738' } });
    next();
    fireEvent.click(screen.getByRole('button', { name: 'Terminar' }));
    expect(await screen.findByText(/confírmala con tu RUT/)).toBeInTheDocument();
  });
});

describe('reglas de la bienvenida (valores límite)', () => {
  const at = (patch: Partial<typeof EMPTY_ANSWERS>) => accountErrors({ ...EMPTY_ANSWERS, ...patch });

  it('usernames de 2 a 30 caracteres permitidos; id federativo numérico de hasta 10 dígitos', () => {
    expect(at({})).toEqual([]);
    expect(at({ lichess: 'ab', chesscom: 'x'.repeat(30) })).toEqual([]);
    expect(at({ lichess: 'a' })).toHaveLength(1);
    expect(at({ chesscom: 'x'.repeat(31) })).toHaveLength(1);
    expect(at({ lichess: 'ana soto' })).toHaveLength(1);
    expect(at({ federationId: '1234567890' })).toEqual([]);
    expect(at({ federationId: '12345678901' })).toHaveLength(1);
    expect(at({ federationId: '73a' })).toHaveLength(1);
  });

  it('omitir todo guarda solo la marca de bienvenida', () => {
    expect(welcomeRequest(EMPTY_ANSWERS)).toEqual({ welcomed: true });
  });

  it('ejemplos por ritmo y preset propuesto al desafiar según el ritmo favorito', () => {
    expect(examplesOf('BLITZ')).toBe('3+2, 5+0');
    expect(examplesOf('CLASSICAL')).toBe('30+0');
    expect([presetFor('BULLET'), presetFor('BLITZ'), presetFor('RAPID'), presetFor('CLASSICAL')]).toEqual([0, 1, 3, 6]);
    expect(presetFor(null)).toBe(1);
    expect(presetFor(undefined)).toBe(1);
  });
});
