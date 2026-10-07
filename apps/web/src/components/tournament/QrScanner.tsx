import { useEffect, useRef, useState } from 'react';
import { BrowserQRCodeReader, type IScannerControls } from '@zxing/browser';

const SAME_CODE_PAUSE_MS = 3_000;

/**
 * Lector de QR con la cámara del celular o del computador (funciona también en iOS, donde Safari no tiene
 * BarcodeDetector). Entrega cada código una vez: si el mismo QR sigue frente a la cámara, espera 3 s antes de repetirlo.
 * La cámara exige HTTPS (o localhost) y el permiso del navegador; si no se puede abrir, se avisa y queda el ingreso
 * manual del código.
 */
export const QrScanner = ({ onCode }: { onCode: (code: string) => void }) => {
  const video = useRef<HTMLVideoElement>(null);
  const last = useRef<{ code: string; at: number } | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let controls: IScannerControls | undefined;
    let stopped = false;
    const reader = new BrowserQRCodeReader();
    reader.decodeFromVideoDevice(undefined, video.current!, (result) => {
      if (!result) return;
      const code = result.getText().trim();
      const now = Date.now();
      if (last.current?.code === code && now - last.current.at < SAME_CODE_PAUSE_MS) return;
      last.current = { code, at: now };
      onCode(code);
    }).then((c) => { controls = c; if (stopped) c.stop(); })
      .catch(() => setError('No pudimos abrir la cámara: revisa el permiso del navegador o ingresa el código a mano.'));
    return () => { stopped = true; controls?.stop(); };
  }, [onCode]);

  return (
    <div>
      <video ref={video} className="cq-qr-video" muted playsInline aria-label="Cámara para leer el QR de acreditación" />
      {error && <p role="alert" className="cq-muted">{error}</p>}
    </div>
  );
};
