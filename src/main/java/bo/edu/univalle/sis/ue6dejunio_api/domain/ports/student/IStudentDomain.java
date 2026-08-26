package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IStudentDomain {
    Student save(Student student);
    Optional<Student> findById(UUID id);

    /** The students behind a whole batch of RUDE codes, in one lookup. */
    List<Student> findByRudeCodeIn(Collection<String> rudeCodes);

    /** The students behind a whole batch of identity cards, in one lookup. */
    List<Student> findByIdentityCardIn(Collection<String> identityCards);
    PageResult<StudentDirectoryItem> searchDirectory(String q, UUID courseId, PageQuery pageQuery);
    void updateStatus(UUID studentId, String status, String statusReason);
}
