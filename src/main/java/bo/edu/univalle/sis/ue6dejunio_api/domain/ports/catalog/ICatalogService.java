package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;

import java.util.List;

public interface ICatalogService {
    List<SubjectItem> subjects();
    List<GradeItem> grades();
    List<ParallelItem> parallels();
    List<TeacherItem> teachers();
}
