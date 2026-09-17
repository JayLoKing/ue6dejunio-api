package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.ratelimit.InMemoryBucketStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("it")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    /**
     * Singleton container: started once per JVM and never stopped between test classes. Ryuk
     * removes it when the run ends.
     *
     * <p>Deliberately NOT annotated {@code @Container}. That made JUnit stop the container after
     * the first test class and restart it on a fresh random port for the next one, while Spring
     * kept reusing the cached context holding the port {@code @DynamicPropertySource} had already
     * resolved — every class after the first one then failed with ConnectException.
     */
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ue6_it")
            .withUsername("it")
            .withPassword("it");

    static {
        // This block runs at class-load time, before disabledWithoutDocker gets a chance to skip
        // anything, so the availability check has to happen here rather than being inherited.
        if (DockerClientFactory.instance().isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected JwtEncoder jwtEncoder;
    @Autowired private InMemoryBucketStore bucketStore;

    /**
     * Shared request serializer. A bare ObjectMapper refuses java.time types, so every subclass
     * building a body with a LocalDate needs the JSR-310 module registered — one place instead of
     * one per test class.
     */
    protected final ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    /**
     * Everything a test writes. Truncated before each test because the container is now a
     * singleton shared by every IT class — without this, one class's courses and students leak
     * into the next one's assertions.
     *
     * <p>The catalog tables are deliberately absent: roles, levels, grades, parallels, subjects,
     * academic_years and academic_trimesters are seeded once by schema-it.sql, and the trimester
     * periods that several tests depend on hang off academic_years.
     */
    private static final String[] TRANSACTIONAL_TABLES = {
        "notifications", "risk_predictions", "attendance", "academic_scores", "assessment_scores",
        "assessment_events", "evaluation_criteria", "curriculum_adaptations",
        "curriculum_plan_progress", "curriculum_plans", "pedagogical_report_failures",
        "pedagogical_reports", "class_groups", "course_enrollments", "courses", "students", "users"
    };

    /** The gestiones schema-it.sql seeds. Anything else in the table was made up by a test. */
    private static final String SEEDED_YEARS = "(2025, 2026)";

    /** Likewise the four areas of knowledge the curriculum actually has. */
    private static final String SEEDED_AREAS =
        "('Cosmos y Pensamiento', 'Comunidad y Sociedad', 'Vida Tierra y Territorio', "
            + "'Ciencia Tecnología y Producción')";

    @BeforeEach
    void resetSharedState() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TRANSACTIONAL_TABLES) + " CASCADE");
        // A test that needs a year of its own inserts one, and academic_years is a catalog table
        // nobody truncates. Left behind, the highest of those invented years becomes what every
        // later test calls the current gestión — and the student directory answers about it.
        jdbc.update("DELETE FROM academic_trimesters WHERE id_academic_year IN "
            + "(SELECT id_academic_year FROM academic_years WHERE year NOT IN " + SEEDED_YEARS + ")");
        jdbc.update("DELETE FROM academic_years WHERE year NOT IN " + SEEDED_YEARS);
        // Same story for the areas of knowledge: a catalogue table nobody truncates, so an area a
        // test invents outlives it and turns up in the next class's listing. Its subjects go first
        // — subjects are catalogue too, and subjects.id_area is ON DELETE RESTRICT, so the area
        // cannot leave while one still names it.
        jdbc.update("DELETE FROM subjects WHERE id_area IN "
            + "(SELECT id_area FROM knowledge_areas WHERE name NOT IN " + SEEDED_AREAS + ")");
        jdbc.update("DELETE FROM knowledge_areas WHERE name NOT IN " + SEEDED_AREAS);
        // Rate-limit buckets live in the shared context, not the database, so they need their own
        // reset: otherwise a test that deliberately exhausts a bucket makes the next one fail 429.
        bucketStore.clear();
    }

    @Value("${app.security.jwt.issuer}")
    protected String jwtIssuer;

    private static final String STATUS_EFFECTIVE = "Effective";

    /** Mints a real RS256 JWT for the given user id and role, mirroring JwtService. */
    protected String tokenFor(UUID userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtIssuer)
            .issuedAt(now)
            .expiresAt(now.plus(15, ChronoUnit.MINUTES))
            .subject(userId.toString())
            .claim("role", role)
            .build();
        return jwtEncoder.encode(
            JwtEncoderParameters.from(JwsHeader.with(() -> "RS256").build(), claims)
        ).getTokenValue();
    }

    protected UUID seedUser(String role, boolean technical) {
        UUID id = UUID.randomUUID();
        Integer roleId = jdbc.queryForObject(
            "SELECT id_role FROM roles WHERE name = ?", Integer.class, role);
        String suffix = id.toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO users (id_user, ci, names, last_names, phone, email, password, id_role, "
                + "is_technical, is_active, must_change_password) "
                + "VALUES (?,?,?,?,?,?,?,?,?,true,false)",
            id, "CI-" + suffix, "Nombre" + suffix, "Apellido" + suffix, "70000000",
            "user-" + suffix + "@ue6.test", "{noop}pwd", roleId, technical);
        return id;
    }

    /** A course in the current gestión — the year every caller who names none is talking about. */
    protected UUID seedCourse(UUID homeroomTeacherId, String parallelName) {
        return seedCourse(homeroomTeacherId, parallelName, currentAcademicYearId());
    }

    /**
     * A course in a named gestión. {@code UNIQUE (id_grade, id_parallel, id_academic_year)} is what
     * makes this worth having: the same grade and parallel in another year is another course, which
     * is exactly how a student who moves up keeps one record and gains a second enrolment.
     */
    protected UUID seedCourse(UUID homeroomTeacherId, String parallelName, Integer academicYearId) {
        UUID id = UUID.randomUUID();
        Integer gradeId = jdbc.queryForObject("SELECT id_grade FROM grades LIMIT 1", Integer.class);
        Integer parallelId = jdbc.queryForObject(
            "SELECT id_parallel FROM parallels WHERE name = ?", Integer.class, parallelName);
        jdbc.update(
            "INSERT INTO courses (id_course, id_grade, id_parallel, id_academic_year, "
                + "id_homeroom_teacher, is_active) VALUES (?,?,?,?,?,true)",
            id, gradeId, parallelId, academicYearId, homeroomTeacherId);
        return id;
    }

    /** The latest year on record, which is what the application calls the current gestión. */
    protected Integer currentAcademicYearId() {
        return jdbc.queryForObject(
            "SELECT id_academic_year FROM academic_years ORDER BY year DESC LIMIT 1", Integer.class);
    }

    protected Integer academicYearId(int year) {
        return jdbc.queryForObject(
            "SELECT id_academic_year FROM academic_years WHERE year = ?", Integer.class, year);
    }

    protected UUID seedClassGroup(UUID courseId, UUID teacherId, String subjectName) {
        UUID id = UUID.randomUUID();
        UUID subjectId = jdbc.queryForObject(
            "SELECT id_subject FROM subjects WHERE name = ?", UUID.class, subjectName);
        jdbc.update(
            "INSERT INTO class_groups (id_class_group, id_course, id_subject, id_teacher, is_active) "
                + "VALUES (?,?,?,?,true)",
            id, courseId, subjectId, teacherId);
        return id;
    }

    protected UUID seedStudent() {
        UUID id = UUID.randomUUID();
        String suffix = id.toString().substring(0, 8);
        return insertStudent(id, "Est" + suffix, "Apellido" + suffix);
    }

    /**
     * Seeds a student with stable names. Needed wherever the assertion depends on the response
     * text or on row order: listings sort by last name, and the generated names carry a random
     * UUID fragment, so two students would swap places from one run to the next.
     */
    protected UUID seedStudent(String names, String lastNames) {
        return insertStudent(UUID.randomUUID(), names, lastNames);
    }

    private UUID insertStudent(UUID id, String names, String lastNames) {
        String suffix = id.toString().substring(0, 8);
        jdbc.update(
            "INSERT INTO students (id_student, rude_code, identity_card, names, last_names, "
                + "birth_date, gender, status) VALUES (?,?,?,?,?,?,?,?)",
            id, "RUDE-" + suffix, "ID-" + suffix, names, lastNames,
            java.sql.Date.valueOf("2015-01-01"), "M", STATUS_EFFECTIVE);
        return id;
    }

    protected UUID seedEnrollment(UUID studentId, UUID courseId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO course_enrollments (id_course_enrollment, id_student, id_course, status) "
                + "VALUES (?,?,?,?)",
            id, studentId, courseId, STATUS_EFFECTIVE);
        return id;
    }

    /** A criterion scored directly: no activity, so its score lands on the criterion itself. */
    protected UUID seedCriterion(UUID classGroupId, int trimester, String dimension, String name) {
        return insertCriterion(classGroupId, trimester, dimension, name, null);
    }

    /** A criterion fed by an activity: its score is the average of the items seeded under it. */
    protected UUID seedActivityCriterion(UUID classGroupId, int trimester, String dimension,
                                         String name, String activityName) {
        return insertCriterion(classGroupId, trimester, dimension, name, activityName);
    }

    private UUID insertCriterion(UUID classGroupId, int trimester, String dimension,
                                 String name, String activityName) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO evaluation_criteria "
                + "(id_criterion, id_class_group, trimester, dimension, name, activity_name) "
                + "VALUES (?,?,?,?,?,?)",
            id, classGroupId, trimester, dimension, name, activityName);
        return id;
    }

    protected UUID seedEvent(UUID criterionId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO assessment_events (id_assessment_event, id_criterion, title) VALUES (?,?,?)",
            id, criterionId, title);
        return id;
    }

    protected void seedEventScore(UUID courseEnrollmentId, UUID eventId, double score) {
        jdbc.update(
            "INSERT INTO assessment_scores (id_course_enrollment, id_assessment_event, score) "
                + "VALUES (?,?,?)",
            courseEnrollmentId, eventId, score);
    }

    protected void seedCriterionScore(UUID courseEnrollmentId, UUID criterionId, double score) {
        jdbc.update(
            "INSERT INTO assessment_scores (id_course_enrollment, id_criterion, score) "
                + "VALUES (?,?,?)",
            courseEnrollmentId, criterionId, score);
    }
}
