import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { joinUrl } from '../../api/rooms';

/** Código grande + QR para entrar a la sala desde el celular (se proyecta al inicio de la clase). */
export const RoomQr = ({ code }: { code: string }) => {
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    let alive = true;
    QRCode.toDataURL(joinUrl(code), { margin: 1, width: 200 }).then((url) => { if (alive) setSrc(url); }).catch(() => undefined);
    return () => { alive = false; };
  }, [code]);
  return (
    <div className="cq-room-code">
      <div>
        <div className="cq-muted">Código para entrar</div>
        <div className="cq-room-code-value" aria-label={`Código de la sala: ${code.split('').join(' ')}`}>{code}</div>
        <div className="cq-muted">ChessQuery → Salas → ingresa el código, o escanea el QR</div>
      </div>
      {src && <img src={src} width={160} height={160} alt={`QR para entrar a la sala con el código ${code}`} />}
    </div>
  );
};
