package bo.edu.univalle.sis.ue6dejunio_api.application.services.academicyear;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.YearStructureCopy;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.Course;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.course.GradeParallel;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IYearStructureService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.course.ICourseDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Next year starts from this year's classrooms and subjects, minus the people. The reads are a
 * fixed number of queries however large the school is: the source courses, what the target already
 * holds, and every source subject at once. Nothing is read per row.
 */
@Service
public class YearStructureService implements IYearStructureService {

    private final IAcademicYearDomain academicYearDomain;
    private final ICourseDomain courseDomain;
    private final IClassGroupDomain classGroupDomain;
    private final ClosedYearGuard closedYearGuard;

    public YearStructureService(
            IAcademicYearDomain academicYearDomain,
            ICourseDomain courseDomain,
            IClassGroupDomain classGroupDomain,
            ClosedYearGuard closedYearGuard) {
        this.academicYearDomain = academicYearDomain;
        this.courseDomain = courseDomain;
        this.classGroupDomain = classGroupDomain;
        this.closedYearGuard = closedYearGuard;
    }

    @Override
    @Transactional
    public YearStructureCopy duplicate(int targetYearId, int sourceYearId) {
        if (targetYearId == sourceYearId) {
            throw new ValidationException(
                    "La gestión de origen debe ser distinta de la gestión de destino.");
        }
        requireExists(targetYearId);
        requireExists(sourceYearId);
        closedYearGuard.requireOpenForYear(targetYearId);

        List<Course> sourceCourses = courseDomain.activeOfYear(sourceYearId);
        Set<GradeParallel> alreadyThere = courseDomain.gradeParallelsOfYear(targetYearId);

        List<Course> toCopy = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Course c : sourceCourses) {
            if (alreadyThere.contains(new GradeParallel(c.gradeId(), c.parallelId()))) {
                skipped.add(c.gradeName() + " " + c.parallelName());
            } else {
                toCopy.add(c);
            }
        }
        if (toCopy.isEmpty()) {
            return new YearStructureCopy(0, skipped.size(), 0, skipped);
        }

        Map<GradeParallel, UUID> created =
                courseDomain.createWithoutHomeroom(
                        targetYearId,
                        toCopy.stream()
                                .map(c -> new GradeParallel(c.gradeId(), c.parallelId()))
                                .toList());
        Map<UUID, List<UUID>> subjectsBySourceCourse =
                classGroupDomain.activeSubjectIdsByCourse(toCopy.stream().map(Course::id).toList());
        Map<UUID, List<UUID>> subjectsByNewCourse = new HashMap<>();
        for (Course c : toCopy) {
            List<UUID> subjects = subjectsBySourceCourse.get(c.id());
            UUID newCourseId = created.get(new GradeParallel(c.gradeId(), c.parallelId()));
            if (subjects != null && newCourseId != null) {
                subjectsByNewCourse.put(newCourseId, subjects);
            }
        }
        int classGroups = classGroupDomain.createUnassigned(subjectsByNewCourse);
        return new YearStructureCopy(toCopy.size(), skipped.size(), classGroups, skipped);
    }

    private void requireExists(int academicYearId) {
        academicYearDomain
                .findItem(academicYearId)
                .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
    }
}
