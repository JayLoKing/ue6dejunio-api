package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaAttendanceRepository extends JpaRepository<AttendanceEntity, UUID> {
    Optional<AttendanceEntity> findByCourseEnrollment_IdAndDateAndClassGroupIsNull(UUID courseEnrollmentId, LocalDate date);
    Optional<AttendanceEntity> findByCourseEnrollment_IdAndClassGroup_IdAndDate(UUID courseEnrollmentId, UUID classGroupId, LocalDate date);
    List<AttendanceEntity> findByCourseEnrollment_IdOrderByDate(UUID courseEnrollmentId);
    List<AttendanceEntity> findByCourseEnrollment_IdAndClassGroupIsNullOrderByDate(UUID courseEnrollmentId);
}
