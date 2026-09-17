package cl.chessquery.users.roster;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
}
