package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The plan against a real database. The service tests mock the port, so nothing until now proved
 * that a plan holding several subjects, each holding several weeks, can be read back at all —
 * fetching two collections in one query is exactly where Hibernate refuses.
 */
class PdcPlanPersistenceIT extends AbstractIntegrationTest {

    @Autowired private IPdcService pdcService;
    @Autowired private IAdaptationService adaptationService;

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

    private String fullNameOf(UUID userId) {
        return jdbc.queryForObject(
            "SELECT names || ' ' || last_names FROM users WHERE id_user = ?", String.class, userId);
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
                    "Respeta opiniones", "Reconoce la estructura", "Elabora un esquema"),
                new UpsertPdcSubjectCommand.PdcEntryCommand("Semanas 3 y 4", "T35: La oración",
                    "Identificamos", "Explicamos", "Valoramos", "Redacción", "Cuaderno", 8,
                    "Muestra interés", "Reconoce las partes", "Redacta un párrafo")));
    }

    // Two subjects, two weeks each: both collections have to come back in one read, which is the
    // shape that throws MultipleBagFetchException when the mapping gets it wrong.
    @Test
    void readsBackAPlanHoldingSeveralSubjectsEachWithSeveralWeeks() {
        Pdc created = pdcService.create(august(), teacher);
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
        Pdc created = pdcService.create(august(), teacher);

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
        Pdc created = pdcService.create(august(), teacher);
        UUID block = created.getSubjects().get(0).id();

        pdcService.writeSubject(created.getId(), block, twoWeeks("Primera versión"), teacher);
        pdcService.writeSubject(created.getId(), block,
            new UpsertPdcSubjectCommand("Segunda versión", null,
                List.of(new UpsertPdcSubjectCommand.PdcEntryCommand("Semana 1", "Solo una",
                    null, null, null, null, null, 2, null, null, null))),
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
        Pdc created = pdcService.create(august(), teacher);
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
        Pdc created = pdcService.create(august(), teacher);
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

    // The printed form heads itself with the level, which lives two hops away from the plan
    // (plan → course → grade → level) and had no way of reaching the document until now.
    @Test
    void carriesTheLevelTheCourseBelongsTo() {
        Pdc created = pdcService.create(august(), teacher);

        assertThat(created.getLevelName()).isEqualTo("Primaria Comunitaria Vocacional");
        assertThat(pdcService.getById(created.getId()).getLevelName())
            .isEqualTo("Primaria Comunitaria Vocacional");
    }

    // A listing row says how wide the plan is without carrying it. The two numbers come from their
    // own grouped queries, so a plan whose blocks were never read still reports them.
    @Test
    void aListingRowCountsTheAreasAndTheSignificantAdaptations() {
        Pdc created = pdcService.create(august(), teacher);

        Pdc row = pdcService.list(null, null, null, teacher, PageQuery.of(0, 20))
            .content().stream()
            .filter(p -> p.getId().equals(created.getId()))
            .findFirst()
            .orElseThrow();

        // Matemáticas and Lenguaje sit in two different areas of knowledge.
        assertThat(row.getAreaCount()).isEqualTo(2);
        assertThat(row.getSignificantAdaptationCount()).isZero();
        // The row carries no blocks — the count is what says how wide it is.
        assertThat(row.getSubjects()).isEmpty();
    }

    /**
     * Every field of the plan survives a write, checked over the whole class rather than one field
     * at a time.
     *
     * <p>A write does not re-read the document: it rebuilds its answer with
     * {@code toHeader(saved).toBuilder()} and copies across what the caller already held. Anything
     * the rebuild forgets falls back to its {@code @Builder.Default} — silently, and only on the
     * write path, so a publish answers with a plan that a read of the same plan contradicts. That
     * defect has now shipped twice, with the teacher names and with the counts, and both times the
     * suite missed it because every other test re-reads after publishing instead of looking at what
     * the publish returned.
     *
     * <p>Walking the fields is what makes this close the whole class: the next field added to
     * {@link Pdc} fails here until {@code save} carries it, without anyone remembering to come back.
     */
    @Test
    void everyFieldOfThePlanSurvivesAWrite() throws IllegalAccessException {
        // Fields the write is meant to change: it stamps who wrote and when, so the answer differing
        // from a later read is the point rather than a loss.
        Set<String> writtenByTheSaveItself = Set.of("updatedAt", "updatedById", "updatedByName");

        Pdc created = pdcService.create(august(), teacher);
        pdcService.writeSubject(created.getId(), created.getSubjects().get(0).id(),
            twoWeeks("Objetivo del mes"), teacher);
        adaptationService.create(new CreateAdaptationCommand(created.getId(), seedStudent(),
            "Discapacidad", "Contenido adaptado", "Metodología adaptada", "Criterio adaptado",
            teacher));

        Pdc answeredByTheWrite = pdcService.publish(created.getId(), teacher);
        Pdc read = pdcService.getById(created.getId());

        for (Field field : Pdc.class.getDeclaredFields()) {
            if (field.isSynthetic() || writtenByTheSaveItself.contains(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            assertThat(field.get(answeredByTheWrite))
                .as("El campo '%s' se pierde al escribir: save() no lo copia y vuelve a su valor "
                    + "por defecto, así que un publish contradice una lectura del mismo plan",
                    field.getName())
                .isEqualTo(field.get(read));
        }
    }

    // The write path answers from what the caller already held rather than re-reading the plan.
    // The counts have to survive that shortcut, or a publish contradicts a read of the same plan.
    @Test
    void aStatusChangeKeepsTheCountsItWasGiven() {
        Pdc created = pdcService.create(august(), teacher);
        Pdc read = pdcService.getById(created.getId());
        assertThat(read.getAreaCount()).isEqualTo(2);

        Pdc published = pdcService.publish(read.getId(), teacher);

        assertThat(published.getAreaCount()).isEqualTo(read.getAreaCount());
        assertThat(published.getSignificantAdaptationCount())
            .isEqualTo(read.getSignificantAdaptationCount());
    }

    // A draft is a teacher's unfinished month. The Director reviews what was handed in, so an
    // unscoped listing leaves drafts out; the teacher who owns it still sees it in theirs.
    @Test
    void anUnscopedListingLeavesTheDraftsOut() {
        Pdc draft = pdcService.create(august(), teacher);

        List<UUID> unscoped = pdcService
            .list(null, null, null, null, PageQuery.of(0, 20))
            .content().stream().map(Pdc::getId).toList();
        List<UUID> owners = pdcService
            .list(null, null, null, teacher, PageQuery.of(0, 20))
            .content().stream().map(Pdc::getId).toList();

        assertThat(unscoped).doesNotContain(draft.getId());
        assertThat(owners).contains(draft.getId());
    }

    // The Director opens no plans: they review what the teachers publish. Reaching create at all
    // would mean a month planned from an office, by someone who teaches none of it.
    @Test
    void aDirectorCannotOpenAPlan() {
        UUID director = seedUser("Director", false);

        assertThatThrownBy(() -> pdcService.create(august(), director))
            .isInstanceOf(ConflictException.class);
    }

    // A status flip answers with the plan, and the heading it carries has to be the one a read of
    // the same plan would give. The write path builds its answer without re-reading the document,
    // so the printed "Maestro/a" line has to survive that shortcut.
    @Test
    void aStatusChangeAnswersWithTheTeacherToo() {
        Pdc created = pdcService.create(august(), teacher);

        Pdc published = pdcService.publish(created.getId(), teacher);

        assertThat(published.getHomeroomTeacherName()).isEqualTo(fullNameOf(teacher));
    }

    // The subjects somebody else runs are that teacher's to plan. Opening them here would file
    // their month under this teacher's name, and the printed heading would claim they teach it.
    @Test
    void leavesOutTheSubjectsTheAuthorDoesNotTeach() {
        UUID specialist = seedUser("Teacher", true);
        jdbc.update("UPDATE class_groups SET id_teacher = ? WHERE id_class_group = ?",
            specialist, languageGroup);

        Pdc plan = pdcService.create(august(), teacher);

        assertThat(plan.getSubjects()).hasSize(1);
        assertThat(plan.getSubjects().get(0).classGroupId()).isEqualTo(mathGroup);
    }

    // "Maestro/a" is the teacher in charge of the course, however many blocks the plan holds.
    @Test
    void namesTheCoursesTeacherOnceHoweverManySubjects() {
        Pdc plan = pdcService.create(august(), teacher);

        assertThat(plan.getSubjects()).hasSize(2);
        assertThat(plan.getHomeroomTeacherName()).isEqualTo(fullNameOf(teacher));
    }

    // The rotation: one teacher of the grade writes the month, the parallels copy it. What the copy
    // carries is the planning; the teacher it lands on is the one who runs that parallel.
    @Test
    void aCopyNamesTheTeacherOfTheParallelItLandsOn() {
        UUID otherTeacher = seedUser("Teacher", false);
        UUID parallelB = seedCourse(otherTeacher, "B");
        seedClassGroup(parallelB, otherTeacher, "Matematicas");
        seedClassGroup(parallelB, otherTeacher, "Lenguaje");

        Pdc original = pdcService.create(august(), teacher);
        pdcService.publish(original.getId(), teacher);
        List<Pdc> copies = pdcService.copyToSiblingCourses(original.getId(), teacher);

        assertThat(copies).hasSize(1);
        assertThat(pdcService.getById(original.getId()).getHomeroomTeacherName())
            .isEqualTo(fullNameOf(teacher));
        assertThat(pdcService.getById(copies.get(0).getId()).getHomeroomTeacherName())
            .isEqualTo(fullNameOf(otherTeacher));
    }

    // Adaptations answer to the students in front of one teacher: which of them needs the content
    // broken down, who needs longer. Carrying them into a parallel would hand a teacher strategies
    // written for children who are not in their classroom.
    @Test
    void aCopyDoesNotCarryTheAdaptationsWrittenForAnotherClassroom() {
        UUID otherTeacher = seedUser("Teacher", false);
        UUID parallelB = seedCourse(otherTeacher, "B");
        seedClassGroup(parallelB, otherTeacher, "Matematicas");
        seedClassGroup(parallelB, otherTeacher, "Lenguaje");

        Pdc original = pdcService.create(august(), teacher);
        UUID block = original.getSubjects().get(0).id();
        pdcService.writeSubject(original.getId(), block, twoWeeks("Objetivo del mes"), teacher);
        pdcService.publish(original.getId(), teacher);

        List<Pdc> copies = pdcService.copyToSiblingCourses(original.getId(), teacher);
        Pdc copy = pdcService.getById(copies.get(0).getId());

        assertThat(pdcService.getById(original.getId()).getSubjects())
            .anySatisfy(s -> assertThat(s.generalAdaptations()).isNotBlank());
        assertThat(copy.getSubjects()).allSatisfy(s ->
            assertThat(s.generalAdaptations()).isNull());
        // The planning itself does travel — it is the reason the rotation exists.
        assertThat(copy.getSubjects()).anySatisfy(s -> {
            assertThat(s.learningObjective()).isEqualTo("Objetivo del mes");
            assertThat(s.entries()).hasSize(2);
        });
    }

    @Test
    void aSecondPlanOfTheSameTrimesterIsAccepted() {
        pdcService.create(august(), teacher);

        Pdc september = pdcService.create(new CreatePdcCommand(course, 5, 2,
            LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 2), null, null, null,
            List.of(mathGroup)), teacher);

        assertThat(september.getPlanNumber()).isEqualTo(5);
        assertThat(september.getSubjects()).hasSize(1);
        assertThat(september.getSubjects().get(0).classGroupId()).isEqualTo(mathGroup);
        assertThat(languageGroup).isNotNull();
    }
}
