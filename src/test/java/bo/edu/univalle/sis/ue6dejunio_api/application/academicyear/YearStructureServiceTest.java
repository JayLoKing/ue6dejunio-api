package bo.edu.univalle.sis.ue6dejunio_api.application.academicyear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.ClosedYearGuard;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear.YearStructureService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.YearStructureCopy;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.GradeParallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Copying a year's structure into another: the rules, with the ports mocked. */
@ExtendWith(MockitoExtension.class)
class YearStructureServiceTest {

    private static final int SOURCE = 1;
    private static final int TARGET = 2;

    @Mock private IAcademicYearDomain years;
    @Mock private ICourseDomain courses;
    @Mock private IClassGroupDomain classGroups;
    @Mock private ClosedYearGuard guard;

    private YearStructureService service;

    @BeforeEach
    void setUp() {
        service = new YearStructureService(years, courses, classGroups, guard);
    }

    private void bothYearsExist() {
        when(years.findItem(SOURCE))
                .thenReturn(Optional.of(new AcademicYearItem(SOURCE, 2026, true, false)));
        when(years.findItem(TARGET))
                .thenReturn(Optional.of(new AcademicYearItem(TARGET, 2027, false, false)));
    }

    private Course sourceCourse(UUID id, int gradeId, int parallelId, String parallelName) {
        return new Course(
                id,
                gradeId,
                "Primero",
                parallelId,
                parallelName,
                SOURCE,
                2026,
                UUID.randomUUID(),
                "Nora Arnez",
                true,
                true);
    }

    @Test
    void sameYear_isRefusedBeforeAnythingIsRead() {
        assertThatThrownBy(() -> service.duplicate(SOURCE, SOURCE))
                .isInstanceOf(ValidationException.class);

        verifyNoInteractions(years, courses, classGroups, guard);
    }

    @Test
    void unknownTarget_isNotFound() {
        when(years.findItem(TARGET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate(TARGET, SOURCE))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(courses, classGroups);
    }

    @Test
    void unknownSource_isNotFound() {
        when(years.findItem(TARGET))
                .thenReturn(Optional.of(new AcademicYearItem(TARGET, 2027, false, false)));
        when(years.findItem(SOURCE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.duplicate(TARGET, SOURCE))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(courses, classGroups);
    }

    @Test
    void closedTarget_isAConflictAndNothingIsWritten() {
        bothYearsExist();
        doThrow(new ConflictException("cerrada")).when(guard).requireOpenForYear(TARGET);

        assertThatThrownBy(() -> service.duplicate(TARGET, SOURCE))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(courses, classGroups);
    }

    @Test
    void closedSource_isStillReadable() {
        when(years.findItem(SOURCE))
                .thenReturn(Optional.of(new AcademicYearItem(SOURCE, 2025, false, true)));
        when(years.findItem(TARGET))
                .thenReturn(Optional.of(new AcademicYearItem(TARGET, 2027, false, false)));
        when(courses.activeOfYear(SOURCE)).thenReturn(List.of());
        when(courses.gradeParallelsOfYear(TARGET)).thenReturn(Set.of());

        YearStructureCopy result = service.duplicate(TARGET, SOURCE);

        assertThat(result).isEqualTo(new YearStructureCopy(0, 0, 0, List.of()));
        verify(guard).requireOpenForYear(TARGET);
        verify(guard, never()).requireOpenForYear(SOURCE);
    }

    @Test
    void copiesWhatIsMissing_skipsWhatExists_andLeavesTeachersBlank() {
        bothYearsExist();
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID newA = UUID.randomUUID();
        UUID math = UUID.randomUUID();
        UUID language = UUID.randomUUID();
        when(courses.activeOfYear(SOURCE))
                .thenReturn(List.of(sourceCourse(aId, 1, 1, "A"), sourceCourse(bId, 1, 2, "B")));
        when(courses.gradeParallelsOfYear(TARGET)).thenReturn(Set.of(new GradeParallel(1, 2)));
        when(courses.createWithoutHomeroom(eq(TARGET), anyCollection()))
                .thenReturn(Map.of(new GradeParallel(1, 1), newA));
        when(classGroups.activeSubjectIdsByCourse(anyCollection()))
                .thenReturn(Map.of(aId, List.of(math, language)));
        when(classGroups.createUnassigned(anyMap())).thenReturn(2);

        YearStructureCopy result = service.duplicate(TARGET, SOURCE);

        assertThat(result).isEqualTo(new YearStructureCopy(1, 1, 2, List.of("Primero B")));
        verify(courses).createWithoutHomeroom(TARGET, List.of(new GradeParallel(1, 1)));
        verify(classGroups).activeSubjectIdsByCourse(List.of(aId));
        verify(classGroups).createUnassigned(Map.of(newA, List.of(math, language)));
    }

    @Test
    void everythingAlreadyThere_writesNothing() {
        bothYearsExist();
        UUID aId = UUID.randomUUID();
        when(courses.activeOfYear(SOURCE)).thenReturn(List.of(sourceCourse(aId, 1, 1, "A")));
        when(courses.gradeParallelsOfYear(TARGET)).thenReturn(Set.of(new GradeParallel(1, 1)));

        YearStructureCopy result = service.duplicate(TARGET, SOURCE);

        assertThat(result).isEqualTo(new YearStructureCopy(0, 1, 0, List.of("Primero A")));
        verify(courses, never()).createWithoutHomeroom(any(), any());
        verifyNoInteractions(classGroups);
    }
}
