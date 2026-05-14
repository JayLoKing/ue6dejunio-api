package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByCi(String ci);

    boolean existsByUsername(String username);

    @Query("""
        SELECT u FROM UserEntity u
        WHERE (:q IS NULL OR LOWER(u.lastNames) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(u.names) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<UserEntity> search(@Param("q") String q, Pageable pageable);
}
