package bo.edu.univalle.sis.ue6dejunio_api.application.pdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.pdc.PdcService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.event.IDomainEventPublisher;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PdcServiceTest {

    @Mock private IPdcDomain pdcDomain;

    /**
     * A state change states what it became and stops there. Nothing here asserts on the event — who
     * hears about it is the notification module's question, and is pinned by its own IT. What this
     * mock buys is that these tests keep saying nothing about notifications at all.
     */
    @Mock private IDomainEventPublisher events;

    @InjectMocks private PdcService pdcService;

    private final UUID user = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final LocalDate august1 = LocalDate.of(2026, 8, 3);
    private final LocalDate september4 = LocalDate.of(2026, 9, 4);

    private CreatePdcCommand command(
            Integer planNumber, LocalDate start, LocalDate end, List<UUID> groups) {
        return new CreatePdcCommand(courseId, planNumber, 2, start, end, null, null, null, groups);
    }

    private CreatePdcCommand command() {
        return command(4, august1, september4, null);
    }

    private Pdc pdcWithStatus(UUID id, String status) {
        return Pdc.builder()
                .id(id)
                .courseId(courseId)
                .status(status)
                .planNumber(4)
                .trimester(2)
                .periodStart(august1)
                .periodEnd(september4)
                .build();
    }

    // ---- create -------------------------------------------------------------------------------

    // The plan covers what its author teaches. A homeroom teacher may reach every subject of the
    // course, but the subjects a technical teacher runs there are planned by that teacher, in their
    // own plan — opening them here would file someone else's work under this teacher's name.
    @Test
    void create_setsDraftAndOpensABlockPerSubjectTheAuthorTeaches() {
        UUID planId = UUID.randomUUID();
        List<UUID> ownSubjects = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(ownSubjects);
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(), user);

        ArgumentCaptor<Pdc> saved = ArgumentCaptor.forClass(Pdc.class);
        verify(pdcDomain).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PdcStatus.DRAFT);
        verify(pdcDomain).addSubjects(planId, ownSubjects);
    }

    // A homeroom teacher who also runs the technical subjects plans all of them — the nine blocks
    // are theirs because they teach nine, not because they run the course.
    @Test
    void create_opensTheTechnicalSubjectsTheHomeroomTeacherAlsoRuns() {
        UUID planId = UUID.randomUUID();
        List<UUID> nineSubjects =
                List.of(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID());
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(nineSubjects);
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(), user);

        verify(pdcDomain).addSubjects(planId, nineSubjects);
    }

    @Test
    void create_withNamedSubjects_keepsOnlyThose() {
        UUID planId = UUID.randomUUID();
        UUID onlySubject = UUID.randomUUID();
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(List.of(onlySubject));
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(4, august1, september4, List.of(onlySubject)), user);

        verify(pdcDomain).addSubjects(planId, List.of(onlySubject));
    }

    @Test
    void create_subjectFromAnotherCourse_isRefused() {
        UUID foreign = UUID.randomUUID();
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user))
                .thenReturn(List.of(UUID.randomUUID()));

        assertThatThrownBy(
                        () ->
                                pdcService.create(
                                        command(4, august1, september4, List.of(foreign)), user))
                .isInstanceOf(ValidationException.class);
        verify(pdcDomain, never()).save(any());
    }

    // The plan is monthly. A trimester legitimately holds three or four of them, so only the same
    // numbered plan of the same course may not repeat.
    @Test
    void create_secondPlanOfTheSameTrimester_isAllowed() {
        UUID planId = UUID.randomUUID();
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 5)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user))
                .thenReturn(List.of(UUID.randomUUID()));
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(5, august1, september4, null), user);

        verify(pdcDomain).save(any(Pdc.class));
    }

    @Test
    void create_samePlanNumberTwice_throwsDuplicate() {
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(true);

        assertThatThrownBy(() -> pdcService.create(command(), user))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void create_unknownCourse_throwsNotFound() {
        when(pdcDomain.courseExists(courseId)).thenReturn(false);

        assertThatThrownBy(() -> pdcService.create(command(), user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_periodEndingBeforeItStarts_isRefused() {
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);

        assertThatThrownBy(() -> pdcService.create(command(4, september4, august1, null), user))
                .isInstanceOf(ValidationException.class);
    }

    // Nobody opens a plan over subjects they do not teach — not even to cover a course that has
    // none active. Widening an empty list would hand whoever asked the course's single monthly
    // slot.
    @Test
    void create_callerTeachesNothingInTheCourse_isRefused() {
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(List.of());

        assertThatThrownBy(() -> pdcService.create(command(), user))
                .isInstanceOf(ConflictException.class);
        verify(pdcDomain, never()).save(any());
    }

    // Repeating an id passed a distinct-count check and then collided with the index that allows
    // one block per subject, turning a fixable request into a 500.
    @Test
    void create_repeatedSubject_isOpenedOnce() {
        UUID planId = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(List.of(subject));
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(4, august1, september4, List.of(subject, subject)), user);

        verify(pdcDomain).addSubjects(planId, List.of(subject));
    }

    // A specialist could otherwise open a plan covering every subject of someone else's course —
    // and because one plan per course and month is allowed, take the slot the homeroom teacher's
    // own plan was going to fill.
    @Test
    void create_specialistWithoutNamingSubjects_getsOnlyTheirOwn() {
        UUID planId = UUID.randomUUID();
        UUID ownSubject = UUID.randomUUID();
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(List.of(ownSubject));
        when(pdcDomain.save(any(Pdc.class))).thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));
        when(pdcDomain.addSubjects(eq(planId), anyList()))
                .thenReturn(pdcWithStatus(planId, PdcStatus.DRAFT));

        pdcService.create(command(), user);

        verify(pdcDomain).addSubjects(planId, List.of(ownSubject));
    }

    @Test
    void create_teacherWithNoActiveSubjectInTheCourse_isRefused() {
        when(pdcDomain.courseExists(courseId)).thenReturn(true);
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 4)).thenReturn(false);
        when(pdcDomain.classGroupIdsTaughtBy(courseId, user)).thenReturn(List.of());

        assertThatThrownBy(() -> pdcService.create(command(), user))
                .isInstanceOf(ConflictException.class);
        verify(pdcDomain, never()).save(any());
    }

    // ---- update -------------------------------------------------------------------------------

    // Renumbering onto a month the course already planned collides with the unique index. Caught
    // here it is a conflict; left to the database it surfaced as a 500.
    @Test
    void update_renumberingOntoAnExistingMonth_throwsDuplicate() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.existsByCoursePlanNumber(courseId, 2, 5)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                pdcService.update(
                                        id,
                                        new UpdatePdcCommand(5, null, null, null, null, null),
                                        user))
                .isInstanceOf(DuplicateResourceException.class);
        verify(pdcDomain, never()).save(any());
    }

    @Test
    void update_keepingTheSameNumber_doesNotCollideWithItself() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        Pdc updated =
                pdcService.update(
                        id,
                        new UpdatePdcCommand(4, null, null, "Nuevo objetivo", null, null),
                        user);

        assertThat(updated.getHolisticObjective()).isEqualTo("Nuevo objetivo");
        verify(pdcDomain, never()).existsByCoursePlanNumber(any(), any(), any());
    }

    // ---- writing a subject block --------------------------------------------------------------

    @Test
    void writeSubject_rowWithoutAWeek_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.statusOf(id)).thenReturn(Optional.of(PdcStatus.DRAFT));

        UpsertPdcSubjectCommand c =
                new UpsertPdcSubjectCommand(
                        null,
                        null,
                        List.of(
                                new UpsertPdcSubjectCommand.PdcEntryCommand(
                                        "  ", "T34", null, null, null, null, null, null, null, null,
                                        null)));

        assertThatThrownBy(() -> pdcService.writeSubject(id, UUID.randomUUID(), c, user))
                .isInstanceOf(ValidationException.class);
        verify(pdcDomain, never()).writeSubject(any(), any(), any(), any());
    }

    @Test
    void writeSubject_negativePeriods_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.statusOf(id)).thenReturn(Optional.of(PdcStatus.DRAFT));

        UpsertPdcSubjectCommand c =
                new UpsertPdcSubjectCommand(
                        null,
                        null,
                        List.of(
                                new UpsertPdcSubjectCommand.PdcEntryCommand(
                                        "Semana 1",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        -1,
                                        null,
                                        null,
                                        null)));

        assertThatThrownBy(() -> pdcService.writeSubject(id, UUID.randomUUID(), c, user))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void writeSubject_onApprovedPlan_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.statusOf(id)).thenReturn(Optional.of(PdcStatus.APPROVED));

        assertThatThrownBy(
                        () ->
                                pdcService.writeSubject(
                                        id,
                                        UUID.randomUUID(),
                                        new UpsertPdcSubjectCommand(null, null, List.of()),
                                        user))
                .isInstanceOf(ConflictException.class);
    }

    // The guard reads the status alone rather than the whole plan, so a plan that is not there has
    // to answer 404 from that read instead of falling through to the write.
    @Test
    void writeSubject_onAPlanThatDoesNotExist_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.statusOf(id)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                pdcService.writeSubject(
                                        id,
                                        UUID.randomUUID(),
                                        new UpsertPdcSubjectCommand(null, null, List.of()),
                                        user))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(pdcDomain, never()).writeSubject(any(), any(), any(), any());
    }

    // ---- the rotation -------------------------------------------------------------------------

    @Test
    void copyToSiblingCourses_copiesIntoEveryParallelThatLacksTheMonth() {
        UUID id = UUID.randomUUID();
        UUID parallelB = UUID.randomUUID();
        UUID parallelC = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.APPROVED)));
        when(pdcDomain.siblingCourseIdsOf(courseId)).thenReturn(List.of(parallelB, parallelC));
        // C already wrote its own August plan, so the rotation leaves it alone.
        when(pdcDomain.courseIdsWithPlan(List.of(parallelB, parallelC), 2, 4))
                .thenReturn(Set.of(parallelC));
        when(pdcDomain.copyTo(id, List.of(parallelB), user))
                .thenReturn(List.of(pdcWithStatus(UUID.randomUUID(), PdcStatus.DRAFT)));

        List<Pdc> copies = pdcService.copyToSiblingCourses(id, user);

        assertThat(copies).hasSize(1);
        // C is left out of the call entirely, not copied and discarded.
        verify(pdcDomain).copyTo(id, List.of(parallelB), user);
    }

    @Test
    void copyToSiblingCourses_fromADraft_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));

        assertThatThrownBy(() -> pdcService.copyToSiblingCourses(id, user))
                .isInstanceOf(ConflictException.class);
        verify(pdcDomain, never()).copyTo(any(), anyList(), any());
    }

    // A plan the Director sent back is one nobody stands behind yet. Handing it to the other
    // parallels would spread work that was already refused.
    @Test
    void copyToSiblingCourses_fromAPlanWithObservations_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id))
                .thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.WITH_OBSERVATIONS)));

        assertThatThrownBy(() -> pdcService.copyToSiblingCourses(id, user))
                .isInstanceOf(ConflictException.class);
        verify(pdcDomain, never()).copyTo(any(), anyList(), any());
    }

    // ---- review ------------------------------------------------------------------------------

    @Test
    void publish_fromDraft_movesToPublished() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(pdcService.publish(id, user).getStatus()).isEqualTo(PdcStatus.PUBLISHED);
    }

    @Test
    void approve_fromPublished_movesToApprovedAndClearsObservations() {
        UUID id = UUID.randomUUID();
        Pdc stored = pdcWithStatus(id, PdcStatus.PUBLISHED);
        stored.setReviewObservations("faltaba el producto final");
        when(pdcDomain.findById(id)).thenReturn(Optional.of(stored));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        Pdc approved = pdcService.approve(id, user);

        assertThat(approved.getStatus()).isEqualTo(PdcStatus.APPROVED);
        assertThat(approved.getReviewObservations()).isNull();
    }

    @Test
    void approve_aDraft_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));

        assertThatThrownBy(() -> pdcService.approve(id, user))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void observe_recordsWhatMustBeCorrected() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id))
                .thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.PUBLISHED)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        Pdc observed = pdcService.observe(id, "Falta el producto final del mes", user);

        assertThat(observed.getStatus()).isEqualTo(PdcStatus.WITH_OBSERVATIONS);
        assertThat(observed.getReviewObservations()).isEqualTo("Falta el producto final del mes");
    }

    // Sending a plan back without saying why leaves the teacher guessing, and the status alone
    // carries no instruction.
    @Test
    void observe_withoutSayingWhat_isRefused() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> pdcService.observe(id, "   ", user))
                .isInstanceOf(ValidationException.class);
        verify(pdcDomain, never()).save(any());
    }

    // ---- delete ------------------------------------------------------------------------------

    @Test
    void delete_aDraftNobodyCopied_removesIt() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.hasCopies(id)).thenReturn(false);

        pdcService.delete(id);

        verify(pdcDomain).deleteById(id);
    }

    @Test
    void delete_anOriginalAlreadyCopied_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.hasCopies(id)).thenReturn(true);

        assertThatThrownBy(() -> pdcService.delete(id)).isInstanceOf(ConflictException.class);
        verify(pdcDomain, never()).deleteById(any());
    }

    @Test
    void delete_aPublishedPlan_isRefused() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id))
                .thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.PUBLISHED)));

        assertThatThrownBy(() -> pdcService.delete(id)).isInstanceOf(ConflictException.class);
    }
}
