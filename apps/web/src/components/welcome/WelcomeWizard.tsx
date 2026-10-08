import { FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Button, Card } from '@chessquery/ui-lib';
import { isApiError } from '../../api/client';
import { usersApi } from '../../api/users';
import { CATEGORIES, CATEGORY_LABEL } from '../../lib/timeControl';
import { REGIONS } from '../../lib/regions';
import { StatusMessage } from '../StatusMessage';
import { EMPTY_ANSWERS, accountErrors, examplesOf, welcomeRequest, type WelcomeAnswers } from './welcome';

type StepProps = { answers: WelcomeAnswers; set: (patch: Partial<WelcomeAnswers>) => void };
type FederationOutcome = 'linked' | 'claim' | 'error' | null;

const CategoryStep = ({ answers, set }: StepProps) => (
  <fieldset className="cq-fieldset">
    <legend>¿Qué ritmo te gusta jugar?</legend>
    <p className="cq-muted" style={{ margin: 0 }}>Cada ritmo tiene su propio ELO ChessQuery. Lo usaremos para proponerte partidas.</p>
    {CATEGORIES.map((c) => (
      <label key={c} className="cq-radio">
        <input type="radio" name="welcome-category" value={c} checked={answers.category === c} onChange={() => set({ category: c })} />
        {CATEGORY_LABEL[c]} <span className="cq-muted">(ej. {examplesOf(c)})</span>
      </label>
    ))}
  </fieldset>
);

const AccountsStep = ({ answers, set }: StepProps) => (
  <fieldset className="cq-fieldset">
    <legend>¿Ya juegas en otro lado?</legend>
    <p className="cq-muted" style={{ margin: 0 }}>Todo es opcional. Juntamos tus ratings en un solo perfil.</p>
    <label>Id federativo (opcional)
      <input inputMode="numeric" value={answers.federationId} placeholder="Ej: 738"
             onChange={(e) => set({ federationId: e.target.value.trim() })} />
    </label>
    <label>Lichess (opcional)
      <input value={answers.lichess} autoComplete="off" onChange={(e) => set({ lichess: e.target.value.trim() })} />
    </label>
    <label>Chess.com (opcional)
      <input value={answers.chesscom} autoComplete="off" onChange={(e) => set({ chesscom: e.target.value.trim() })} />
    </label>
  </fieldset>
);

const RegionStep = ({ answers, set }: StepProps) => (
  <fieldset className="cq-fieldset">
    <legend>¿Dónde juegas?</legend>
    <p className="cq-muted" style={{ margin: 0 }}>Para mostrarte torneos y clubes cerca de ti.</p>
    <label>Región
      <select value={answers.region} onChange={(e) => set({ region: e.target.value })}>
        <option value="">Prefiero no decirlo</option>
        {REGIONS.map((r) => <option key={r} value={r}>{r}</option>)}
      </select>
    </label>
  </fieldset>
);

const STEPS = [CategoryStep, AccountsStep, RegionStep];

const FEDERATION_NOTE: Record<Exclude<FederationOutcome, null>, string> = {
  linked: 'Estamos trayendo tu ficha de la Federación.',
  claim: 'Esa ficha ya está en ChessQuery: confírmala con tu RUT en la tarjeta de la Federación.',
  error: 'No pudimos vincular tu ficha federativa; puedes intentarlo desde la tarjeta de la Federación.',
};

/** Siguientes pasos al terminar: lo que alguien nuevo puede hacer de inmediato. */
const Done = ({ federation, onClose }: { federation: FederationOutcome; onClose: () => void }) => (
  <Card header="¡Listo! Tu perfil quedó configurado">
    {federation && <p role="status" style={{ marginTop: 0 }}>{FEDERATION_NOTE[federation]}</p>}
    <p className="cq-muted" style={{ marginTop: 0 }}>¿Qué quieres hacer ahora?</p>
    <div className="cq-actions">
      <Link to="/app/jugadores"><Button size="sm">Buscar a alguien para desafiar</Button></Link>
      <Link to="/app/torneos"><Button size="sm" variant="secondary">Ver torneos</Button></Link>
      <Link to="/app/salas"><Button size="sm" variant="secondary">Entrar a una sala con código</Button></Link>
      <Button size="sm" variant="secondary" onClick={onClose}>Ir a mi inicio</Button>
    </div>
  </Card>
);

/** Guarda las respuestas y, si dio un id federativo, intenta vincularlo (sin frenar la bienvenida si falla). */
const useFinish = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (a: WelcomeAnswers): Promise<FederationOutcome> => {
      await usersApi.updateMyProfile(welcomeRequest(a));
      if (!a.federationId) return null;
      try {
        await usersApi.linkFederation(a.federationId);
        return 'linked';
      } catch (e) {
        return isApiError(e) && e.error === 'CLAIM_REQUIRED' ? 'claim' : 'error';
      }
    },
    onSuccess: () => ['me', 'my-history', 'claim-suggestions'].forEach((k) => void qc.invalidateQueries({ queryKey: [k] })),
  });
};

/**
 * Asistente de bienvenida (3 pasos) para quien entra por primera vez: ritmo favorito, cuentas en otras plataformas
 * y región. No bloquea nada: se puede omitir y no vuelve a aparecer.
 */
export const WelcomeWizard = ({ onClose }: { onClose: () => void }) => {
  const [step, setStep] = useState(0);
  const [answers, setAnswers] = useState(EMPTY_ANSWERS);
  const finish = useFinish();
  const skip = useMutation({ mutationFn: () => usersApi.updateMyProfile({ welcomed: true }), onSuccess: onClose });
  const errors = step === 1 ? accountErrors(answers) : [];
  const last = step === STEPS.length - 1;
  if (finish.isSuccess) return <Done federation={finish.data} onClose={onClose} />;

  const Step = STEPS[step];
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (errors.length) return;
    if (last) finish.mutate(answers);
    else setStep(step + 1);
  };
  return (
    <Card header={`Bienvenida a ChessQuery · paso ${step + 1} de ${STEPS.length}`}>
      <form className="cq-form" onSubmit={onSubmit} aria-label="Bienvenida">
        <Step answers={answers} set={(patch) => setAnswers({ ...answers, ...patch })} />
        {errors.map((m) => <p key={m} role="alert" style={{ color: 'var(--red)', margin: 0 }}>{m}</p>)}
        <div className="cq-actions">
          {step > 0 && <Button type="button" size="sm" variant="secondary" onClick={() => setStep(step - 1)}>Atrás</Button>}
          <Button type="submit" size="sm" loading={finish.isPending} disabled={errors.length > 0}>{last ? 'Terminar' : 'Siguiente'}</Button>
          <Button type="button" size="sm" variant="secondary" loading={skip.isPending} onClick={() => skip.mutate()}>Omitir por ahora</Button>
          <StatusMessage error={finish.error ?? skip.error} />
        </div>
      </form>
    </Card>
  );
};
