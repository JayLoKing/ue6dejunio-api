package bo.edu.univalle.sis.ue6dejunio_api.application.services.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatusChanged;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PdcService implements IPdcService {

    private static final Set<String> EDITABLE =
            Set.of(PdcStatus.DRAFT, PdcStatus.WITH_OBSERVATIONS);
    private static final Set<String> REVIEWABLE =
            Set.of(PdcStatus.PUBLISHED, PdcStatus.UNDER_REVIEW);

    /**
     * What may be handed to the other parallels. A draft is still being written, and a plan sent
     * back with observations is one the Director refused — spreading either would put work nobody
     * stands behind into two more courses.
     */
    private static final Set<String> COPYABLE =
            Set.of(PdcStatus.PUBLISHED, PdcStatus.UNDER_REVIEW, PdcStatus.APPROVED);

    private final IPdcDomain pdcDomain;
    private final IDomainEventPublisher events;

    public PdcService(IPdcDomain pdcDomain, IDomainEventPublisher events) {
        this.events = events;
        this.pdcDomain = pdcDomain;
    }

    @Override
    @Transactional
    public Pdc create(CreatePdcCommand c, UUID currentUserId) {
        if (!pdcDomain.courseExists(c.courseId())) {
            throw new ResourceNotFoundException("Course", c.courseId());
        }
        // The plan is monthly, so what may not repeat is the numbered plan of a course — not the
        // trimester, which legitimately holds three or four of them.
        if (pdcDomain.existsByCoursePlanNumber(c.courseId(), c.trimester(), c.planNumber())) {
            throw new DuplicateResourceException(
                    "PDC (curso + trimestre + numero)",
                    c.courseId() + "/T" + c.trimester() + "/N" + c.planNumber());
        }
        if (c.periodStart() == null || c.periodEnd() == null) {
            throw new ValidationException("El PDC necesita el periodo que cubre");
        }
        if (c.periodEnd().isBefore(c.periodStart())) {
            throw new ValidationException("El periodo del PDC termina antes de empezar");
        }

        List<UUID> classGroupIds = resolveClassGroups(c, currentUserId);

        Pdc created =
                pdcDomain.save(
                        Pdc.builder()
                                .courseId(c.courseId())
                                .planNumber(c.planNumber())
                                .trimester(c.trimester())
                                .periodStart(c.periodStart())
                                .periodEnd(c.periodEnd())
                                .status(PdcStatus.DRAFT)
                                .holisticObjective(c.holisticObjective())
                                .finalProduct(c.finalProduct())
                                .bibliography(c.bibliography())
                                .createdById(currentUserId)
                                .updatedById(currentUserId)
                                .build());

        // Opening the blocks with the plan is what lets the form walk subject by subject: the
        // teacher fills blocks that already exist rather than inventing the plan's shape.
        return pdcDomain.addSubjects(created.getId(), classGroupIds);
    }

    /**
     * Which subjects the caller may put in the plan.
     *
     * <p>Whoever teaches in the course plans what they teach — no more, however wide their reach
     * over the course is. The heading of the printed form names the subjects the teacher runs, and
     * the technical subjects somebody else runs belong in that teacher's own plan. Only a caller
     * who teaches nothing and may plan the course as a whole gets every subject; anyone else with
     * nothing to teach here is refused.
     */
    private List<UUID> resolveClassGroups(CreatePdcCommand c, UUID currentUserId) {
        // What the caller teaches here is what their plan is about. A homeroom teacher who also
        // runs the technical subjects plans those too, and a homeroom teacher who does not leaves
        // them to the technical teacher's own plan rather than filing them under their name.
        List<UUID> allowed = pdcDomain.classGroupIdsTaughtBy(c.courseId(), currentUserId);
        if (allowed.isEmpty()) {
            // Refused rather than widened. Reading an empty list as "plans everything" would let
            // anyone who reached here without an active subject — a teacher whose only class group
            // was deactivated — open a course-wide plan and take the slot the homeroom teacher
            // needed, since one plan per course and month is all there is.
            throw new ConflictException("No dictas ninguna materia activa en este curso");
        }
        if (c.classGroupIds() == null || c.classGroupIds().isEmpty()) {
            return allowed;
        }
        // Repeats would pass a distinct-count check and then collide with the one-block-per-subject
        // index, answering a request the caller can fix with a 500.
        List<UUID> asked = c.classGroupIds().stream().distinct().toList();
        if (!allowed.containsAll(asked)) {
            throw new ValidationException(
                    "Alguna materia indicada no pertenece al curso o no la dictas");
        }
        return asked;
    }

    @Override
    @Transactional
    public Pdc update(UUID id, UpdatePdcCommand c, UUID currentUserId) {
        Pdc pdc = getById(id);
        requireEditable(pdc, "editar");

        if (c.planNumber() != null && !c.planNumber().equals(pdc.getPlanNumber())) {
            // Renumbering onto a month the course already planned would hit the unique index and
            // surface as a 500 instead of the conflict it is.
            if (pdcDomain.existsByCoursePlanNumber(
                    pdc.getCourseId(), pdc.getTrimester(), c.planNumber())) {
                throw new DuplicateResourceException(
                        "PDC (curso + trimestre + numero)",
                        pdc.getCourseId() + "/T" + pdc.getTrimester() + "/N" + c.planNumber());
            }
            pdc.setPlanNumber(c.planNumber());
        }
        if (c.periodStart() != null) {
            pdc.setPeriodStart(c.periodStart());
        }
        if (c.periodEnd() != null) {
            pdc.setPeriodEnd(c.periodEnd());
        }
        if (c.holisticObjective() != null) {
            pdc.setHolisticObjective(c.holisticObjective());
        }
        if (c.finalProduct() != null) {
            pdc.setFinalProduct(c.finalProduct());
        }
        if (c.bibliography() != null) {
            pdc.setBibliography(c.bibliography());
        }

        if (pdc.getPeriodEnd().isBefore(pdc.getPeriodStart())) {
            throw new ValidationException("El periodo del PDC termina antes de empezar");
        }
        pdc.setUpdatedById(currentUserId);
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional
    public Pdc writeSubject(
            UUID id, UUID planSubjectId, UpsertPdcSubjectCommand c, UUID currentUserId) {
        // Only the status is read here. The adapter loads the plan whole to write the block, and
        // reading the document a second time just to check one string doubled every subject write.
        String status =
                pdcDomain.statusOf(id).orElseThrow(() -> new ResourceNotFoundException("PDC", id));
        requireEditable(status, "editar");
        if (c.entries() != null) {
            for (UpsertPdcSubjectCommand.PdcEntryCommand row : c.entries()) {
                if (row.weekLabel() == null || row.weekLabel().isBlank()) {
                    throw new ValidationException(
                            "Cada fila del plan necesita decir a que semana corresponde");
                }
                if (row.periods() != null && row.periods() < 0) {
                    throw new ValidationException(
                            "Los periodos de una fila no pueden ser negativos");
                }
            }
        }
        return pdcDomain.writeSubject(id, planSubjectId, c, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Pdc getById(UUID id) {
        return pdcDomain.findById(id).orElseThrow(() -> new ResourceNotFoundException("PDC", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Pdc> list(
            UUID courseId, Integer trimester, String status, UUID teacherId, PageQuery pageQuery) {
        // A draft is a teacher's unfinished month. A listing that spans the school is somebody
        // reading other people's work — the Director, the secretariat — and what they have to see
        // is what was handed in. A teacher's own listing is scoped to them, so their drafts stay.
        String excludeStatus = teacherId == null ? PdcStatus.DRAFT : null;
        return pdcDomain.list(courseId, trimester, status, excludeStatus, teacherId, pageQuery);
    }

    @Override
    @Transactional
    public List<Pdc> copyToSiblingCourses(UUID id, UUID currentUserId) {
        Pdc source = getById(id);
        if (!COPYABLE.contains(source.getStatus())) {
            throw new ConflictException(
                    "Solo se copia un PDC publicado, en revision o aprobado. Actual: "
                            + source.getStatus());
        }
        // A course that already holds that month's plan keeps it: the rotation must not overwrite
        // work a teacher already did on their own copy. Asked once for all the parallels.
        List<UUID> siblings = pdcDomain.siblingCourseIdsOf(source.getCourseId());
        Set<UUID> alreadyPlanned =
                siblings.isEmpty()
                        ? Set.of()
                        : pdcDomain.courseIdsWithPlan(
                                siblings, source.getTrimester(), source.getPlanNumber());
        List<UUID> targets =
                siblings.stream().filter(siblingId -> !alreadyPlanned.contains(siblingId)).toList();
        return targets.isEmpty() ? List.of() : pdcDomain.copyTo(id, targets, currentUserId);
    }

    @Override
    @Transactional
    public Pdc publish(UUID id, UUID currentUserId) {
        Pdc pdc = getById(id);
        requireEditable(pdc, "publicar");
        pdc.setStatus(PdcStatus.PUBLISHED);
        pdc.setReviewObservations(null);
        pdc.setUpdatedById(currentUserId);
        return announce(pdcDomain.save(pdc));
    }

    @Override
    @Transactional
    public Pdc approve(UUID id, UUID currentUserId) {
        Pdc pdc = requireReviewable(getById(id), "aprobar");
        pdc.setStatus(PdcStatus.APPROVED);
        pdc.setReviewObservations(null);
        pdc.setUpdatedById(currentUserId);
        return announce(pdcDomain.save(pdc));
    }

    @Override
    @Transactional
    public Pdc observe(UUID id, String observations, UUID currentUserId) {
        if (observations == null || observations.isBlank()) {
            throw new ValidationException("Observar un PDC exige decir que hay que corregir");
        }
        Pdc pdc = requireReviewable(getById(id), "observar");
        pdc.setStatus(PdcStatus.WITH_OBSERVATIONS);
        pdc.setReviewObservations(observations);
        pdc.setUpdatedById(currentUserId);
        return announce(pdcDomain.save(pdc));
    }

    /**
     * States what the plan became, and hands the plan back untouched.
     *
     * <p>Only a fact leaves here — who should hear about it is not this service's question. The
     * listener runs after this transaction commits, so a change that is refused announces nothing.
     */
    private Pdc announce(Pdc saved) {
        events.publish(
                new PdcStatusChanged(
                        saved.getId(),
                        saved.getCreatedById(),
                        saved.getStatus(),
                        saved.getPlanNumber(),
                        saved.getTrimester(),
                        saved.getReviewObservations()));
        return saved;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Pdc pdc = getById(id);
        if (!PdcStatus.DRAFT.equals(pdc.getStatus())) {
            throw new ConflictException(
                    "Solo se puede eliminar un PDC en Draft. Actual: " + pdc.getStatus());
        }
        // Deleting an original leaves its copies pointing at nothing, and the rotation loses the
        // record of who wrote the month.
        if (pdcDomain.hasCopies(id)) {
            throw new ConflictException(
                    "Este PDC ya fue copiado a los paralelos. Elimina primero las copias.");
        }
        // Adaptations need no guard of their own: both of their foreign keys into the plan, the one
        // to the plan and the one V11 added to the subject block, are ON DELETE CASCADE. They go
        // with the draft they were written on rather than holding it back.
        pdcDomain.deleteById(id);
    }

    private void requireEditable(Pdc pdc, String action) {
        requireEditable(pdc.getStatus(), action);
    }

    private void requireEditable(String status, String action) {
        if (!EDITABLE.contains(status)) {
            throw new ConflictException(
                    "Solo se puede "
                            + action
                            + " en estado Draft o With Observations. Actual: "
                            + status);
        }
    }

    private Pdc requireReviewable(Pdc pdc, String action) {
        if (!REVIEWABLE.contains(pdc.getStatus())) {
            throw new ConflictException(
                    "Solo se puede "
                            + action
                            + " un PDC Published o Under Review. Actual: "
                            + pdc.getStatus());
        }
        return pdc;
    }
}
