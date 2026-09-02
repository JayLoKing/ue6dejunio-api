package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.mappers;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.criterion.EvaluationCriterion;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.level.Level;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.parallel.Parallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.progress.PlanProgress;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.subject.Subject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.trimesterperiod.TrimesterPeriod;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumAdaptationEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanProgressEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.EvaluationCriterionEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.GradeEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.KnowledgeAreaEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.LevelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.StudentEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.TrimesterPeriodEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the generated mappers actually produce.
 *
 * <p>These replaced hand-written {@code toDomain} methods in the adapters, and the whole risk of
 * that swap is a source path pointing at the wrong column — which compiles, and which no test that
 * mocks the port can see. So the assertions are field by field, and every mapper is also asked what
 * it does with the association missing: a plan row whose student was removed used to reach a
 * {@code getId()} on null.
 */
class RepositoryMapperTest {

    private final LevelMapper levelMapper = Mappers.getMapper(LevelMapper.class);
    private final ParallelMapper parallelMapper = Mappers.getMapper(ParallelMapper.class);
    private final GradeMapper gradeMapper = Mappers.getMapper(GradeMapper.class);
    private final SubjectMapper subjectMapper = Mappers.getMapper(SubjectMapper.class);
    private final CriterionMapper criterionMapper = Mappers.getMapper(CriterionMapper.class);
    private final TrimesterPeriodMapper trimesterPeriodMapper =
        Mappers.getMapper(TrimesterPeriodMapper.class);
    private final ProgressMapper progressMapper = Mappers.getMapper(ProgressMapper.class);
    private final AdaptationMapper adaptationMapper = Mappers.getMapper(AdaptationMapper.class);

    @Test
    void levelCarriesItsIdAndName() {
        LevelEntity entity = new LevelEntity();
        entity.setId(1);
        entity.setName("Primaria Comunitaria Vocacional");

        Level level = levelMapper.toDomain(entity);

        assertThat(level.id()).isEqualTo(1);
        assertThat(level.name()).isEqualTo("Primaria Comunitaria Vocacional");
    }

    @Test
    void parallelCarriesItsIdAndName() {
        ParallelEntity entity = new ParallelEntity();
        entity.setId(2);
        entity.setName("B");

        Parallel parallel = parallelMapper.toDomain(entity);

        assertThat(parallel.id()).isEqualTo(2);
        assertThat(parallel.name()).isEqualTo("B");
    }

    @Test
    void gradeNamesTheLevelItBelongsTo() {
        LevelEntity level = new LevelEntity();
        level.setId(1);
        level.setName("Primaria Comunitaria Vocacional");
        GradeEntity entity = new GradeEntity();
        entity.setId(5);
        entity.setName("Quinto");
        entity.setLevel(level);

        Grade grade = gradeMapper.toDomain(entity);

        assertThat(grade.id()).isEqualTo(5);
        assertThat(grade.name()).isEqualTo("Quinto");
        assertThat(grade.levelId()).isEqualTo(1);
        assertThat(grade.levelName()).isEqualTo("Primaria Comunitaria Vocacional");
    }

    @Test
    void gradeWithNoLevelSaysSoRatherThanBreaking() {
        GradeEntity entity = new GradeEntity();
        entity.setId(5);
        entity.setName("Quinto");

        Grade grade = gradeMapper.toDomain(entity);

        assertThat(grade.levelId()).isNull();
        assertThat(grade.levelName()).isNull();
    }

    @Test
    void subjectNamesItsAreaAndKeepsBothFlags() {
        KnowledgeAreaEntity area = new KnowledgeAreaEntity();
        area.setId(3);
        area.setName("Comunidad y Sociedad");
        SubjectEntity entity = new SubjectEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setName("Comunicacion y Lenguajes");
        entity.setArea(area);
        entity.setTechnical(true);
        entity.setActive(false);

        Subject subject = subjectMapper.toDomain(entity);

        assertThat(subject.id()).isEqualTo(id);
        assertThat(subject.name()).isEqualTo("Comunicacion y Lenguajes");
        assertThat(subject.areaId()).isEqualTo(3);
        assertThat(subject.areaName()).isEqualTo("Comunidad y Sociedad");
        assertThat(subject.technical()).isTrue();
        assertThat(subject.active()).isFalse();
    }

    @Test
    void subjectWithNoAreaSaysSoRatherThanBreaking() {
        SubjectEntity entity = new SubjectEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Musica");

        Subject subject = subjectMapper.toDomain(entity);

        assertThat(subject.areaId()).isNull();
        assertThat(subject.areaName()).isNull();
    }

    @Test
    void criterionCarriesItsGroupAndThePlanItAnswersTo() {
        UUID classGroupId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(classGroupId);
        CurriculumPlanEntity plan = new CurriculumPlanEntity();
        plan.setId(planId);
        EvaluationCriterionEntity entity = new EvaluationCriterionEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setClassGroup(classGroup);
        entity.setTrimester(2);
        entity.setDimension("SABER");
        entity.setName("Reconoce la estructura");
        entity.setActivityName("Exposicion");
        entity.setCurriculumPlan(plan);

        EvaluationCriterion criterion = criterionMapper.toDomain(entity);

        assertThat(criterion.id()).isEqualTo(id);
        assertThat(criterion.classGroupId()).isEqualTo(classGroupId);
        assertThat(criterion.trimester()).isEqualTo(2);
        assertThat(criterion.dimension()).isEqualTo("SABER");
        assertThat(criterion.name()).isEqualTo("Reconoce la estructura");
        assertThat(criterion.activityName()).isEqualTo("Exposicion");
        assertThat(criterion.curriculumPlanId()).isEqualTo(planId);
    }

    // A criterion the teacher wrote outside any plan. The column is nullable and most rows have it
    // empty, so this is the ordinary case rather than the odd one.
    @Test
    void criterionOutsideAnyPlanCarriesNoPlan() {
        ClassGroupEntity classGroup = new ClassGroupEntity();
        classGroup.setId(UUID.randomUUID());
        EvaluationCriterionEntity entity = new EvaluationCriterionEntity();
        entity.setId(UUID.randomUUID());
        entity.setClassGroup(classGroup);
        entity.setTrimester(1);
        entity.setDimension("SER");
        entity.setName("Respeta opiniones");

        EvaluationCriterion criterion = criterionMapper.toDomain(entity);

        assertThat(criterion.curriculumPlanId()).isNull();
        assertThat(criterion.activityName()).isNull();
    }

    @Test
    void trimesterPeriodCarriesItsYearAndItsRange() {
        Integer yearId = 2026;
        AcademicYearEntity year = new AcademicYearEntity();
        year.setId(yearId);
        TrimesterPeriodEntity entity = new TrimesterPeriodEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setAcademicYear(year);
        entity.setTrimester(3);
        entity.setStartDate(LocalDate.of(2026, 8, 3));
        entity.setEndDate(LocalDate.of(2026, 11, 30));

        TrimesterPeriod period = trimesterPeriodMapper.toDomain(entity);

        assertThat(period.id()).isEqualTo(id);
        assertThat(period.academicYearId()).isEqualTo(yearId);
        assertThat(period.trimester()).isEqualTo(3);
        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void progressCarriesThePlanItAdvances() {
        UUID planId = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        CurriculumPlanEntity plan = new CurriculumPlanEntity();
        plan.setId(planId);
        CurriculumPlanProgressEntity entity = new CurriculumPlanProgressEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setCurriculumPlan(plan);
        entity.setProgressDate(LocalDate.of(2026, 8, 20));
        entity.setAdvancedContent("Semanas 1 y 2");
        entity.setPercentage(new BigDecimal("45.50"));
        entity.setObservations("Se retraso por el feriado");
        entity.setCreatedBy(author);
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 20, 9, 0));

        PlanProgress progress = progressMapper.toDomain(entity);

        assertThat(progress.id()).isEqualTo(id);
        assertThat(progress.planId()).isEqualTo(planId);
        assertThat(progress.progressDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(progress.advancedContent()).isEqualTo("Semanas 1 y 2");
        assertThat(progress.percentage()).isEqualByComparingTo("45.50");
        assertThat(progress.observations()).isEqualTo("Se retraso por el feriado");
        assertThat(progress.createdBy()).isEqualTo(author);
        assertThat(progress.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 0));
    }

    @Test
    void adaptationNamesTheStudentItAnswersTo() {
        UUID planId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        CurriculumPlanEntity plan = new CurriculumPlanEntity();
        plan.setId(planId);
        StudentEntity student = new StudentEntity();
        student.setId(studentId);
        student.setNames("Juan Carlos");
        student.setLastNames("Vargas Rojas");
        UserEntity author = new UserEntity();
        author.setId(authorId);
        UserEntity editor = new UserEntity();
        editor.setId(editorId);
        CurriculumAdaptationEntity entity = new CurriculumAdaptationEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setCurriculumPlan(plan);
        entity.setStudent(student);
        entity.setConditionType("TEA");
        entity.setAdaptedContents("Numeros hasta el 20");
        entity.setAdaptedMethodology("Material concreto");
        entity.setAdaptedCriteria("Cuenta con apoyo");
        entity.setCreatedBy(author);
        entity.setUpdatedBy(editor);
        entity.setCreatedAt(LocalDateTime.of(2026, 8, 3, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 8, 4, 11, 0));

        Adaptation adaptation = adaptationMapper.toDomain(entity);

        assertThat(adaptation.id()).isEqualTo(id);
        assertThat(adaptation.planId()).isEqualTo(planId);
        assertThat(adaptation.studentId()).isEqualTo(studentId);
        assertThat(adaptation.studentName()).isEqualTo("Juan Carlos Vargas Rojas");
        assertThat(adaptation.conditionType()).isEqualTo("TEA");
        assertThat(adaptation.adaptedContents()).isEqualTo("Numeros hasta el 20");
        assertThat(adaptation.adaptedMethodology()).isEqualTo("Material concreto");
        assertThat(adaptation.adaptedCriteria()).isEqualTo("Cuenta con apoyo");
        assertThat(adaptation.createdById()).isEqualTo(authorId);
        assertThat(adaptation.updatedById()).isEqualTo(editorId);
        assertThat(adaptation.createdAt()).isEqualTo(LocalDateTime.of(2026, 8, 3, 10, 0));
        assertThat(adaptation.updatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 4, 11, 0));
    }

    // The name is two columns joined, so it is the one field here that is neither a copy nor an id.
    // With no student there is no name to build, and "null null" is what a careless join produces.
    @Test
    void adaptationWithNoStudentCarriesNoName() {
        CurriculumAdaptationEntity entity = new CurriculumAdaptationEntity();
        entity.setId(UUID.randomUUID());
        entity.setAdaptedContents("Numeros hasta el 20");

        Adaptation adaptation = adaptationMapper.toDomain(entity);

        assertThat(adaptation.studentId()).isNull();
        assertThat(adaptation.studentName()).isNull();
        assertThat(adaptation.planId()).isNull();
        assertThat(adaptation.createdById()).isNull();
        assertThat(adaptation.updatedById()).isNull();
    }
}
