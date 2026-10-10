import { ChangeEvent, useId, useState } from 'react';
import { cn } from '../utils/cn';

export interface FileInputProps {
  /** Nombre accesible del campo (lo usan los lectores de pantalla y las pruebas). */
  label: string;
  /** Texto del botón visible, p. ej. «Elegir archivo CSV». */
  buttonText: string;
  accept?: string;
  onChange: (e: ChangeEvent<HTMLInputElement>) => void;
  className?: string;
}

/**
 * Selector de archivo con texto propio: el control nativo del navegador muestra su texto en el idioma del sistema
 * («Choose File / No file chosen»). El input real queda accesible (oculto visualmente, no para lectores) y el botón es
 * su etiqueta, así que funcionan el teclado, el lector de pantalla y `setInputFiles` de las pruebas.
 */
export const FileInput = ({ label, buttonText, accept, onChange, className }: FileInputProps) => {
  const id = useId();
  const [name, setName] = useState<string | null>(null);
  return (
    <div className={cn('file-input', className)}>
      <input id={id} type="file" accept={accept} aria-label={label} className="visually-hidden-input"
             onChange={(e) => { setName(e.target.files?.[0]?.name ?? null); onChange(e); }} />
      <label htmlFor={id} className="btn btn-secondary">{buttonText}</label>
      <span className="file-input-name" aria-live="polite">{name ?? 'Ningún archivo elegido'}</span>
    </div>
  );
};
