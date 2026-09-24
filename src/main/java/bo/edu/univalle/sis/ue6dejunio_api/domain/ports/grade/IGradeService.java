package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.UpdateGradeCommand;

public interface IGradeService {
    Grade create(CreateGradeCommand command);

    Grade update(Integer id, UpdateGradeCommand command);

    Grade getById(Integer id);

    PageResult<Grade> list(PageQuery pageQuery);

    void delete(Integer id);
}
