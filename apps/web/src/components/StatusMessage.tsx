import { isApiError } from '../api/client';

/**
 * Mensaje de estado de una operación: error de la API (con su `message`), error genérico o éxito.
 * Va con role=alert/status para que los lectores de pantalla lo anuncien.
 */
export const StatusMessage = ({ error, success }: { error?: unknown; success?: string | null }) => {
  if (error) {
    const text = isApiError(error) ? error.message : 'Ocurrió un error inesperado';
    return <p role="alert" style={{ color: 'var(--red)', margin: 0 }}>{text}</p>;
  }
  if (success) return <p role="status" className="cq-ok">{success}</p>;
  return null;
};
