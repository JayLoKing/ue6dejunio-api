package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanSubjectEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaCurriculumPlanSubjectRepository
        extends JpaRepository<CurriculumPlanSubjectEntity, UUID> {

    /**
     * The weekly rows of every block of a plan, in one query.
     *
     * <p>The blocks themselves are fetched with the plan; this fills their rows afterwards.
     * Hibernate will not join-fetch both ordered collections at once, and leaving the rows lazy
     * would issue one query per subject instead. The returned list is discarded on purpose — what
     * matters is that the rows are now loaded in the same persistence context as the blocks.
     */
    @Query(
            """
        SELECT s FROM CurriculumPlanSubjectEntity s
        LEFT JOIN FETCH s.entries
        WHERE s.curriculumPlan.id = :planId
        """)
    List<CurriculumPlanSubjectEntity> fetchEntriesOfPlan(@Param("planId") UUID planId);
}
