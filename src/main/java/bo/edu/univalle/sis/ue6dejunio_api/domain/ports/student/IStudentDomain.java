package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IStudentDomain {
    Student save(Student student);

    /**
     * How the roll moved during a gestión: intakes and withdrawals by month, and the reasons.
     *
     * <p>Two aggregate queries and no rows carried into memory — this answers a chart, and a chart
     * asks for counts. The intakes come from each enrolment's own {@code enrollment_date}; the
     * withdrawals from the single status row a student carries, which is the last word and not a
     * ledger. See {@link
     * bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary} for what
     * that costs.
     */
    StudentMovementSummary movementSummary(Integer academicYearId);

    Optional<Student> findById(UUID id);

    /** The students behind a whole batch of RUDE codes, in one lookup. */
    List<Student> findByRudeCodeIn(Collection<String> rudeCodes);

    /** The students behind a whole batch of identity cards, in one lookup. */
    List<Student> findByIdentityCardIn(Collection<String> identityCards);

    PageResult<StudentDirectoryItem> searchDirectory(
            StudentDirectoryQuery query, PageQuery pageQuery);

    /**
     * Moves the student to another status and records why, in whose words, and by whom.
     *
     * <p>The only path that writes the explanation. {@link #save} sets the status a student is
     * created with and nothing else about it, so an ordinary edit cannot restate — or blank — who
     * took a student off the roll.
     */
    void updateStatus(UUID studentId, StudentStatusChange change);

    /**
     * The same change applied to a whole set of students, in one statement.
     *
     * <p>For the moments the school moves people together rather than one at a time — an imported
     * roster putting back everyone who had left. Asking per student would cost a read and a write
     * per name on the one path whose whole design is a fixed number of queries for the roster.
     */
    void updateStatusIn(Collection<UUID> studentIds, StudentStatusChange change);
}
