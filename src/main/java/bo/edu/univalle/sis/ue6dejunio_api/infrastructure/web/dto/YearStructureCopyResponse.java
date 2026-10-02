package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.YearStructureCopy;
import java.util.List;

public record YearStructureCopyResponse(
        int coursesCreated,
        int coursesSkipped,
        int classGroupsCreated,
        List<String> skippedCourses) {
    public static YearStructureCopyResponse from(YearStructureCopy c) {
        return new YearStructureCopyResponse(
                c.coursesCreated(), c.coursesSkipped(), c.classGroupsCreated(), c.skippedCourses());
    }
}
