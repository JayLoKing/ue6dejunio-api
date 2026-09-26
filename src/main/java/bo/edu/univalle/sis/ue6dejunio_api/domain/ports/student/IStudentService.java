package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryItem;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDirectoryQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentMovementSummary;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.WithdrawStudentCommand;
import java.util.UUID;

public interface IStudentService {
    Student getById(UUID id);

    /** The institution's students, narrowed by whatever the caller filtered on. */
    PageResult<StudentDirectoryItem> search(StudentDirectoryQuery query, PageQuery pageQuery);

    /**
     * How the roll moved during a gestión: intakes and withdrawals month by month, and the reasons
     * students left.
     *
     * <p>Secretaría's table. The intakes are exact — every enrolment carries its own date — while
     * the withdrawals are the status each student holds now and not a ledger of every move they
     * made; see {@link StudentMovementSummary} for what that leaves out.
     */
    StudentMovementSummary movementSummary(Integer academicYearId);

    /**
     * Takes a student off the roll, recording under which category, in whose words, and by whom.
     */
    void withdraw(WithdrawStudentCommand command);
}
