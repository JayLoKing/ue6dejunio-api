package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade.IGradeDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.GradeEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.LevelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaCourseRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaGradeRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaLevelRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public class GradeRepositoryAdapter implements IGradeDomain {

    private final JpaGradeRepository gradeRepo;
    private final JpaLevelRepository levelRepo;
    private final JpaCourseRepository courseRepo;

    public GradeRepositoryAdapter(JpaGradeRepository gradeRepo,
                                  JpaLevelRepository levelRepo,
                                  JpaCourseRepository courseRepo) {
        this.gradeRepo = gradeRepo;
        this.levelRepo = levelRepo;
        this.courseRepo = courseRepo;
    }

    @Override
    @Transactional
    public Grade create(String name, Integer levelId) {
        LevelEntity level = levelRepo.findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("Level", levelId));
        GradeEntity g = new GradeEntity();
        g.setName(name);
        g.setLevel(level);
        return toDomain(gradeRepo.save(g));
    }

    @Override
    @Transactional
    public Grade update(Integer id, String name, Integer levelId) {
        GradeEntity g = gradeRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grade", id));
        LevelEntity level = levelRepo.findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("Level", levelId));
        g.setName(name);
        g.setLevel(level);
        return toDomain(gradeRepo.save(g));
    }

    @Override
    public Optional<Grade> findById(Integer id) {
        return gradeRepo.findById(id).map(this::toDomain);
    }

    @Override
    public boolean levelExists(Integer levelId) {
        return levelRepo.existsById(levelId);
    }

    @Override
    public boolean existsByNameAndLevel(String name, Integer levelId) {
        return gradeRepo.existsByNameAndLevel_Id(name, levelId);
    }

    @Override
    public boolean hasClassGroups(Integer gradeId) {
        return courseRepo.existsByGrade_Id(gradeId);
    }

    @Override
    public long countTotal() {
        return gradeRepo.count();
    }

    @Override
    public PageResult<Grade> list(PageQuery pageQuery) {
        Pageable pageable = SpringPaging.toPageable(pageQuery);
        return SpringPaging.toPageResult(gradeRepo.findAll(pageable).map(this::toDomain));
    }

    @Override
    @Transactional
    public void deleteById(Integer id) {
        gradeRepo.deleteById(id);
    }

    private Grade toDomain(GradeEntity e) {
        return new Grade(e.getId(), e.getName(),
            e.getLevel() != null ? e.getLevel().getId() : null,
            e.getLevel() != null ? e.getLevel().getName() : null);
    }
}
