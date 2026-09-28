package cl.chessquery.users;

import cl.chessquery.common.events.ChessEvent;
import cl.chessquery.common.events.EventPublisher;
import cl.chessquery.users.events.UsersEvents;
import cl.chessquery.users.identity.IdentityService;
import cl.chessquery.users.player.Player;
import cl.chessquery.users.player.PlayerRepository;
import cl.chessquery.users.rating.EloUpdatedConsumer;
import cl.chessquery.users.rating.RatingType;
import cl.chessquery.users.rating.RatingUpdatedConsumer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de punta a punta contra PostgreSQL 16 real: aplica Flyway, valida el mapeo JPA y
 * recorre la API con tokens simulados. Es la única prueba que ejercita las consultas nativas
 * (pg_trgm, ranking, índice funcional de amistades). Requiere Docker; en CI corre siempre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UsersIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EventPublisher events;

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired IdentityService identity;
    @Autowired EloUpdatedConsumer eloConsumer;
    @Autowired RatingUpdatedConsumer ratingConsumer;

    /** Request autenticado como el sujeto dado (el JWT se simula; la identidad se resuelve en BD). */
    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String subject, String email) {
        return b.with(jwt().jwt(j -> j.subject(subject).claim("email", email)
                .claim("given_name", "Ana").claim("family_name", "Soto").claim("name", "Ana Soto")));
    }

    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder b) {
        return b.header("X-Internal-Token", "test-token");
    }

    @Test @Order(1)
    void firstRequestProvisionsPlayerAndMeShowsIt() throws Exception {
        mvc.perform(as(get("/api/users/me"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.profile.firstName").value("Ana"))
           .andExpect(jsonPath("$.profile.email").value("ana@x.cl"))
           .andExpect(jsonPath("$.organizer").value(false));
        verify(events).publish(eq(UsersEvents.PLAYER_PROVISIONED), any());
        assertThat(players.findByExternalSubject("sub-ana")).isPresent();
    }

    @Test @Order(2)
    void createClubMakesMeOrganizerAndRosterWorks() throws Exception {
        mvc.perform(as(get("/api/organizations/me/roster"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ORGANIZER_REQUIRED"));
        mvc.perform(as(get("/api/organizations/me"), "sub-ana", "ana@x.cl")).andExpect(status().isNotFound());

        mvc.perform(as(post("/api/organizations"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club Torre\",\"city\":\"Santiago\"}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.plan").value("FREE"));
        mvc.perform(as(post("/api/organizations"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}"))
           .andExpect(status().isConflict());

        mvc.perform(as(get("/api/users/me"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$.organizer").value(true)).andExpect(jsonPath("$.organizationId").isNumber());
        mvc.perform(as(put("/api/organizations/me"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club Torre Norte\",\"description\":\" \"}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Club Torre Norte"));

        // Roster: alta, listado, etiquetas, baja
        mvc.perform(as(post("/api/organizations/me/roster"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Pedro\",\"lastName\":\"Rojas\",\"email\":\"Pedro@X.cl\",\"rut\":\"11111111-1\",\"eloNational\":1500,\"tags\":[\"sub12\",\" \",\"sub12\"]}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.provisional").value(true))
           .andExpect(jsonPath("$.email").value("pedro@x.cl"))
           .andExpect(jsonPath("$.tags.length()").value(1));
        mvc.perform(as(post("/api/organizations/me/roster"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Otro\",\"lastName\":\"Rojas\",\"rut\":\"11111111-1\"}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("RUT_TAKEN"));

        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        mvc.perform(as(get("/api/organizations/me/roster"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(as(patch("/api/organizations/me/roster/" + pedroId + "/tags"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"tags\":[\"sub14\",\"avanzado\"]}"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.tags.length()").value(2));
        mvc.perform(as(get("/api/organizations/me"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$.rosterCount").value(1));
        mvc.perform(as(delete("/api/organizations/me/roster/" + pedroId), "sub-ana", "ana@x.cl"))
           .andExpect(status().isNoContent());
        mvc.perform(as(get("/api/organizations/me"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$.rosterCount").value(0));
        mvc.perform(internal(get("/internal/organizations/by-owner/" + players.findByExternalSubject("sub-ana").orElseThrow().getId() + "/plan")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.maxActiveTournaments").value(3));
    }

    @Test @Order(3)
    void provisionalIsClaimedWhenOwnerSignsInWithSameEmail() throws Exception {
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        mvc.perform(as(get("/api/users/me"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.profile.id").value(pedroId))
           .andExpect(jsonPath("$.profile.provisional").value(false));
        verify(events).publish(eq(UsersEvents.PLAYER_CLAIMED), any());
        assertThat(players.findById(pedroId).orElseThrow().isActive()).isTrue();
    }

    @Test @Order(4)
    void profileEditSearchRankingAndPublicProfile() throws Exception {
        mvc.perform(as(put("/api/users/me/profile"), "sub-pedro", "pedro@x.cl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"region\":\"Metropolitana\",\"birthDate\":\"" + LocalDate.now().minusYears(11) + "\",\"lichessUsername\":\"pedrito\",\"countryId\":1}"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ageCategory").value("SUB_12"))
           .andExpect(jsonPath("$.country.isoCode").value("CHL"));
        mvc.perform(as(put("/api/users/me/profile"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"lichessUsername\":\"PEDRITO\"}"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LICHESS_USERNAME_TAKEN"));

        // Búsqueda difusa (pg_trgm) tolera un error de tipeo
        // Pedro tiene 11 años y no hay consentimiento parental: para terceros, apellido abreviado
        mvc.perform(as(get("/api/users/search").param("q", "pedro rojas"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].lastName").value("R."));
        mvc.perform(as(get("/api/users/search").param("q", "11111111-1"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$[0].fideId").isEmpty());
        mvc.perform(as(get("/api/users/search").param("q", " "), "sub-ana", "ana@x.cl"))
           .andExpect(status().isBadRequest());

        // Ranking por categoría y región
        mvc.perform(as(get("/api/users/ranking").param("category", "SUB_12").param("region", "metropolitana"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].position").value(1))
           .andExpect(jsonPath("$[0].eloNational").value(1500));
        mvc.perform(as(get("/api/users/ranking").param("category", "NOPE"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isBadRequest());

        // Perfil público sin PII
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        mvc.perform(as(get("/api/users/" + pedroId + "/public-profile"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.firstName").value("Pedro"))
           .andExpect(jsonPath("$.email").doesNotExist())
           .andExpect(jsonPath("$.rut").doesNotExist());
        mvc.perform(as(get("/api/users/999999/public-profile"), "sub-ana", "ana@x.cl")).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/catalog/countries"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].isoCode").isString());
        mvc.perform(as(get("/api/catalog/clubs"), "sub-ana", "ana@x.cl")).andExpect(status().isOk());
    }

    @Test @Order(5)
    void friendsFlow() throws Exception {
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        long anaId = players.findByExternalSubject("sub-ana").orElseThrow().getId();

        mvc.perform(as(get("/api/friends/status/" + pedroId), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$.status").value("NONE"));
        mvc.perform(as(post("/api/friends/requests"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"addresseeId\":" + pedroId + "}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_OUT"));
        mvc.perform(as(post("/api/friends/requests"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"addresseeId\":" + pedroId + "}"))
           .andExpect(status().isConflict());
        mvc.perform(as(get("/api/friends/requests").param("direction", "incoming"), "sub-pedro", "pedro@x.cl"))
           .andExpect(jsonPath("$[0].direction").value("INCOMING")).andExpect(jsonPath("$[0].firstName").value("Ana"));
        mvc.perform(as(get("/api/friends/requests").param("direction", "sideways"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isBadRequest());

        // Solicitud cruzada: Pedro pide a Ana y eso acepta la pendiente
        mvc.perform(as(post("/api/friends/requests"), "sub-pedro", "pedro@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"addresseeId\":" + anaId + "}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FRIENDS"));
        mvc.perform(as(get("/api/friends"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$[0].playerId").value(pedroId));
        mvc.perform(internal(get("/internal/friends/are-friends").param("a", String.valueOf(anaId)).param("b", String.valueOf(pedroId))))
           .andExpect(jsonPath("$.friends").value(true));
        mvc.perform(as(delete("/api/friends/" + pedroId), "sub-ana", "ana@x.cl")).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/friends/status/" + pedroId), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$.status").value("NONE"));

        // Rechazo explícito
        mvc.perform(as(post("/api/friends/requests"), "sub-ana", "ana@x.cl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"addresseeId\":" + pedroId + "}"));
        long requestId = Long.parseLong(mvc.perform(as(get("/api/friends/status/" + pedroId), "sub-ana", "ana@x.cl"))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"requestId\":(\\d+).*", "$1"));
        mvc.perform(as(post("/api/friends/requests/" + requestId + "/decline"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/friends/requests/" + requestId + "/decline"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isNoContent());
    }

    @Test @Order(6)
    void ratingsFromEventsAndHistory() throws Exception {
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        eloConsumer.apply(ChessEvent.of(UsersEvents.ELO_UPDATED, Map.of(
                "playerId", pedroId, "oldElo", 1500, "newElo", 1516, "ratingType", "NATIONAL", "gameId", 9)));
        ratingConsumer.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "AJEFECH", "players", List.of(
                // Match determinista por RUT (hasheado); nunca por nombre
                Map.of("firstName", "Pedro", "lastName", "Rojas", "federationId", "738", "fideId", "3404803",
                        "rut", "11.111.111-1", "eloNational", 1530, "eloFideStandard", 1600, "clubName", "Club Viña",
                        "birthDate", "2014-05-01"),
                Map.of("firstName", "Nueva", "lastName", "Federada", "federationId", "999", "eloNational", 1800),
                Map.of("lastName", "sin nombre")))));
        ratingConsumer.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "LICHESS", "players", List.of(
                Map.of("lichessUsername", "pedrito", "eloLichessBlitz", 1700, "eloLichessBullet", 0),
                Map.of("lichessUsername", "nadie", "eloLichessBlitz", 1)))));

        Player pedro = players.findById(pedroId).orElseThrow();
        assertThat(pedro.getEloNational()).isEqualTo(1530);
        assertThat(pedro.getEloFideStandard()).isEqualTo(1600);
        assertThat(pedro.getEloLichessBlitz()).isEqualTo(1700);
        assertThat(pedro.getFederationId()).isEqualTo("738");
        assertThat(pedro.getEnrichmentSource()).isEqualTo("LICHESS");
        assertThat(players.findByFederationId("999")).isPresent();

        mvc.perform(as(get("/api/users/me/rating-history").param("months", "6"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(2))
           .andExpect(jsonPath("$[1].rating").value(1530))
           .andExpect(jsonPath("$[1].delta").value(14));
        mvc.perform(as(get("/api/users/" + pedroId + "/rating-history").param("type", "LICHESS_BLITZ"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$[0].source").value("LICHESS"));
        // Sync externo con APIs inalcanzables: no rompe, devuelve el perfil
        mvc.perform(as(post("/api/users/me/external-ratings/sync"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.lichessUsername").value("pedrito"));
    }

    @Test @Order(7)
    void internalContract() throws Exception {
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        mvc.perform(get("/internal/players/by-subject/sub-pedro")).andExpect(status().isUnauthorized());
        mvc.perform(internal(get("/internal/players/by-subject/sub-pedro")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(pedroId));
        mvc.perform(internal(get("/internal/players/by-subject/nadie"))).andExpect(status().isNotFound());
        mvc.perform(internal(post("/internal/players/provision")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"sub-nuevo\",\"email\":\"nuevo@x.cl\",\"firstName\":\"Nuevo\"}"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.playerId").isNumber());
        mvc.perform(internal(post("/internal/players/provision")).contentType(MediaType.APPLICATION_JSON).content("{}"))
           .andExpect(status().isBadRequest());
        mvc.perform(internal(get("/internal/players").param("ids", pedroId + ",999999")))
           .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
           .andExpect(jsonPath("$[0].hasAccount").value(true));
        mvc.perform(internal(get("/internal/players/" + pedroId))).andExpect(jsonPath("$.rut").value("11111111-1"));
        mvc.perform(internal(get("/internal/players/by-email").param("email", "PEDRO@x.cl")))
           .andExpect(jsonPath("$.id").value(pedroId));
        mvc.perform(internal(get("/internal/players/by-email").param("email", "  "))).andExpect(status().isBadRequest());
        mvc.perform(internal(get("/internal/players/by-email").param("email", "x@y.z"))).andExpect(status().isNotFound());
        mvc.perform(internal(get("/internal/players/external-usernames")))
           .andExpect(jsonPath("$.lichess[0]").value("pedrito"));
        assertThat(identity.find("sub-pedro").playerId()).isEqualTo(pedroId);
        assertThat(LocalDate.now()).isAfter(LocalDate.of(2020, 1, 1));
        assertThat(Instant.now()).isNotNull();
    }

    @Test @Order(8)
    void publicViewsNeedNoLoginAndHideMinorsAndPii() throws Exception {
        long pedroId = players.findByRut("11111111-1").orElseThrow().getId();
        mvc.perform(get("/api/public/ranking").param("category", "SUB_12"))
           .andExpect(status().isOk())
           .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
           .andExpect(jsonPath("$[0].firstName").value("Pedro"))
           .andExpect(jsonPath("$[0].lastName").value("R."));
        mvc.perform(get("/api/public/players/" + pedroId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.lastName").value("R."))
           .andExpect(jsonPath("$.rut").doesNotExist())
           .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/users/ranking")).andExpect(status().isUnauthorized());
    }

    @Test @Order(9)
    void federatedRowsNeverMergeByNameAndStoreOnlyMinimalData() throws Exception {
        ratingConsumer.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "FIDE", "players", List.of(
                Map.of("firstName", "Ana", "lastName", "Soto", "fideId", "3400001", "birthYear", 1990,
                        "eloFideStandard", 1850, "eloFideRapid", 1800, "period", "2026-10",
                        "sourceUrl", "https://ratings.fide.com/profile/3400001"),
                Map.of("firstName", "Rut", "lastName", "Tercero", "federationId", "5555", "rut", "22.222.222-2",
                        "birthDate", "1980-03-04", "eloNational", 1400)))));

        // "Ana Soto" federada NO se fusiona con la cuenta de Ana: queda como sugerencia
        Player federada = players.findByFideId("3400001").orElseThrow();
        assertThat(federada.hasAccount()).isFalse();
        assertThat(federada.getSourcePeriod()).isEqualTo("2026-10");
        assertThat(federada.getEloFideRapid()).isEqualTo(1800);
        mvc.perform(as(get("/api/users/me/claim-suggestions"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(federada.getId()));

        // De terceros: año de nacimiento y hash del RUT, nunca la fecha ni el RUT en claro
        Player tercero = players.findByFederationId("5555").orElseThrow();
        assertThat(tercero.getRut()).isNull();
        assertThat(tercero.getRutHash()).hasSize(64);
        assertThat(tercero.getBirthDate()).isNull();
        assertThat(tercero.getBirthYear()).isEqualTo(1980);
        mvc.perform(as(get("/api/users/search").param("q", "22222222-2"), "sub-ana", "ana@x.cl"))
           .andExpect(jsonPath("$[0].id").value(tercero.getId()));
    }

    @Test @Order(10)
    void exportAndErasureWithSuppression() throws Exception {
        mvc.perform(as(get("/api/users/me/export"), "sub-pedro", "pedro@x.cl"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
           .andExpect(jsonPath("$.profile.rut").value("11111111-1"))
           .andExpect(jsonPath("$.ratingHistory.length()").isNumber())
           .andExpect(jsonPath("$.organization").doesNotExist());
        mvc.perform(as(delete("/api/users/me"), "sub-ana", "ana@x.cl"))
           .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORGANIZATION_OWNER"));

        // Luis entra, vincula su FIDE id por el ETL y luego pide supresión
        mvc.perform(as(get("/api/users/me"), "sub-luis", "luis@x.cl")).andExpect(status().isOk());
        Player luis = players.findByExternalSubject("sub-luis").orElseThrow();
        luis.setFideId("3499999");
        players.save(luis);
        mvc.perform(as(delete("/api/users/me"), "sub-luis", "luis@x.cl")).andExpect(status().isNoContent());
        verify(events).publish(eq(UsersEvents.PLAYER_DELETED), any());

        Player borrado = players.findById(luis.getId()).orElseThrow();
        assertThat(borrado.getEmail()).isNull();
        assertThat(borrado.getExternalSubject()).isNull();
        assertThat(borrado.isActive()).isFalse();
        // El próximo mes el ETL vuelve a traer ese FIDE id: no se reimporta
        ratingConsumer.apply(ChessEvent.of(UsersEvents.RATING_UPDATED, Map.of("source", "FIDE", "players", List.of(
                Map.of("firstName", "Luis", "lastName", "Mena", "fideId", "3499999", "eloFideStandard", 1700)))));
        assertThat(players.findByFideId("3499999")).isEmpty();
    }
}
