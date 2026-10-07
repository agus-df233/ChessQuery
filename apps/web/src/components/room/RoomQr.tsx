import { joinUrl } from '../../api/rooms';
import { QrCode } from '../QrCode';

/** Código grande + QR para entrar a la sala desde el celular (se proyecta al inicio de la clase). */
export const RoomQr = ({ code }: { code: string }) => (
  <div className="cq-room-code">
    <div>
      <div className="cq-muted">Código para entrar</div>
      <div className="cq-room-code-value" aria-label={`Código de la sala: ${code.split('').join(' ')}`}>{code}</div>
      <div className="cq-muted">ChessQuery → Salas → ingresa el código, o escanea el QR</div>
    </div>
    <QrCode value={joinUrl(code)} label={`QR para entrar a la sala con el código ${code}`} />
  </div>
);
