package bo.edu.univalle.sis.ue6dejunio_api.application.services.student;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.BatchResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.CreateStudentCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StudentService implements IStudentService {

    private static final String STATUS_EFFECTIVE = "Effective";

    private final IStudentDomain studentDomain;

    public StudentService(IStudentDomain studentDomain) {
        this.studentDomain = studentDomain;
    }

    @Override
    @Transactional
    public Student create(CreateStudentCommand command) {
        if (studentDomain.existsByRudeCode(command.rudeCode())) {
            throw new DuplicateResourceException("rudeCode", command.rudeCode());
        }
        if (studentDomain.existsByIdentityCard(command.identityCard())) {
            throw new DuplicateResourceException("identityCard", command.identityCard());
        }
        return studentDomain.save(toStudent(command));
    }

    @Override
    public BatchResult createBatch(List<CreateStudentCommand> commands) {
        List<BatchResult.CreatedItem> created = new ArrayList<>();
        List<BatchResult.FailedItem> failed = new ArrayList<>();

        for (int i = 0; i < commands.size(); i++) {
            CreateStudentCommand cmd = commands.get(i);
            try {
                Student saved = create(cmd);
                created.add(new BatchResult.CreatedItem(i, saved.getId(), saved.getRudeCode()));
            } catch (RuntimeException e) {
                failed.add(new BatchResult.FailedItem(i, cmd.rudeCode(), e.getMessage()));
            }
        }
        return new BatchResult(commands.size(), created.size(), failed.size(), created, failed);
    }

    @Override
    @Transactional(readOnly = true)
    public Student getById(UUID id) {
        return studentDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante", id));
    }

    private Student toStudent(CreateStudentCommand command) {
        return Student.builder()
            .rudeCode(command.rudeCode())
            .identityCard(command.identityCard())
            .names(command.names())
            .lastNames(command.lastNames())
            .birthDate(command.birthDate())
            .gender(command.gender())
            .status(STATUS_EFFECTIVE)
            .build();
    }
}
