package cl.chessquery.users.roster;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Contratos del roster provisorio del club. */
public final class RosterDtos {

    private RosterDtos() {}

    public record CreateRequest(@NotBlank @Size(max = 100) String firstName,
                                @NotBlank @Size(max = 100) String lastName,
                                @Size(max = 12) String rut,
                                @Email @Size(max = 255) String email,
                                Integer eloNational,
                                Integer eloFideStandard,
                                Integer clubId,
                                List<String> tags) {}

    public record TagsRequest(List<String> tags) {}

    /** Carga masiva (CSV ya leído en el navegador): cada fila se valida por separado. */
    public record ImportRequest(@NotEmpty @Size(max = 500) List<CreateRequest> rows) {}

    /** Resultado de una fila (1 = primera fila de datos): CREATED, DUPLICATE o ERROR (código y motivo). */
    public record ImportRow(int row, String outcome, Long playerId, String error, String message) {}

    public record ImportReport(int created, int duplicates, int errors, List<ImportRow> rows) {}

    /** Enlace para reclamar un perfil del roster (lo arma la web: /app/reclamar/{token}). */
    public record InviteView(Long playerId, String token, Instant expiresAt) {}

    /** Lo que ve quien abre la invitación antes de aceptarla: el nombre público y el club que lo cargó. */
    public record ClaimPreview(String firstName, String lastName, String organizationName) {}

    public record ClaimRequest(@NotBlank @Size(max = 64) String token) {}
}
