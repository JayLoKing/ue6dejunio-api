package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaStudentRepository extends JpaRepository<StudentEntity, UUID> {
    boolean existsByRudeCode(String rudeCode);
    boolean existsByIdentityCard(String identityCard);
    Optional<StudentEntity> findByRudeCode(String rudeCode);
    Optional<StudentEntity> findByIdentityCard(String identityCard);
}
