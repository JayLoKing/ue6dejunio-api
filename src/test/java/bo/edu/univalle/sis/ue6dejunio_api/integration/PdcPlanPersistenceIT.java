package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plan against a real database. The service tests mock the port, so nothing until now proved
 * that a plan holding several subjects, each holding several weeks, can be read back at all —
 * fetching two collections in one query is exactly where Hibernate refuses.
 */
class PdcPlanPersistenceIT extends AbstractIntegrationTest {

    @Autowired private IPdcService pdcService;

    private UUID teacher;
    private UUID course;
    private UUID mathGroup;
    private UUID languageGroup;

    @BeforeEach
    void setUp() {
        teacher = seedUser("Teacher", false);
        course = seedCourse(teacher, "A");
        mathGroup = seedClassGroup(course, teacher, "Matematicas");
        languageGroup = seedClassGroup(course, teacher, "Lenguaje");
    }

    private CreatePdcCommand august() {
        return new CreatePdcCommand(course, 4, 2,
            LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 4),
            "Fortalecemos la práctica de valores sociocomunitarios.", null, null, null);
    }

    private UpsertPdcSubjectCommand twoWeeks(String objective) {
        return new UpsertPdcSubjectCommand(objective, "Material manipulable para ritmos distintos.",
            List.of(
                new UpsertPdcSubjectCommand.PdcEntryCommand("Semana 1", "T34: Conformación",
                    "Leemos", "Explicamos", "Valoramos", "Esquema", "Periódicos", 11,
                    "Respeta opiniones", "Reconoce la estructura", "Elabora un esquema", null),
                new UpsertPdcSubjectCommand.PdcEntryCommand("Semanas 3 y 4", "T35: La oración",
                    "Identificamos", "Explicamos", "Valoramos", "Redacción", "Cuaderno", 8,
                    "Muestra interés", "Reconoce las partes", "Redacta un párrafo", null)));
    }

    // Two subjects, two weeks each: both collections have to come back in one read, which is the
    // shape that throws MultipleBagFetchException when the mapping gets it wrong.
    @Test
    void readsBackAPlanHoldingSeveralSubjectsEachWithSeveralWeeks() {
        Pdc created = pdcService.create(august(), teacher, true);
        assertThat(created.getSubjects()).hasSize(2);

        for (var block : created.getSubjects()) {
            pdcService.writeSubject(created.getId(), block.id(),
                twoWeeks("Objetivo de " + block.subjectName()), teacher);
        }

        Pdc read = pdcService.getById(created.getId());

        assertThat(read.getStatus()).isEqualTo(PdcStatus.DRAFT);
        assertThat(read.getCourseId()).isEqualTo(course);
        assertThat(read.getSubjects()).hasSize(2);
        assertThat(read.getSubjects()).allSatisfy(block -> {
            assertThat(block.entries()).hasSize(2);
            assertThat(block.learningObjective()).startsWith("Objetivo de ");
            assertThat(block.knowledgeArea()).isNotBlank();
        });
        assertThat(read.getSubjects().get(0).entries().get(0).periods()).isEqualTo(11);
    }

    // The blocks print grouped by knowledge area, in the curriculum's own order — not alphabetical.
    // Comunidad y Sociedad comes before Ciencia Tecnología y Producción, so the order the plan
    // comes back in is part of what it holds rather than something the view rediscovers.
    @Test
    void keepsTheSubjectsInTheOrderTheyPrint() {
        Pdc created = pdcService.create(august(), teacher, true);

        List<String> areas = pdcService.getById(created.getId()).getSubjects().stream()
            .map(s -> s.knowledgeArea())
            .toList();
        List<Integer> orders = pdcService.getById(created.getId()).getSubjects().stream()
            .map(s -> s.displayOrder())
            .toList();

        assertThat(areas)
            .containsExactly("Comunidad y Sociedad", "Ciencia Tecnología y Producción");
        assertThat(orders).containsExactly(0, 1);
    }

    // Writing a block replaces its rows. A merge would leave a week the teacher removed behind.
    @Test
    void writingASubjectReplacesItsWeeksRatherThanAddingToThem() {
        Pdc created = pdcService.create(august(), teacher, true);
        UUID block = created.getSubjects().get(0).id();

        pdcService.writeSubject(created.getId(), block, twoWeeks("Primera versión"), teacher);
        pdcService.writeSubject(created.getId(), block,
            new UpsertPdcSubjectCommand("Segunda versión", null,
                List.of(new UpsertPdcSubjectCommand.PdcEntryCommand("Semana 1", "Solo una",
                    null, null, null, null, null, 2, null, null, null, null))),
            teacher);

        Pdc read = pdcService.getById(created.getId());
        var written = read.getSubjects().stream()
            .filter(s -> s.id().equals(block))
            .findFirst()
            .orElseThrow();

        assertThat(written.learningObjective()).isEqualTo("Segunda versión");
        assertThat(written.entries()).hasSize(1);
        assertThat(written.entries().get(0).contents()).isEqualTo("Solo una");
    }

    // The stepped form saves one step at a time. A step that only touches the objective sends no
    // rows at all, and collapsing that into "clear them" wiped the whole table — the same shape of
    // data loss as a refetch overwriting a draft.
    @Test
    void writingASubjectWithoutSendingRowsLeavesTheWeeksAlone() {
        Pdc created = pdcService.create(august(), teacher, true);
        UUID block = created.getSubjects().get(0).id();
        pdcService.writeSubject(created.getId(), block, twoWeeks("Con semanas"), teacher);

        pdcService.writeSubject(created.getId(), block,
            new UpsertPdcSubjectCommand("Solo cambio el objetivo", null, null), teacher);

        var written = pdcService.getById(created.getId()).getSubjects().stream()
            .filter(s -> s.id().equals(block))
            .findFirst()
            .orElseThrow();
        assertThat(written.learningObjective()).isEqualTo("Solo cambio el objetivo");
        assertThat(written.entries()).hasSize(2);
    }

    // An empty list is a different answer from no list: it says the teacher removed every week.
    @Test
    void writingASubjectWithAnEmptyRowListClearsTheWeeks() {
        Pdc created = pdcService.create(august(), teacher, true);
        UUID block = created.getSubjects().get(0).id();
        pdcService.writeSubject(created.getId(), block, twoWeeks("Con semanas"), teacher);

        pdcService.writeSubject(created.getId(), block,
            new UpsertPdcSubjectCommand("Sin semanas", null, List.of()), teacher);

        var written = pdcService.getById(created.getId()).getSubjects().stream()
            .filter(s -> s.id().equals(block))
            .findFirst()
            .orElseThrow();
        assertThat(written.entries()).isEmpty();
    }

    @Test
    void aSecondPlanOfTheSameTrimesterIsAccepted() {
        pdcService.create(august(), teacher, true);

        Pdc september = pdcService.create(new CreatePdcCommand(course, 5, 2,
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 2), null, null, null,
            List.of(mathGroup)), teacher, true);

        assertThat(september.getPlanNumber()).isEqualTo(5);
        assertThat(september.getSubjects()).hasSize(1);
        assertThat(september.getSubjects().get(0).classGroupId()).isEqualTo(mathGroup);
        assertThat(languageGroup).isNotNull();
    }
}
