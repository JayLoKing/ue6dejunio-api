package bo.edu.univalle.sis.ue6dejunio_api.application.classgroup;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.classgroup.ClassGroupService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.CreateClassGroupCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassGroupServiceTest {

    @Mock private IClassGroupDomain classGroupDomain;
    @InjectMocks private ClassGroupService classGroupService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID subjectId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID classGroupId = UUID.randomUUID();

    // --- createForCourse: shared validation regression (was IllegalArgumentException -> 500) ---

    @Test
    void createForCourse_technicalSubject_auladTeacher_throwsConflictException_notIllegalArgument() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        when(classGroupDomain.subjectExists(subjectId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(false);
        // An aula teacher from some other course. The exception the school makes is for the one who
        // runs THIS course, not for aula teachers in general.
        when(classGroupDomain.userIsHomeroomTeacherOf(teacherId, courseId)).thenReturn(false);

        CreateClassGroupCommand command = new CreateClassGroupCommand(
            courseId, List.of(new CreateClassGroupCommand.Assignment(subjectId, teacherId)));

        assertThatThrownBy(() -> classGroupService.createForCourse(command))
            .isInstanceOf(ConflictException.class)
            .isNotInstanceOf(IllegalArgumentException.class);

        verify(classGroupDomain, never()).create(any(), any(), any());
    }

    /**
     * There are not enough technical teachers to cover every course, so the school has the teacher
     * who runs the course teach its technical subjects too. Refusing that left the subject with
     * nobody in front of it, which is not a stricter rule — it is a course without a class.
     */
    @Test
    void createForCourse_technicalSubject_takenByTheCoursesOwnHomeroomTeacher_ok() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        when(classGroupDomain.subjectExists(subjectId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(false);
        when(classGroupDomain.userIsHomeroomTeacherOf(teacherId, courseId)).thenReturn(true);
        when(classGroupDomain.existsByCourseAndSubject(courseId, subjectId)).thenReturn(false);
        when(classGroupDomain.create(courseId, subjectId, teacherId))
            .thenReturn(new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
                teacherId, "Docente", true));

        CreateClassGroupCommand command = new CreateClassGroupCommand(
            courseId, List.of(new CreateClassGroupCommand.Assignment(subjectId, teacherId)));

        assertThat(classGroupService.createForCourse(command)).hasSize(1);
        verify(classGroupDomain).create(courseId, subjectId, teacherId);
    }

    /**
     * The opening is only for technical subjects. A non-technical one still belongs to an aula
     * teacher, and letting a technical teacher take it would undo the rule in the other direction.
     */
    @Test
    void createForCourse_nonTechnicalSubject_isNotOpenedByBeingHomeroomTeacher() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        when(classGroupDomain.subjectExists(subjectId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(false);
        when(classGroupDomain.userIsNonTechnicalTeacher(teacherId)).thenReturn(false);

        CreateClassGroupCommand command = new CreateClassGroupCommand(
            courseId, List.of(new CreateClassGroupCommand.Assignment(subjectId, teacherId)));

        assertThatThrownBy(() -> classGroupService.createForCourse(command))
            .isInstanceOf(ConflictException.class);
        verify(classGroupDomain, never()).userIsHomeroomTeacherOf(any(), any());
    }

    @Test
    void createForCourse_nonTechnicalSubject_technicalTeacher_throwsConflictException() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        when(classGroupDomain.subjectExists(subjectId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(false);
        when(classGroupDomain.userIsNonTechnicalTeacher(teacherId)).thenReturn(false);

        CreateClassGroupCommand command = new CreateClassGroupCommand(
            courseId, List.of(new CreateClassGroupCommand.Assignment(subjectId, teacherId)));

        assertThatThrownBy(() -> classGroupService.createForCourse(command))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void createForCourse_technicalSubject_technicalTeacher_ok() {
        when(classGroupDomain.courseExists(courseId)).thenReturn(true);
        when(classGroupDomain.subjectExists(subjectId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(true);
        when(classGroupDomain.existsByCourseAndSubject(courseId, subjectId)).thenReturn(false);
        when(classGroupDomain.create(courseId, subjectId, teacherId))
            .thenReturn(new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
                teacherId, "Docente", true));

        CreateClassGroupCommand command = new CreateClassGroupCommand(
            courseId, List.of(new CreateClassGroupCommand.Assignment(subjectId, teacherId)));

        List<ClassGroup> result = classGroupService.createForCourse(command);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).teacherId()).isEqualTo(teacherId);
    }

    // --- reassignTeacher (RF12) ---

    @Test
    void reassignTeacher_classGroupNotFound_throwsResourceNotFound() {
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> classGroupService.reassignTeacher(courseId, classGroupId, teacherId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reassignTeacher_classGroupBelongsToDifferentCourse_throwsResourceNotFound_antiIdor() {
        UUID otherCourseId = UUID.randomUUID();
        ClassGroup cg = new ClassGroup(classGroupId, otherCourseId, "1ro", "A", subjectId, "Materia",
            null, null, true);
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(cg));

        assertThatThrownBy(() -> classGroupService.reassignTeacher(courseId, classGroupId, teacherId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(classGroupDomain, never()).setTeacher(any(), any());
    }

    @Test
    void reassignTeacher_teacherDoesNotExist_throwsResourceNotFound() {
        ClassGroup cg = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            null, null, true);
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(cg));
        when(classGroupDomain.userIsTeacher(teacherId)).thenReturn(false);

        assertThatThrownBy(() -> classGroupService.reassignTeacher(courseId, classGroupId, teacherId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(classGroupDomain, never()).setTeacher(any(), any());
    }

    @Test
    void reassignTeacher_aulaTeacherOnTechnicalSubject_throwsConflict() {
        ClassGroup cg = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            null, null, true);
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(cg));
        when(classGroupDomain.userIsTeacher(teacherId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(false);

        assertThatThrownBy(() -> classGroupService.reassignTeacher(courseId, classGroupId, teacherId))
            .isInstanceOf(ConflictException.class);

        verify(classGroupDomain, never()).setTeacher(any(), any());
    }

    @Test
    void reassignTeacher_technicalTeacherOnTechnicalSubject_ok() {
        ClassGroup cg = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            null, null, true);
        ClassGroup updated = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            teacherId, "Docente", true);
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(cg));
        when(classGroupDomain.userIsTeacher(teacherId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(true);
        when(classGroupDomain.setTeacher(classGroupId, teacherId)).thenReturn(updated);

        ClassGroup result = classGroupService.reassignTeacher(courseId, classGroupId, teacherId);

        assertThat(result.teacherId()).isEqualTo(teacherId);
        verify(classGroupDomain).setTeacher(classGroupId, teacherId);
    }

    /** Handing a technical subject to the teacher who runs the course, after the fact. */
    @Test
    void reassignTeacher_technicalSubjectToTheCoursesOwnHomeroomTeacher_ok() {
        ClassGroup cg = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            null, null, true);
        ClassGroup updated = new ClassGroup(classGroupId, courseId, "1ro", "A", subjectId, "Materia",
            teacherId, "Docente", true);
        when(classGroupDomain.findById(classGroupId)).thenReturn(Optional.of(cg));
        when(classGroupDomain.userIsTeacher(teacherId)).thenReturn(true);
        when(classGroupDomain.subjectIsTechnical(subjectId)).thenReturn(true);
        when(classGroupDomain.userIsTechnicalTeacher(teacherId)).thenReturn(false);
        when(classGroupDomain.userIsHomeroomTeacherOf(teacherId, courseId)).thenReturn(true);
        when(classGroupDomain.setTeacher(classGroupId, teacherId)).thenReturn(updated);

        assertThat(classGroupService.reassignTeacher(courseId, classGroupId, teacherId).teacherId())
            .isEqualTo(teacherId);
    }
}
