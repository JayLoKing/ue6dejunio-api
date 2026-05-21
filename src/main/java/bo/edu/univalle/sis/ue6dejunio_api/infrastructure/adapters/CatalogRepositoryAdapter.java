package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.GradeItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.ParallelItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.SubjectItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.catalog.TeacherItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.catalog.ICatalogDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaGradeRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaParallelRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public class CatalogRepositoryAdapter implements ICatalogDomain {

    private static final String TEACHER_ROLE = "Teacher";

    private final JpaSubjectRepository subjectRepo;
    private final JpaGradeRepository gradeRepo;
    private final JpaParallelRepository parallelRepo;
    private final JpaUserRepository userRepo;

    public CatalogRepositoryAdapter(JpaSubjectRepository subjectRepo,
                                    JpaGradeRepository gradeRepo,
                                    JpaParallelRepository parallelRepo,
                                    JpaUserRepository userRepo) {
        this.subjectRepo = subjectRepo;
        this.gradeRepo = gradeRepo;
        this.parallelRepo = parallelRepo;
        this.userRepo = userRepo;
    }

    @Override
    public List<SubjectItem> subjects() {
        return subjectRepo.findByActiveTrueOrderByName().stream()
            .map(s -> new SubjectItem(s.getId(), s.getName(), s.getArea()))
            .toList();
    }

    @Override
    public List<GradeItem> grades() {
        return gradeRepo.findAll(Sort.by("id")).stream()
            .map(g -> new GradeItem(g.getId(), g.getName(),
                g.getLevel() != null ? g.getLevel().getName() : null))
            .toList();
    }

    @Override
    public List<ParallelItem> parallels() {
        return parallelRepo.findAll(Sort.by("id")).stream()
            .map(p -> new ParallelItem(p.getId(), p.getName()))
            .toList();
    }

    @Override
    public List<TeacherItem> teachers() {
        return userRepo.findByRole_NameAndActiveTrueOrderByLastNames(TEACHER_ROLE).stream()
            .map(u -> new TeacherItem(u.getId(), u.getNames() + " " + u.getLastNames(), u.getEmail()))
            .toList();
    }
}
