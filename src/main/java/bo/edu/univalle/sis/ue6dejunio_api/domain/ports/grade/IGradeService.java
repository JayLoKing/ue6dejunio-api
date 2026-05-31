package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.CreateGradeCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.UpdateGradeCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IGradeService {
    Grade create(CreateGradeCommand command);
    Grade update(Integer id, UpdateGradeCommand command);
    Grade getById(Integer id);
    Page<Grade> list(Pageable pageable);
    void delete(Integer id);
}
