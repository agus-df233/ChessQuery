package cl.chessquery.users.player;

import cl.chessquery.users.catalog.Club;
import cl.chessquery.users.catalog.Country;
import cl.chessquery.users.rating.RatingType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Jugador. Puede ser (a) un usuario con cuenta ({@code externalSubject} = sub del IdP),
 * (b) un provisorio cargado por un organizador ({@code provisional}, sin cuenta) o
 * (c) una fila federada creada desde AJEFECH que nadie reclamó todavía.
 *
 * <p>Los campos {@code elo*} son snapshots; el historial vive en {@code rating_history}.
 * Se leen y escriben por modalidad con {@link #rating(RatingType)} / {@link #setRating}
 * para que ningún servicio repita el switch.
 */
@Entity
@Table(name = "player")
@Getter @Setter
@Builder
@NoArgsConstructor
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_subject")
    private String externalSubject;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "display_name", length = 200)
    private String displayName;

    /** Siempre normalizado (trim + minúsculas); ver {@link Emails}. */
    @Column(length = 255)
    private String email;

    /** RUT chileno "12345678-9"; null para extranjeros. */
    @Column(length = 12)
    private String rut;

    /** RUT en claro: solo si lo ingresó su titular o el organizador que lo inscribió. */
    @Column(name = "rut_hash", length = 64)
    private String rutHash;

    /** Fecha completa: solo si la entregó su titular. Setter propio: mantiene {@link #birthYear}. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** Año de nacimiento: define la categoría. Único dato de edad de los federados no reclamados. */
    @Column(name = "birth_year")
    private Integer birthYear;

    /** Consentimiento parental registrado (menores de 14 con cuenta). */
    @Column(name = "parental_consent_at")
    private Instant parentalConsentAt;

    /** 'M', 'F' u 'O'. */
    @Column(length = 1)
    private String gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;

    @Column(length = 100)
    private String region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(name = "fide_id", length = 20)
    private String fideId;

    @Column(name = "federation_id", length = 50)
    private String federationId;

    @Column(name = "lichess_username", length = 100)
    private String lichessUsername;

    @Column(name = "chesscom_username", length = 100)
    private String chesscomUsername;

    // ── Snapshots de rating por modalidad ────────────────────────────────────
    @Column(name = "elo_national")          private Integer eloNational;
    @Column(name = "elo_fide_standard")     private Integer eloFideStandard;
    @Column(name = "elo_fide_rapid")        private Integer eloFideRapid;
    @Column(name = "elo_fide_blitz")        private Integer eloFideBlitz;
    @Column(name = "elo_platform_bullet")   private Integer eloPlatformBullet;
    @Column(name = "elo_platform_blitz")    private Integer eloPlatformBlitz;
    @Column(name = "elo_platform_rapid")    private Integer eloPlatformRapid;
    @Column(name = "elo_platform_classical") private Integer eloPlatformClassical;
    @Column(name = "elo_lichess_bullet")    private Integer eloLichessBullet;
    @Column(name = "elo_lichess_blitz")     private Integer eloLichessBlitz;
    @Column(name = "elo_lichess_rapid")     private Integer eloLichessRapid;
    @Column(name = "elo_lichess_classical") private Integer eloLichessClassical;
    @Column(name = "elo_chesscom_bullet")   private Integer eloChesscomBullet;
    @Column(name = "elo_chesscom_blitz")    private Integer eloChesscomBlitz;
    @Column(name = "elo_chesscom_rapid")    private Integer eloChesscomRapid;
    @Column(name = "elo_chesscom_daily")    private Integer eloChesscomDaily;

    /** Fuente del último enriquecimiento externo: AJEFECH, LICHESS, CHESSCOM. */
    @Column(name = "enrichment_source", length = 20)
    private String enrichmentSource;

    @Column(name = "enriched_at")
    private Instant enrichedAt;

    /** Trazabilidad del dato externo: URL de la ficha o lista y período "YYYY-MM". */
    @Column(name = "source_url", length = 300)
    private String sourceUrl;

    @Column(name = "source_period", length = 7)
    private String sourcePeriod;

    // ── Roster provisorio del organizador ────────────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private boolean provisional = false;

    @Column(name = "created_by_organizer_id")
    private Long createdByOrganizerId;

    /** Baja lógica del roster (nunca se borra: puede tener historial de torneos). */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Etiquetas del organizador separadas por coma (categoría, nivel, grupo). */
    @Column(length = 300)
    private String tags;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        syncBirthYear();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        syncBirthYear();
    }

    /** La fecha completa, si existe, manda sobre el año (el builder no pasa por el setter). */
    private void syncBirthYear() {
        if (birthDate != null) birthYear = birthDate.getYear();
    }

    // ── Helpers de dominio ───────────────────────────────────────────────────

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        this.birthYear = birthDate == null ? null : birthDate.getYear();
    }

    public boolean hasAccount() {
        return externalSubject != null;
    }

    /** Snapshot de una modalidad (null si nunca se registró). */
    public Integer rating(RatingType type) {
        return type.read(this);
    }

    public void setRating(RatingType type, Integer value) {
        type.write(this, value);
    }

    /** Etiquetas como lista limpia (sin vacíos ni espacios). */
    public List<String> tagList() {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    /** Guarda las etiquetas normalizadas (trim, sin vacíos ni duplicados); null si queda vacío. */
    public void setTagList(List<String> list) {
        if (list == null) { tags = null; return; }
        List<String> clean = list.stream().filter(t -> t != null && !t.isBlank())
                .map(String::trim).distinct().toList();
        tags = clean.isEmpty() ? null : String.join(",", clean);
    }
}
