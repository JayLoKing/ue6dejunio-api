package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAcademicYearRepository extends JpaRepository<AcademicYearEntity, Integer> {
    Optional<AcademicYearEntity> findByYear(Integer year);

    Optional<AcademicYearEntity> findByActiveTrue();

    boolean existsByActiveTrue();

    /**
     * Newest first: the gestión a school works in is the one it opens a picker to select, and every
     * year before it is history somebody scrolls back to.
     */
    List<AcademicYearEntity> findAllByOrderByYearDesc();

    /**
     * First half of a switch. The partial unique index on {@code active} is checked row by row, so
     * the old row has to be cleared in its own statement before the new one is set.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.active = false WHERE y.active = true AND y.id <> :id")
    int deactivateAllExcept(@Param("id") Integer id);

    /** Second half of a switch; see {@link #deactivateAllExcept(Integer)}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE AcademicYearEntity y SET y.active = true WHERE y.id = :id")
    int activate(@Param("id") Integer id);
}
