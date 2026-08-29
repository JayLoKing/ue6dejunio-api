package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCi(String ci);

    List<UserEntity> findByRole_NameAndActiveTrueOrderByLastNames(String roleName);
    List<UserEntity> findByRole_NameAndActiveTrueAndTechnicalOrderByLastNames(String roleName, boolean technical);

    /** The single holder of a role, for the callers that need one rather than the roster. */
    Optional<UserEntity> findFirstByRole_NameAndActiveTrueOrderByLastNames(String roleName);

    @Query("SELECT u FROM UserEntity u WHERE (:excludeId IS NULL OR u.id <> :excludeId)")
    Page<UserEntity> listExcluding(@Param("excludeId") UUID excludeId, Pageable pageable);

    @Query("""
        SELECT u FROM UserEntity u
        WHERE (:excludeId IS NULL OR u.id <> :excludeId)
              AND (LOWER(u.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.names) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<UserEntity> search(@Param("q") String q, @Param("excludeId") UUID excludeId, Pageable pageable);
}
