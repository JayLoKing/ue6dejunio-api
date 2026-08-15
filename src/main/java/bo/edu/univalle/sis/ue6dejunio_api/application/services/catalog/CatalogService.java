package bo.edu.univalle.sis.ue6dejunio_api.application.services.catalog;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TrimesterPeriodItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.academicyear.IAcademicYearDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.trimesterperiod.ITrimesterPeriodDomain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CatalogService implements ICatalogService {

    private final ICatalogDomain catalogDomain;
    private final ITrimesterPeriodDomain trimesterPeriodDomain;
    private final IAcademicYearDomain academicYearDomain;

    public CatalogService(ICatalogDomain catalogDomain, ITrimesterPeriodDomain trimesterPeriodDomain,
                          IAcademicYearDomain academicYearDomain) {
        this.catalogDomain = catalogDomain;
        this.trimesterPeriodDomain = trimesterPeriodDomain;
        this.academicYearDomain = academicYearDomain;
    }

    @Override
    public List<SubjectItem> subjects(Boolean technical) {
        return catalogDomain.subjects(technical);
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

    @Override
    public List<TrimesterPeriodItem> trimesters(Integer academicYearId) {
        Integer yearId = academicYearId != null ? academicYearId : academicYearDomain.currentYearId();
        return trimesterPeriodDomain.findByAcademicYear(yearId).stream()
            .map(p -> new TrimesterPeriodItem(p.id(), p.trimester(), p.startDate(), p.endDate()))
            .toList();
    }

}
