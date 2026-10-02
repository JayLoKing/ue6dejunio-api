package bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog;

import java.util.List;

/**
 * What copying one gestión's classrooms and subjects into another did.
 *
 * @param coursesCreated classrooms opened in the target
 * @param coursesSkipped classrooms the target already had for the same grade and parallel
 * @param classGroupsCreated subjects opened across the new classrooms, each with no teacher
 * @param skippedCourses the skipped classrooms by name, such as "Primero A", for the Director to
 *     see what was left as it was
 */
public record YearStructureCopy(
        int coursesCreated,
        int coursesSkipped,
        int classGroupsCreated,
        List<String> skippedCourses) {}
