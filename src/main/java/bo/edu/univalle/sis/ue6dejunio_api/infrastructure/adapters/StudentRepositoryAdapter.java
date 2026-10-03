package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.NotReenrolledStudent;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryScope;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentStatusChange;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers.StudentMapper;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaStudentRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class StudentRepositoryAdapter implements IStudentDomain {

    private static final String STATUS_EFFECTIVE = "Effective";

    private final JpaStudentRepository repo;
    private final JpaUserRepository userRepo;
    private final StudentMapper mapper;

    /**
     * Only {@link #movementSummary} (two {@code GROUP BY} counts) and {@link #notReenrolled} (an
     * anti-join) use it. Through entities those would be a read of every enrolment of the gestión
     * to end up with a dozen numbers — this is the one place where the answer really is what the
     * database computes.
     */
    private final JdbcClient jdbc;

    public StudentRepositoryAdapter(
            JpaStudentRepository repo,
            JpaUserRepository userRepo,
            StudentMapper mapper,
            JdbcClient jdbc) {
        this.repo = repo;
        this.userRepo = userRepo;
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Student save(Student student) {
        StudentEntity entity = mapper.toEntity(student);
        return mapper.toDomain(repo.save(entity));
    }

    @Override
    public Optional<Student> findById(UUID id) {
        return repo.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Student> findByRudeCodeIn(Collection<String> rudeCodes) {
        if (rudeCodes == null || rudeCodes.isEmpty()) {
            return List.of();
        }
        return repo.findByRudeCodeIn(rudeCodes).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Student> findByIdentityCardIn(Collection<String> identityCards) {
        if (identityCards == null || identityCards.isEmpty()) {
            return List.of();
        }
        return repo.findByIdentityCardIn(identityCards).stream().map(mapper::toDomain).toList();
    }

    /**
     * Two queries and not one: a blank {@code q} takes the listing, so a null never reaches a
     * {@code LIKE}. Everything else about them is the same set of optional filters.
     */
    @Override
    public PageResult<StudentDirectoryItem> searchDirectory(
            StudentDirectoryQuery query, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        // Null narrows to nothing, which is what ALL means. The scope, not the caller, decides it.
        String status = query.scope().status();
        String q = query.q();
        if (q == null || q.isBlank()) {
            return SpringPaging.toPageResult(
                    repo.listDirectory(
                            query.courseId(),
                            query.gradeId(),
                            query.parallelId(),
                            query.academicYearId(),
                            status,
                            pageable));
        }
        return SpringPaging.toPageResult(
                repo.searchDirectory(
                        q.trim(),
                        query.courseId(),
                        query.gradeId(),
                        query.parallelId(),
                        query.academicYearId(),
                        status,
                        pageable));
    }

    /**
     * Two aggregate queries, no rows in memory.
     *
     * <p>Plain SQL and not JPA because both are {@code GROUP BY} counts: through entities they
     * would be a read of every enrolment and every student of the gestión, to end up with a dozen
     * numbers. This is the one place the answer really is what the database computes.
     *
     * <p>The two halves are counted off different columns because they are different facts. An
     * intake has its own dated row in {@code course_enrollments}; a withdrawal only has the single
     * status the student carries now.
     */
    @Override
    @Transactional(readOnly = true)
    public StudentMovementSummary movementSummary(Integer academicYearId) {
        Map<YearMonth, int[]> byMonth = new TreeMap<>();

        jdbc.sql(
                        """
                SELECT EXTRACT(YEAR FROM ce.enrollment_date)::int  AS y,
                       EXTRACT(MONTH FROM ce.enrollment_date)::int AS m,
                       COUNT(*)::int                               AS total
                  FROM course_enrollments ce
                  JOIN courses c ON c.id_course = ce.id_course
                 WHERE c.id_academic_year = :year
                   AND ce.enrollment_date IS NOT NULL
                 GROUP BY 1, 2
                """)
                .param("year", academicYearId)
                .query(
                        (rs, rowNum) -> {
                            byMonth.computeIfAbsent(
                                                    YearMonth.of(rs.getInt("y"), rs.getInt("m")),
                                                    k -> new int[2])[0] =
                                    rs.getInt("total");
                            return null;
                        })
                .list();

        /*
         * Only students who hold an enrolment in this gestión, so a child who left two years ago is
         * not counted again in every year that follows. DISTINCT on the student and not on the
         * enrolment: `course_enrollments` is unique on student and course, not on student and year,
         * so a transfer between parallels holds two rows for one child.
         */
        jdbc.sql(
                        """
                SELECT EXTRACT(YEAR FROM s.status_changed_at)::int  AS y,
                       EXTRACT(MONTH FROM s.status_changed_at)::int AS m,
                       COUNT(DISTINCT s.id_student)::int            AS total
                  FROM students s
                  JOIN course_enrollments ce ON ce.id_student = s.id_student
                  JOIN courses c ON c.id_course = ce.id_course
                 WHERE c.id_academic_year = :year
                   AND s.status = :withdrawn
                   AND s.status_changed_at IS NOT NULL
                 GROUP BY 1, 2
                """)
                .param("year", academicYearId)
                .param("withdrawn", StudentDirectoryScope.WITHDRAWN.status())
                .query(
                        (rs, rowNum) -> {
                            byMonth.computeIfAbsent(
                                                    YearMonth.of(rs.getInt("y"), rs.getInt("m")),
                                                    k -> new int[2])[1] =
                                    rs.getInt("total");
                            return null;
                        })
                .list();

        List<StudentMovementSummary.WithdrawalReasonCount> byReason =
                jdbc.sql(
                                """
                    SELECT s.status_reason                   AS reason,
                           COUNT(DISTINCT s.id_student)::int AS total
                      FROM students s
                      JOIN course_enrollments ce ON ce.id_student = s.id_student
                      JOIN courses c ON c.id_course = ce.id_course
                     WHERE c.id_academic_year = :year
                       AND s.status = :withdrawn
                     GROUP BY 1
                     ORDER BY 2 DESC
                    """)
                        .param("year", academicYearId)
                        .param("withdrawn", StudentDirectoryScope.WITHDRAWN.status())
                        .query(
                                (rs, rowNum) ->
                                        new StudentMovementSummary.WithdrawalReasonCount(
                                                rs.getString("reason"), rs.getInt("total")))
                        .list();

        List<StudentMovementSummary.MonthlyMovement> months =
                byMonth.entrySet().stream()
                        .map(
                                e ->
                                        new StudentMovementSummary.MonthlyMovement(
                                                e.getKey().getYear(),
                                                e.getKey().getMonthValue(),
                                                e.getValue()[0],
                                                e.getValue()[1]))
                        .toList();

        return new StudentMovementSummary(months, byReason);
    }

    /**
     * One anti-join, no rows compared in memory.
     *
     * <p>{@code prev} collapses last year's Effective seats to one per student: {@code
     * course_enrollments} is unique on student and course, not on student and gestión, so a child
     * who changed parallel holds two. {@code DISTINCT ON} keeps the latest enrolment date (a null
     * date last) and breaks a tie by course id, so the same data always gives the same row.
     *
     * <p>The {@code NOT EXISTS} takes a seat of any status in the reference gestión as an answer:
     * somebody who enrolled and was withdrawn this year did come back, and is the movement table's
     * business, not this list's. A student whose only seat last year was already Withdrawn never
     * enters {@code prev}.
     */
    @Override
    public List<NotReenrolledStudent> notReenrolled(
            Integer academicYearId, Integer previousYearId) {
        return jdbc.sql(
                        """
                WITH prev AS (
                    SELECT DISTINCT ON (ce.id_student)
                           ce.id_student, c.id_course, g.id_grade, g.name AS grade, p.name AS parallel
                      FROM course_enrollments ce
                      JOIN courses c   ON c.id_course = ce.id_course
                      JOIN grades g    ON g.id_grade = c.id_grade
                      JOIN parallels p ON p.id_parallel = c.id_parallel
                     WHERE c.id_academic_year = :previous
                       AND ce.status = :effective
                     ORDER BY ce.id_student, ce.enrollment_date DESC NULLS LAST, c.id_course
                )
                SELECT s.id_student, s.rude_code, s.identity_card, s.names, s.last_names, s.status,
                       prev.id_course, prev.grade, prev.parallel
                  FROM prev
                  JOIN students s ON s.id_student = prev.id_student
                 WHERE NOT EXISTS (
                        SELECT 1
                          FROM course_enrollments ce2
                          JOIN courses c2 ON c2.id_course = ce2.id_course
                         WHERE ce2.id_student = prev.id_student
                           AND c2.id_academic_year = :year)
                 ORDER BY prev.id_grade, prev.parallel, s.last_names, s.names, s.id_student
                """)
                .param("previous", previousYearId)
                .param("year", academicYearId)
                .param("effective", STATUS_EFFECTIVE)
                .query(
                        (rs, rowNum) ->
                                new NotReenrolledStudent(
                                        rs.getObject("id_student", UUID.class),
                                        rs.getString("rude_code"),
                                        rs.getString("identity_card"),
                                        rs.getString("names") + " " + rs.getString("last_names"),
                                        rs.getString("status"),
                                        new NotReenrolledStudent.LastCourse(
                                                rs.getObject("id_course", UUID.class),
                                                rs.getString("grade"),
                                                rs.getString("parallel"))))
                .list();
    }

    @Override
    @Transactional
    public void updateStatus(UUID studentId, StudentStatusChange change) {
        StudentEntity entity =
                repo.findById(studentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Estudiante", studentId));
        entity.setStatus(change.status());
        entity.setStatusReason(change.reason());
        entity.setStatusNote(change.note());
        // Stamped here rather than carried in: a clock the caller passes is a clock the caller can
        // be wrong about, and this is the row that says when a child left the school.
        entity.setStatusChangedAt(LocalDateTime.now());
        entity.setStatusChangedBy(
                change.changedBy() == null ? null : userRepo.getReferenceById(change.changedBy()));
        repo.save(entity);
    }
}
