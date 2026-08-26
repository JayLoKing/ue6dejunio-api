package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.grade;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.grade.Grade;

import java.util.Optional;

public interface IGradeDomain {
    Grade create(String name, Integer levelId);
    Grade update(Integer id, String name, Integer levelId);
    Optional<Grade> findById(Integer id);
    boolean levelExists(Integer levelId);
    boolean existsByNameAndLevel(String name, Integer levelId);
    boolean hasClassGroups(Integer gradeId);
    long countTotal();
    PageResult<Grade> list(PageQuery pageQuery);
    void deleteById(Integer id);
}
