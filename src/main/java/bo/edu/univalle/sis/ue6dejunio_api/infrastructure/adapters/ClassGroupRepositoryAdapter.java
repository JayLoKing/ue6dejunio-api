package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.adapters;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.ClassGroup;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.classgroup.TeacherHomeroom;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.classgroup.IClassGroupDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.AcademicYearEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ClassGroupEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.GradeEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.ParallelEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.SubjectEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities.UserEntity;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaAcademicYearRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaClassGroupRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaGradeRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaParallelRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaSubjectRepository;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.repositories.JpaUserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@Transactional(readOnly = true)
public class ClassGroupRepositoryAdapter implements IClassGroupDomain {

    private static final String TEACHER_ROLE = "Teacher";

    private final JpaClassGroupRepository classGroupRepo;
    private final JpaGradeRepository gradeRepo;
    private final JpaParallelRepository parallelRepo;
    private final JpaSubjectRepository subjectRepo;
    private final JpaAcademicYearRepository academicYearRepo;
    private final JpaUserRepository userRepo;

    public ClassGroupRepositoryAdapter(JpaClassGroupRepository classGroupRepo,
                                       JpaGradeRepository gradeRepo,
                                       JpaParallelRepository parallelRepo,
                                       JpaSubjectRepository subjectRepo,
                                       JpaAcademicYearRepository academicYearRepo,
                                       JpaUserRepository userRepo) {
        this.classGroupRepo = classGroupRepo;
        this.gradeRepo = gradeRepo;
        this.parallelRepo = parallelRepo;
        this.subjectRepo = subjectRepo;
        this.academicYearRepo = academicYearRepo;
        this.userRepo = userRepo;
    }

    @Override
    public Integer currentAcademicYearId() {
        return academicYearRepo.findTopByOrderByYearDesc()
            .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", "actual"))
            .getId();
    }

    @Override
    public boolean existsAssignment(UUID subjectId, Integer gradeId, Integer parallelId, Integer academicYearId) {
        return classGroupRepo.existsBySubject_IdAndGrade_IdAndParallel_IdAndAcademicYear_Id(
            subjectId, gradeId, parallelId, academicYearId);
    }

    @Override
    @Transactional
    public ClassGroup createAssignment(Integer gradeId, Integer parallelId, Integer academicYearId,
                                       UUID subjectId, UUID teacherId) {
        GradeEntity grade = gradeRepo.findById(gradeId)
            .orElseThrow(() -> new ResourceNotFoundException("Grado", gradeId));
        ParallelEntity parallel = parallelRepo.findById(parallelId)
            .orElseThrow(() -> new ResourceNotFoundException("Paralelo", parallelId));
        AcademicYearEntity year = academicYearRepo.findById(academicYearId)
            .orElseThrow(() -> new ResourceNotFoundException("AcademicYear", academicYearId));
        SubjectEntity subject = subjectRepo.findById(subjectId)
            .orElseThrow(() -> new ResourceNotFoundException("Materia", subjectId));
        UserEntity teacher = userRepo.findById(teacherId)
            .orElseThrow(() -> new ResourceNotFoundException("Docente", teacherId));

        if (teacher.getRole() == null || !TEACHER_ROLE.equals(teacher.getRole().getName())) {
            throw new IllegalArgumentException("El usuario " + teacherId + " no tiene rol Teacher");
        }

        ClassGroupEntity entity = new ClassGroupEntity();
        entity.setGrade(grade);
        entity.setParallel(parallel);
        entity.setAcademicYear(year);
        entity.setSubject(subject);
        entity.setTeacher(teacher);
        return toDomain(classGroupRepo.save(entity));
    }

    @Override
    public Optional<TeacherHomeroom> resolveTeacherHomeroom(UUID teacherId) {
        Integer yearId = academicYearRepo.findTopByOrderByYearDesc()
            .map(AcademicYearEntity::getId).orElse(null);
        if (yearId == null) {
            return Optional.empty();
        }
        List<ClassGroupEntity> groups = classGroupRepo.findByTeacher_IdAndAcademicYear_Id(teacherId, yearId);
        if (groups.isEmpty()) {
            return Optional.empty();
        }
        // Aula = grade+parallel combination with most subjects assigned to this teacher
        Map<String, List<ClassGroupEntity>> byCourse = groups.stream()
            .collect(Collectors.groupingBy(g -> g.getGrade().getId() + "-" + g.getParallel().getId()));
        List<ClassGroupEntity> homeroom = byCourse.values().stream()
            .max((a, b) -> Integer.compare(a.size(), b.size()))
            .orElseThrow();
        ClassGroupEntity ref = homeroom.get(0);
        return Optional.of(new TeacherHomeroom(ref.getGrade().getName(), ref.getParallel().getName()));
    }

    @Override
    public List<UUID> classGroupIdsByCourse(Integer gradeId, Integer parallelId, Integer academicYearId) {
        return classGroupRepo.findIdsByCourse(gradeId, parallelId, academicYearId);
    }

    private ClassGroup toDomain(ClassGroupEntity e) {
        return new ClassGroup(
            e.getId(),
            e.getSubject().getId(), e.getSubject().getName(),
            e.getTeacher().getId(), e.getTeacher().getNames() + " " + e.getTeacher().getLastNames(),
            e.getGrade().getId(), e.getGrade().getName(),
            e.getParallel().getId(), e.getParallel().getName(),
            e.getAcademicYear().getId(), e.getAcademicYear().getYear()
        );
    }
}
