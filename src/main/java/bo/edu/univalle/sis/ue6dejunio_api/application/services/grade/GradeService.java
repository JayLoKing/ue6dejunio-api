package bo.edu.univalle.sis.ue6dejunio_api.application.services.grade;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.UpdateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade.IGradeDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade.IGradeService;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GradeService implements IGradeService {

    private static final long MAX_GRADES = 6;

    private final IGradeDomain gradeDomain;

    public GradeService(IGradeDomain gradeDomain) {
        this.gradeDomain = gradeDomain;
    }

    @Override
    @Transactional
    public Grade create(CreateGradeCommand c) {
        if (gradeDomain.countTotal() >= MAX_GRADES) {
            throw new ConflictException("Limite alcanzado: solo se permiten " + MAX_GRADES + " grados (nivel primaria)");
        }
        if (!gradeDomain.levelExists(c.levelId())) {
            throw new ResourceNotFoundException("Level", c.levelId());
        }
        if (gradeDomain.existsByNameAndLevel(c.name(), c.levelId())) {
            throw new DuplicateResourceException("grade name", c.name());
        }
        return gradeDomain.create(c.name(), c.levelId());
    }

    @Override
    @Transactional
    public Grade update(Integer id, UpdateGradeCommand c) {
        Grade current = getById(id);
        if (!gradeDomain.levelExists(c.levelId())) {
            throw new ResourceNotFoundException("Level", c.levelId());
        }
        // A grade can carry a null level, so comparing it directly turned an edit into a 500.
        boolean changed = !Objects.equals(current.name(), c.name())
            || !Objects.equals(current.levelId(), c.levelId());
        if (changed && gradeDomain.existsByNameAndLevel(c.name(), c.levelId())) {
            throw new DuplicateResourceException("grade name", c.name());
        }
        return gradeDomain.update(id, c.name(), c.levelId());
    }

    @Override
    @Transactional(readOnly = true)
    public Grade getById(Integer id) {
        return gradeDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Grade", id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Grade> list(PageQuery pageQuery) {
        return gradeDomain.list(pageQuery);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        getById(id);
        if (gradeDomain.hasClassGroups(id)) {
            throw new ConflictException("No se puede eliminar: el grado tiene class_groups asociados");
        }
        gradeDomain.deleteById(id);
    }
}
