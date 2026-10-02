package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

/** What makes a classroom unique inside one gestión: its grade and its parallel. */
public record GradeParallel(Integer gradeId, Integer parallelId) {}
