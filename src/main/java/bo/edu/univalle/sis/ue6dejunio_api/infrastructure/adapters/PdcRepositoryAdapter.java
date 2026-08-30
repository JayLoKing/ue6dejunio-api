package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcEntry;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcSubject;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CourseEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanEntryEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.CurriculumPlanSubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumAdaptationRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCurriculumPlanSubjectRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class PdcRepositoryAdapter implements IPdcDomain {

    private final JpaCurriculumPlanRepository planRepo;
    private final JpaCurriculumPlanSubjectRepository planSubjectRepo;
    private final JpaClassGroupRepository classGroupRepo;
    private final JpaCourseRepository courseRepo;
    private final JpaUserRepository userRepo;
    private final JpaCurriculumAdaptationRepository adaptationRepo;

    public PdcRepositoryAdapter(JpaCurriculumPlanRepository planRepo,
                                JpaCurriculumPlanSubjectRepository planSubjectRepo,
                                JpaClassGroupRepository classGroupRepo,
                                JpaCourseRepository courseRepo,
                                JpaUserRepository userRepo,
                                JpaCurriculumAdaptationRepository adaptationRepo) {
        this.planRepo = planRepo;
        this.planSubjectRepo = planSubjectRepo;
        this.classGroupRepo = classGroupRepo;
        this.courseRepo = courseRepo;
        this.userRepo = userRepo;
        this.adaptationRepo = adaptationRepo;
    }

    @Override
    @Transactional
    public Pdc save(Pdc pdc) {
        // The header alone. Saving a status flip or a renumber touches no block, and reading the
        // whole document again — the caller already read it to get here — pulled every weekly row
        // a second time to write one column.
        CurriculumPlanEntity e = pdc.getId() == null
            ? new CurriculumPlanEntity()
            : planRepo.findById(pdc.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PDC", pdc.getId()));

        if (pdc.getId() == null) {
            CourseEntity course = courseRepo.findById(pdc.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", pdc.getCourseId()));
            e.setCourse(course);
            e.setPlanNumber(pdc.getPlanNumber());
            e.setTrimester(pdc.getTrimester());
            e.setCreatedAt(LocalDateTime.now());
            if (pdc.getCreatedById() != null) {
                e.setCreatedBy(userRepo.getReferenceById(pdc.getCreatedById()));
            }
            if (pdc.getSourcePlanId() != null) {
                e.setSourcePlan(planRepo.getReferenceById(pdc.getSourcePlanId()));
            }
        } else if (pdc.getPlanNumber() != null) {
            e.setPlanNumber(pdc.getPlanNumber());
        }

        e.setPeriodStart(pdc.getPeriodStart());
        e.setPeriodEnd(pdc.getPeriodEnd());
        e.setStatus(pdc.getStatus());
        e.setReviewObservations(pdc.getReviewObservations());
        e.setHolisticObjective(pdc.getHolisticObjective());
        e.setFinalProduct(pdc.getFinalProduct());
        e.setBibliography(pdc.getBibliography());
        if (pdc.getUpdatedById() != null) {
            e.setUpdatedBy(userRepo.getReferenceById(pdc.getUpdatedById()));
        }
        e.setUpdatedAt(LocalDateTime.now());
        CurriculumPlanEntity saved = planRepo.save(e);
        // The blocks come back from what the caller already held rather than from a fresh read:
        // this write never touched them, and re-reading them is what made a status flip cost four
        // queries. A plan created here has none yet.
        //
        // The counts ride along for the same reason the blocks do. This write touched neither the
        // subjects nor the adaptations, so what the caller already knew is still true — and letting
        // the builder default them to zero made a publish answer with a heading a read of the same
        // plan contradicts.
        return toHeader(saved).toBuilder()
            .subjects(pdc.getSubjects() == null ? List.of() : pdc.getSubjects())
            .areaCount(pdc.getAreaCount())
            .significantAdaptationCount(pdc.getSignificantAdaptationCount())
            .build();
    }

    @Override
    public Optional<Pdc> findById(UUID id) {
        Optional<CurriculumPlanEntity> plan = planRepo.findWithContent(id);
        if (plan.isPresent()) {
            planSubjectRepo.fetchEntriesOfPlan(id);
        }
        return plan.map(this::toDomain);
    }

    @Override
    public Optional<String> statusOf(UUID planId) {
        return planRepo.findStatusById(planId);
    }

    @Override
    public boolean courseExists(UUID courseId) {
        return courseRepo.existsById(courseId);
    }

    @Override
    public boolean existsByCoursePlanNumber(UUID courseId, Integer trimester, Integer planNumber) {
        return planRepo.existsByCourse_IdAndTrimesterAndPlanNumber(courseId, trimester, planNumber);
    }

    @Override
    public List<UUID> classGroupIdsTaughtBy(UUID courseId, UUID teacherId) {
        if (teacherId == null) {
            return List.of();
        }
        return classGroupRepo.findActiveOfCourseInPlanOrder(courseId).stream()
            .filter(cg -> cg.getTeacher() != null && teacherId.equals(cg.getTeacher().getId()))
            .map(ClassGroupEntity::getId)
            .toList();
    }

    @Override
    @Transactional
    public Pdc addSubjects(UUID planId, List<UUID> classGroupIds) {
        CurriculumPlanEntity plan = load(planId);
        // A homeroom plan opens a dozen blocks at once, so the groups are read in one query and
        // the loop only places them. Reading inside the loop cost one round trip per subject, and
        // the graphed finder keeps the EAGER subject, area and teacher out of a select each.
        Map<UUID, ClassGroupEntity> byId = classGroupRepo.findByIdIn(classGroupIds).stream()
            .collect(Collectors.toMap(ClassGroupEntity::getId, cg -> cg));
        int order = plan.getSubjects().size();
        for (UUID classGroupId : classGroupIds) {
            ClassGroupEntity cg = byId.get(classGroupId);
            if (cg == null) {
                throw new ResourceNotFoundException("ClassGroup", classGroupId);
            }
            CurriculumPlanSubjectEntity block = new CurriculumPlanSubjectEntity();
            block.setCurriculumPlan(plan);
            block.setClassGroup(cg);
            block.setDisplayOrder(order++);
            plan.getSubjects().add(block);
        }
        // A plan whose blocks were just opened holds no adaptations yet, so the count is known
        // without asking.
        return toDomain(planRepo.save(plan), 0);
    }

    @Override
    @Transactional
    public Pdc writeSubject(UUID planId, UUID planSubjectId, UpsertPdcSubjectCommand command,
                            UUID currentUserId) {
        CurriculumPlanEntity plan = load(planId);
        CurriculumPlanSubjectEntity block = plan.getSubjects().stream()
            .filter(s -> s.getId().equals(planSubjectId))
            .findFirst()
            // Looked up inside the plan on purpose: a block id belonging to another plan must read
            // as missing here, not as someone else's block quietly rewritten.
            .orElseThrow(() -> new ResourceNotFoundException("Bloque de materia del PDC", planSubjectId));

        block.setLearningObjective(command.learningObjective());
        block.setGeneralAdaptations(command.generalAdaptations());

        // No rows given at all means the caller was not writing the table — a step that only
        // touched the objective leaves the weeks exactly where they were. Rows given replace the
        // ones held, because a merge would leave a week the teacher deleted behind.
        if (command.entries() == null) {
            plan.setUpdatedAt(LocalDateTime.now());
            if (currentUserId != null) {
                plan.setUpdatedBy(userRepo.getReferenceById(currentUserId));
            }
            return toDomain(planRepo.save(plan));
        }

        block.getEntries().clear();
        List<UpsertPdcSubjectCommand.PdcEntryCommand> rows = command.entries();
        int order = 0;
        for (UpsertPdcSubjectCommand.PdcEntryCommand row : rows) {
            CurriculumPlanEntryEntity entry = new CurriculumPlanEntryEntity();
            entry.setPlanSubject(block);
            entry.setWeekLabel(row.weekLabel());
            entry.setContents(row.contents());
            entry.setPractice(row.practice());
            entry.setTheory(row.theory());
            entry.setValuation(row.valuation());
            entry.setProduction(row.production());
            entry.setResources(row.resources());
            entry.setPeriods(row.periods());
            entry.setCriteriaBeing(row.criteriaBeing());
            entry.setCriteriaKnowing(row.criteriaKnowing());
            entry.setCriteriaDoing(row.criteriaDoing());
            entry.setDisplayOrder(order++);
            block.getEntries().add(entry);
        }
        if (currentUserId != null) {
            plan.setUpdatedBy(userRepo.getReferenceById(currentUserId));
        }
        plan.setUpdatedAt(LocalDateTime.now());
        return toDomain(planRepo.save(plan));
    }

    @Override
    public List<UUID> siblingCourseIdsOf(UUID courseId) {
        return courseRepo.findSiblingCourseIds(courseId);
    }

    @Override
    public Set<UUID> courseIdsWithPlan(List<UUID> courseIds, Integer trimester, Integer planNumber) {
        return courseIds.isEmpty()
            ? Set.of()
            : planRepo.courseIdsWithPlan(courseIds, trimester, planNumber);
    }

    @Override
    @Transactional
    public List<Pdc> copyTo(UUID sourcePlanId, List<UUID> targetCourseIds, UUID currentUserId) {
        // Read once for the whole rotation. A JPQL query goes to the database every time it is
        // issued — the first-level cache resolves the rows it returns, it does not skip the query.
        CurriculumPlanEntity source = load(sourcePlanId);
        // The receiving courses and their subjects are read together too: one query each for the
        // whole rotation rather than two per parallel. The courses come through the graphed finder
        // because their grade, parallel, year and homeroom teacher are EAGER and the printed
        // heading reads all four — findAllById would resolve them one select at a time.
        Map<UUID, CourseEntity> targetsById = courseRepo.findByIdIn(targetCourseIds).stream()
            .collect(Collectors.toMap(CourseEntity::getId, c -> c));
        Map<UUID, List<ClassGroupEntity>> groupsByCourse =
            classGroupRepo.findActiveOfCoursesInPlanOrder(targetCourseIds).stream()
                .collect(Collectors.groupingBy(cg -> cg.getCourse().getId()));

        List<Pdc> copies = new ArrayList<>();
        for (UUID targetCourseId : targetCourseIds) {
            CourseEntity target = targetsById.get(targetCourseId);
            if (target == null) {
                throw new ResourceNotFoundException("Course", targetCourseId);
            }
            copies.add(copyInto(source, target,
                groupsByCourse.getOrDefault(targetCourseId, List.of()), currentUserId));
        }
        return copies;
    }

    private Pdc copyInto(CurriculumPlanEntity source, CourseEntity target,
                         List<ClassGroupEntity> targetGroups, UUID currentUserId) {
        CurriculumPlanEntity copy = new CurriculumPlanEntity();
        copy.setCourse(target);
        copy.setPlanNumber(source.getPlanNumber());
        copy.setTrimester(source.getTrimester());
        copy.setPeriodStart(source.getPeriodStart());
        copy.setPeriodEnd(source.getPeriodEnd());
        copy.setHolisticObjective(source.getHolisticObjective());
        copy.setFinalProduct(source.getFinalProduct());
        copy.setBibliography(source.getBibliography());
        // A copy starts as a draft even when the plan it came from was approved: the receiving
        // teacher adjusts it for their own course, and approval covers what was reviewed.
        copy.setStatus(PdcStatus.DRAFT);
        copy.setSourcePlan(source);
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        if (currentUserId != null) {
            copy.setCreatedBy(userRepo.getReferenceById(currentUserId));
            copy.setUpdatedBy(userRepo.getReferenceById(currentUserId));
        }

        // The blocks are rebuilt against the target course's own class groups: the same subject is
        // a different class group there, with a different teacher. Copying the source's ids would
        // have hung another course's subjects off this plan.
        int order = 0;
        for (CurriculumPlanSubjectEntity sourceBlock : source.getSubjects()) {
            UUID sourceSubjectId = sourceBlock.getClassGroup().getSubject().getId();
            Optional<ClassGroupEntity> match = targetGroups.stream()
                .filter(cg -> cg.getSubject().getId().equals(sourceSubjectId))
                .findFirst();
            // A subject the target course does not teach is skipped rather than invented.
            if (match.isEmpty()) {
                continue;
            }
            CurriculumPlanSubjectEntity block = new CurriculumPlanSubjectEntity();
            block.setCurriculumPlan(copy);
            block.setClassGroup(match.get());
            block.setLearningObjective(sourceBlock.getLearningObjective());
            // The adaptations stay behind. They answer to the students in one classroom — who needs
            // the content broken down, who needs longer — and the teacher receiving the copy has
            // other children in front of them. The planning travels; the reading of a class does not.
            block.setDisplayOrder(order++);
            for (CurriculumPlanEntryEntity sourceEntry : sourceBlock.getEntries()) {
                CurriculumPlanEntryEntity entry = new CurriculumPlanEntryEntity();
                entry.setPlanSubject(block);
                entry.setWeekLabel(sourceEntry.getWeekLabel());
                entry.setContents(sourceEntry.getContents());
                entry.setPractice(sourceEntry.getPractice());
                entry.setTheory(sourceEntry.getTheory());
                entry.setValuation(sourceEntry.getValuation());
                entry.setProduction(sourceEntry.getProduction());
                entry.setResources(sourceEntry.getResources());
                entry.setPeriods(sourceEntry.getPeriods());
                entry.setCriteriaBeing(sourceEntry.getCriteriaBeing());
                entry.setCriteriaKnowing(sourceEntry.getCriteriaKnowing());
                entry.setCriteriaDoing(sourceEntry.getCriteriaDoing());
                entry.setDisplayOrder(sourceEntry.getDisplayOrder());
                block.getEntries().add(entry);
            }
            copy.getSubjects().add(block);
        }
        // The significant adaptations are deliberately not copied: they name a student of the
        // source course, and whether the receiving course has such a student is its own question.
        // The adaptations are deliberately left behind by the rotation, so a fresh copy has none.
        // Asking would spend a query per parallel to be told zero.
        return toDomain(planRepo.save(copy), 0);
    }

    @Override
    public boolean hasCopies(UUID planId) {
        return planRepo.existsBySourcePlan_Id(planId);
    }

    @Override
    public Set<UUID> writerIdsOf(UUID planId) {
        return planRepo.writerIdsOf(planId);
    }

    @Override
    public Set<UUID> subjectWriterIdsOf(UUID planId, UUID planSubjectId) {
        return planRepo.subjectWriterIdsOf(planId, planSubjectId);
    }

    @Override
    public Set<UUID> administratorIdsOf(UUID planId) {
        return planRepo.administratorIdsOf(planId);
    }

    @Override
    public PageResult<Pdc> list(UUID courseId, Integer trimester, String status,
                                String excludeStatus, UUID teacherId, PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        Page<Pdc> page = planRepo
            .search(courseId, trimester, status, excludeStatus, teacherId, pageable)
            .map(this::toHeader);

        // Two grouped queries for the whole page rather than two per row. The listing fetches no
        // blocks and no adaptations, so counting off the entities would mean loading both
        // collections for every plan on screen just to print a number.
        List<UUID> ids = page.getContent().stream().map(Pdc::getId).toList();
        if (!ids.isEmpty()) {
            Map<UUID, Long> areas = countsByPlan(planRepo.areaCountsOf(ids));
            Map<UUID, Long> adaptations = countsByPlan(adaptationRepo.countsByPlan(ids));
            for (Pdc row : page.getContent()) {
                row.setAreaCount(areas.getOrDefault(row.getId(), 0L).intValue());
                row.setSignificantAdaptationCount(
                    adaptations.getOrDefault(row.getId(), 0L).intValue());
            }
        }
        return SpringPaging.toPageResult(page);
    }

    private static Map<UUID, Long> countsByPlan(
        List<JpaCurriculumPlanRepository.PlanCount> counts) {
        return counts.stream().collect(Collectors.toMap(
            JpaCurriculumPlanRepository.PlanCount::getPlanId,
            JpaCurriculumPlanRepository.PlanCount::getTotal));
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        planRepo.deleteById(id);
    }

    private CurriculumPlanEntity load(UUID id) {
        CurriculumPlanEntity plan = planRepo.findWithContent(id)
            .orElseThrow(() -> new ResourceNotFoundException("PDC", id));
        // The blocks arrived with the plan; this brings their weekly rows into the same
        // persistence context. Hibernate refuses to join-fetch both ordered collections at once.
        planSubjectRepo.fetchEntriesOfPlan(id);
        return plan;
    }

    /** The plan without its blocks — what a listing row needs and all the listing query fetched. */
    private Pdc toHeader(CurriculumPlanEntity e) {
        CourseEntity c = e.getCourse();
        UserEntity homeroom = c != null ? c.getHomeroomTeacher() : null;
        return Pdc.builder()
            .id(e.getId())
            .courseId(c != null ? c.getId() : null)
            .gradeName(c != null && c.getGrade() != null ? c.getGrade().getName() : null)
            .parallelName(c != null && c.getParallel() != null ? c.getParallel().getName() : null)
            .levelName(levelName(c))
            .courseName(courseName(c))
            .homeroomTeacherId(homeroom != null ? homeroom.getId() : null)
            .homeroomTeacherName(fullName(homeroom))
            .planNumber(e.getPlanNumber())
            .trimester(e.getTrimester())
            .periodStart(e.getPeriodStart())
            .periodEnd(e.getPeriodEnd())
            .status(e.getStatus())
            .reviewObservations(e.getReviewObservations())
            .holisticObjective(e.getHolisticObjective())
            .finalProduct(e.getFinalProduct())
            .bibliography(e.getBibliography())
            .sourcePlanId(e.getSourcePlan() != null ? e.getSourcePlan().getId() : null)
            .createdById(e.getCreatedBy() != null ? e.getCreatedBy().getId() : null)
            .updatedById(e.getUpdatedBy() != null ? e.getUpdatedBy().getId() : null)
            .updatedByName(fullName(e.getUpdatedBy()))
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }

    /**
     * The plan whole, asking the database how many significant adaptations hang off it.
     *
     * <p>Callers holding a plan that cannot have any — one just created, one just copied — take
     * {@link #toDomain(CurriculumPlanEntity, int)} instead, so the rotation does not spend a query
     * per parallel to be told zero.
     */
    private Pdc toDomain(CurriculumPlanEntity e) {
        return toDomain(e, (int) adaptationRepo.countByCurriculumPlan_Id(e.getId()));
    }

    private Pdc toDomain(CurriculumPlanEntity e, int significantAdaptationCount) {
        List<PdcSubject> blocks = new ArrayList<>();
        for (CurriculumPlanSubjectEntity s : e.getSubjects()) {
            ClassGroupEntity cg = s.getClassGroup();
            List<PdcEntry> rows = s.getEntries().stream()
                .map(entry -> new PdcEntry(
                    entry.getId(),
                    entry.getWeekLabel(),
                    entry.getContents(),
                    entry.getPractice(),
                    entry.getTheory(),
                    entry.getValuation(),
                    entry.getProduction(),
                    entry.getResources(),
                    entry.getPeriods(),
                    entry.getCriteriaBeing(),
                    entry.getCriteriaKnowing(),
                    entry.getCriteriaDoing(),
                    entry.getDisplayOrder()))
                .toList();
            blocks.add(new PdcSubject(
                s.getId(),
                cg != null ? cg.getId() : null,
                cg != null && cg.getSubject() != null ? cg.getSubject().getName() : null,
                cg != null && cg.getSubject() != null && cg.getSubject().getArea() != null
                    ? cg.getSubject().getArea().getName() : null,
                cg != null && cg.getTeacher() != null ? cg.getTeacher().getId() : null,
                cg != null ? fullName(cg.getTeacher()) : null,
                s.getLearningObjective(),
                s.getGeneralAdaptations(),
                s.getDisplayOrder(),
                rows));
        }
        // The detail already holds the blocks, so the areas are counted off them rather than asked
        // for again. The adaptations are not in the document — they hang off the plan — so they
        // arrive from the caller, which knows whether asking was worth a query.
        return toHeader(e).toBuilder()
            .subjects(blocks)
            .areaCount((int) blocks.stream()
                .map(PdcSubject::knowledgeArea)
                .filter(Objects::nonNull)
                .distinct()
                .count())
            .significantAdaptationCount(significantAdaptationCount)
            .build();
    }

    private static String levelName(CourseEntity c) {
        if (c == null || c.getGrade() == null || c.getGrade().getLevel() == null) {
            return null;
        }
        return c.getGrade().getLevel().getName();
    }

    private static String courseName(CourseEntity c) {
        if (c == null || c.getGrade() == null || c.getParallel() == null) {
            return null;
        }
        return c.getGrade().getName() + " \"" + c.getParallel().getName() + "\"";
    }

    private static String fullName(UserEntity u) {
        return u == null ? null : u.getNames() + " " + u.getLastNames();
    }
}
