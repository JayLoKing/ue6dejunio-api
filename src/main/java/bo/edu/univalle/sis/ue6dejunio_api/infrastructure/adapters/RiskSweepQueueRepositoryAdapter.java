package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk.SweepTarget;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.risk.IRiskSweepQueueDomain;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The sweep queue, as five statements.
 *
 * <p>Plain SQL and not JPA, because every one of these is set-based. Marking a course's subjects is
 * an {@code INSERT ... SELECT} over a join; through entities it would be a query for the class
 * groups, a loop, and one persist each — inside the transaction of a teacher saving a roll call.
 * The resolving these statements do (which subjects a course has, which trimester a date falls in)
 * is a question about rows, and the database answers it in the same statement that inserts.
 *
 * <p>Every mark ends in {@code ON CONFLICT ... DO UPDATE SET marked_at = clock_timestamp()}, never
 * {@code DO NOTHING}. The primary key is the coalescing — the second save of a subject adds no
 * second row — but the instant on that row still has to move, because it is what the sweep clears
 * against. Left at the first save's instant, a change arriving while the model was answering would
 * be deleted by a clear bounded on a later one, having never been predicted.
 *
 * <p>Refreshing cannot starve a busy classroom: a row whose instant keeps moving is still returned
 * by {@link #pending(int)} and still predicted every sweep. Only its deletion waits for the edits
 * to stop.
 *
 * <p>The three marking methods run {@code REQUIRES_NEW}. They are called from an
 * {@code AFTER_COMMIT} listener, where the committed transaction's resources are still bound to the
 * thread — the same hazard {@code NotificationDispatcher} documents, and joining it is how a write
 * ends up discarded without an error. {@code clearSwept} is reached only from the sweep, which
 * carries no transaction of its own, so it needs nothing special.
 */
@Repository
@Transactional(readOnly = true)
public class RiskSweepQueueRepositoryAdapter implements IRiskSweepQueueDomain {

    private final JdbcClient jdbc;

    public RiskSweepQueueRepositoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markClassGroups(Collection<UUID> classGroupIds, int trimester) {
        if (classGroupIds.isEmpty()) {
            return;
        }
        // The join to class_groups is not decoration: a mark for a subject that no longer exists
        // would violate the foreign key and take down the write that produced it. Selecting from
        // the table the key points at makes that impossible to express.
        jdbc.sql("""
                INSERT INTO risk_prediction_queue (id_class_group, trimester)
                SELECT DISTINCT cg.id_class_group, :trimester
                  FROM class_groups cg
                 WHERE cg.id_class_group IN (:ids)
                   AND cg.is_active
                ON CONFLICT (id_class_group, trimester)
                DO UPDATE SET marked_at = clock_timestamp()
                """)
            .param("trimester", trimester)
            .param("ids", classGroupIds)
            .update();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markClassGroupsOn(Collection<UUID> classGroupIds, LocalDate date) {
        if (classGroupIds.isEmpty()) {
            return;
        }
        // The trimester comes from the course's own gestión rather than from a parameter. A date
        // outside every configured period joins to no row and therefore marks nothing, which is the
        // right answer: there is no trimester for the model to be asked about.
        jdbc.sql("""
                INSERT INTO risk_prediction_queue (id_class_group, trimester)
                SELECT DISTINCT cg.id_class_group, at.trimester
                  FROM class_groups cg
                  JOIN courses c ON c.id_course = cg.id_course
                  JOIN academic_trimesters at
                    ON at.id_academic_year = c.id_academic_year
                   AND :date BETWEEN at.start_date AND at.end_date
                 WHERE cg.id_class_group IN (:ids)
                   AND cg.is_active
                ON CONFLICT (id_class_group, trimester)
                DO UPDATE SET marked_at = clock_timestamp()
                """)
            .param("date", date)
            .param("ids", classGroupIds)
            .update();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCoursesOfEnrollmentsOn(Collection<UUID> courseEnrollmentIds, LocalDate date) {
        if (courseEnrollmentIds.isEmpty()) {
            return;
        }
        // Every active subject of every course these students are enrolled in. A daily roll call
        // carries a null id_class_group, so it is the attendance any subject of that course falls
        // back to — one roll call moves the feature of all of them at once.
        jdbc.sql("""
                INSERT INTO risk_prediction_queue (id_class_group, trimester)
                SELECT DISTINCT cg.id_class_group, at.trimester
                  FROM course_enrollments ce
                  JOIN class_groups cg ON cg.id_course = ce.id_course
                  JOIN courses c ON c.id_course = ce.id_course
                  JOIN academic_trimesters at
                    ON at.id_academic_year = c.id_academic_year
                   AND :date BETWEEN at.start_date AND at.end_date
                 WHERE ce.id_course_enrollment IN (:ids)
                   AND cg.is_active
                ON CONFLICT (id_class_group, trimester)
                DO UPDATE SET marked_at = clock_timestamp()
                """)
            .param("date", date)
            .param("ids", courseEnrollmentIds)
            .update();
    }

    @Override
    public List<SweepTarget> pending(int limit) {
        return jdbc.sql("""
                SELECT id_class_group, trimester, marked_at
                  FROM risk_prediction_queue
                 ORDER BY marked_at, id_class_group, trimester
                 LIMIT :limit
                """)
            .param("limit", limit)
            .query((rs, rowNum) -> new SweepTarget(
                rs.getObject("id_class_group", UUID.class),
                rs.getInt("trimester"),
                rs.getTimestamp("marked_at").toLocalDateTime()))
            .list();
    }

    @Override
    @Transactional
    public int clearSwept(Collection<UUID> classGroupIds, int trimester, LocalDateTime asOf) {
        if (classGroupIds.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                DELETE FROM risk_prediction_queue
                 WHERE id_class_group IN (:ids)
                   AND trimester = :trimester
                   AND marked_at <= :asOf
                """)
            .param("ids", classGroupIds)
            .param("trimester", trimester)
            .param("asOf", asOf)
            .update();
    }
}
