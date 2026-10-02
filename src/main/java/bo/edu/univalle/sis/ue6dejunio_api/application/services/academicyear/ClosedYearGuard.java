package bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearLockDomain;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The single answer to "may this write land on that year?". A closed year freezes the academic
 * record, so every use case that writes to it asks here first, before any mutation and inside its
 * own transaction.
 *
 * <p>It is a use-case concern and not an authorization one: the refusal is a 409, because the
 * caller may well be allowed to write; the year is what is closed. It therefore lives in the
 * services and not in {@code @authz}, which would answer 403 and which the Director bypasses.
 *
 * <p>Every method resolves the year of its target with one query, and the plural ones with one
 * query for the whole collection. A target that resolves to nothing passes: reporting an unknown id
 * is the use case's job, and the guard must not swallow it as a different error.
 */
@Component
public class ClosedYearGuard {

    private final IAcademicYearLockDomain lock;
    private final IAcademicYearDomain years;

    public ClosedYearGuard(IAcademicYearLockDomain lock, IAcademicYearDomain years) {
        this.lock = lock;
        this.years = years;
    }

    public void requireOpenForClassGroup(UUID classGroupId) {
        requireOpenForClassGroups(Set.of(classGroupId));
    }

    public void requireOpenForClassGroups(Collection<UUID> classGroupIds) {
        if (classGroupIds.isEmpty()) {
            return;
        }
        refuseIfClosed(lock.closedYearsOfClassGroups(Set.copyOf(classGroupIds)));
    }

    public void requireOpenForEnrollment(UUID courseEnrollmentId) {
        requireOpenForEnrollments(Set.of(courseEnrollmentId));
    }

    public void requireOpenForEnrollments(Collection<UUID> courseEnrollmentIds) {
        if (courseEnrollmentIds.isEmpty()) {
            return;
        }
        refuseIfClosed(lock.closedYearsOfEnrollments(Set.copyOf(courseEnrollmentIds)));
    }

    public void requireOpenForCourse(UUID courseId) {
        requireOpenForCourses(Set.of(courseId));
    }

    public void requireOpenForCourses(Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return;
        }
        refuseIfClosed(lock.closedYearsOfCourses(Set.copyOf(courseIds)));
    }

    /** For a caller that already holds the {@code id_academic_year} row id. */
    public void requireOpenForYear(int academicYearId) {
        years.findItem(academicYearId)
                .filter(y -> y.closed())
                .ifPresent(y -> refuseIfClosed(Set.of(y.year())));
    }

    private static void refuseIfClosed(Set<Integer> closedYears) {
        if (closedYears.isEmpty()) {
            return;
        }
        String names =
                new TreeSet<>(closedYears)
                        .stream().map(String::valueOf).collect(Collectors.joining(", "));
        if (closedYears.size() == 1) {
            throw new ConflictException(
                    "La gestión " + names + " está cerrada; no admite cambios.");
        }
        throw new ConflictException(
                "Las gestiones " + names + " están cerradas; no admiten cambios.");
    }
}
