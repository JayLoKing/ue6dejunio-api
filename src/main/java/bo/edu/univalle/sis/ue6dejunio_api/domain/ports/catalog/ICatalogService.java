package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.AcademicYearItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TrimesterPeriodItem;

import java.util.List;

public interface ICatalogService {
    List<SubjectItem> subjects(Boolean technical);
    List<GradeItem> grades();
    List<ParallelItem> parallels();
    List<TeacherItem> teachers(Boolean technical);

    /** Every gestión on record, newest first. Feeds the year filter of the student directory. */
    List<AcademicYearItem> academicYears();

    /** Configured trimester periods for {@code academicYearId}, or the current (latest) year if null. */
    List<TrimesterPeriodItem> trimesters(Integer academicYearId);
}
