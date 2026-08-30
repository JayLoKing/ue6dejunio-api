package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adaptation against a real database. Every test until now mocked the port, so a column the
 * adapter never carries reads exactly like a column the teacher left empty — the value goes in,
 * nothing complains, and it comes back null.
 */
class AdaptationPersistenceIT extends AbstractIntegrationTest {

    @Autowired private IPdcService pdcService;
    @Autowired private IAdaptationService adaptationService;

    private UUID teacher;
    private UUID plan;

    @BeforeEach
    void setUp() {
        teacher = seedUser("Teacher", false);
        UUID course = seedCourse(teacher, "A");
        seedClassGroup(course, teacher, "Matematicas");
        plan = pdcService.create(new CreatePdcCommand(course, 4, 2,
            LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 4),
            "Fortalecemos la práctica de valores sociocomunitarios.", null, null, null),
            teacher).getId();
    }

    private Adaptation adaptationFor(String conditionType) {
        return adaptationService.create(new CreateAdaptationCommand(plan, seedStudent(),
            conditionType, "Contenido adaptado", "Metodología adaptada", "Criterio adaptado",
            teacher));
    }

    // The condition is a column of the handed-in form, not a note: the same adapted content means
    // one thing for a child with a disability and another for one with an extraordinary talent.
    // Losing it leaves a row that no longer says who it was written for.
    @Test
    void theConditionTheAdaptationAnswersToSurvivesACreate() {
        Adaptation created = adaptationFor("Talento extraordinario");

        assertThat(created.conditionType()).isEqualTo("Talento extraordinario");
        assertThat(adaptationService.getById(created.id()).conditionType())
            .isEqualTo("Talento extraordinario");
    }

    // A diagnosis gets corrected after the plan is written, so the update has to reach the column
    // the create wrote.
    @Test
    void anUpdateCorrectsTheConditionAndLeavesTheRestStanding() {
        Adaptation created = adaptationFor("Discapacidad");

        adaptationService.update(created.id(),
            new UpdateAdaptationCommand("TEA", null, null, null, teacher));

        Adaptation read = adaptationService.getById(created.id());
        assertThat(read.conditionType()).isEqualTo("TEA");
        assertThat(read.adaptedContents()).isEqualTo("Contenido adaptado");
        assertThat(read.adaptedMethodology()).isEqualTo("Metodología adaptada");
        assertThat(read.adaptedCriteria()).isEqualTo("Criterio adaptado");
    }

    // A null column means "leave it" everywhere else in this update, and the condition is no
    // different: correcting only the methodology must not blank out who the row was written for.
    @Test
    void anUpdateThatDoesNotNameTheConditionKeepsIt() {
        Adaptation created = adaptationFor("TDH");

        adaptationService.update(created.id(),
            new UpdateAdaptationCommand(null, null, "Otra metodología", null, teacher));

        Adaptation read = adaptationService.getById(created.id());
        assertThat(read.conditionType()).isEqualTo("TDH");
        assertThat(read.adaptedMethodology()).isEqualTo("Otra metodología");
    }

    /**
     * One row per student, held by the database rather than by a look-before-you-write.
     *
     * <p>The service asks {@code existsByPlanAndStudent} before inserting, which is a check and
     * then an act: two concurrent creates both read "no row yet" and both insert, and the plan ends
     * up printing the same child twice. The insert below goes straight to SQL because that is what
     * the losing half of that race does — reach the table with the service's blessing already
     * given.
     */
    @Test
    void theDatabaseItselfRefusesASecondAdaptationForTheSameStudent() {
        Adaptation first = adaptationFor("Discapacidad");

        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO curriculum_adaptations (id_curriculum_adaptation, id_curriculum_plan, "
                + "id_student, condition_type) VALUES (?,?,?,?)",
            UUID.randomUUID(), plan, first.studentId(), "TEA"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Another student of the same plan is a different row, and the constraint must not stand in
    // the way of the ordinary case it exists to protect.
    @Test
    void aSecondStudentOfTheSamePlanIsAccepted() {
        adaptationFor("Discapacidad");

        assertThat(adaptationFor("TEA").id()).isNotNull();
    }

    // The listing is what the preview prints, and it rebuilds each row through the same mapping.
    @Test
    void aListedAdaptationCarriesTheCondition() {
        adaptationFor("Discapacidad");

        assertThat(adaptationService.listByPlan(plan, PageQuery.of(0, 20)).content())
            .singleElement()
            .satisfies(a -> assertThat(a.conditionType()).isEqualTo("Discapacidad"));
    }
}
