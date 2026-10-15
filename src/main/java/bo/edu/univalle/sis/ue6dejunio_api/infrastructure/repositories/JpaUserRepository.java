package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCi(String ci);

    boolean existsByPhone(String phone);

    /**
     * Existence plus the flag in one indexed lookup, for the per-request session check. Derived
     * rather than loading the entity: {@code UserEntity} fetches its role eagerly and holds the
     * password hash, and neither belongs in a boolean.
     */
    boolean existsByIdAndActiveTrue(UUID id);

    List<UserEntity> findByRole_NameAndActiveTrueOrderByLastNames(String roleName);

    List<UserEntity> findByRole_NameAndActiveTrueAndTechnicalOrderByLastNames(
            String roleName, boolean technical);

    /** The single holder of a role, for the callers that need one rather than the roster. */
    Optional<UserEntity> findFirstByRole_NameAndActiveTrueOrderByLastNames(String roleName);

    @Query("SELECT u FROM UserEntity u WHERE (:excludeId IS NULL OR u.id <> :excludeId)")
    Page<UserEntity> listExcluding(@Param("excludeId") UUID excludeId, Pageable pageable);

    /**
     * The search box promises "nombre, CI o correo", so a fragment matches names, last names, CI or
     * email, case-insensitively. {@code q} is never null here: the adapter sends a blank or absent
     * query to {@link #listExcluding} instead, because a null bound into {@code LOWER(...)} reaches
     * Postgres as {@code lower(bytea)} and fails.
     */
    @Query(
            """
        SELECT u FROM UserEntity u
        WHERE (:excludeId IS NULL OR u.id <> :excludeId)
              AND (LOWER(u.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.names) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.ci) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<UserEntity> search(
            @Param("q") String q, @Param("excludeId") UUID excludeId, Pageable pageable);
}
