package bo.edu.univalle.sis.ue6dejunio_api.application.services.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService implements ICatalogService {

    private final ICatalogDomain catalogDomain;

    public CatalogService(ICatalogDomain catalogDomain) {
        this.catalogDomain = catalogDomain;
    }

    @Override
    public List<SubjectItem> subjects() {
        return catalogDomain.subjects();
    }

    @Override
    public List<GradeItem> grades() {
        return catalogDomain.grades();
    }

    @Override
    public List<ParallelItem> parallels() {
        return catalogDomain.parallels();
    }

    @Override
    public List<TeacherItem> teachers(Boolean technical) {
        return catalogDomain.teachers(technical);
    }

}
