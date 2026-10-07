import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

/** QR de un enlace de la app (sala de juego, desafío abierto), generado en el navegador: no pasa por ningún servicio. */
export const QrCode = ({ value, label, size = 160 }: { value: string; label: string; size?: number }) => {
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(value, { margin: 1, width: size + 40 }).then((url) => { if (alive) setSrc(url); }).catch(() => undefined);
    return () => { alive = false; };
  }, [value, size]);
  return src ? <img src={src} width={size} height={size} alt={label} /> : null;
};
