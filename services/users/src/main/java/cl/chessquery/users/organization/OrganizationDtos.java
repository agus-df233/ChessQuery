package cl.chessquery.users.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contratos REST de la organización. */
public final class OrganizationDtos {

    private OrganizationDtos() {}

    public record Response(Long id, String name, String city, String description, String logoUrl,
                           String plan, int rosterCount, int maxRosterPlayers, int maxActiveTournaments) {
        static Response of(Organization o, int rosterCount) {
            return new Response(o.getId(), o.getName(), o.getCity(), o.getDescription(), o.getLogoUrl(),
                    o.getPlan().name(), rosterCount, o.getPlan().maxRosterPlayers, o.getPlan().maxActiveTournaments);
        }
    }

    /** Plan y límites, para que otros servicios apliquen cuotas. FREE si no hay club. */
    public record PlanInfo(Long organizationId, String plan, int maxRosterPlayers, int maxActiveTournaments) {}

    /** Crear o editar el club (nombre obligatorio). El plan no se edita por acá: eso es billing. */
    public record UpsertRequest(@NotBlank @Size(max = 150) String name,
                                @Size(max = 120) String city,
                                @Size(max = 500) String description,
                                @Size(max = 500) String logoUrl) {}
}
