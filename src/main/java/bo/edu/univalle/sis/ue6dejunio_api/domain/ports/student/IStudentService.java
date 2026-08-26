package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentWithdrawalReason;

import java.util.UUID;

public interface IStudentService {
    Student getById(UUID id);
    PageResult<StudentDirectoryItem> search(String q, UUID courseId, PageQuery pageQuery);
    void withdraw(UUID studentId, StudentWithdrawalReason reason);
}
