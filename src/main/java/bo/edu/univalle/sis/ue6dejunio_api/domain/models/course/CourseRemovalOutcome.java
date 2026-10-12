package bo.edu.univalle.sis.ue6dejunio_api.domain.models.course;

/** What removing a course actually did to it. */
public enum CourseRemovalOutcome {
    /** It never held students or academic content, so it was erased with its bare class groups. */
    DELETED,
    /** It held some, so it was only switched off; everything it generated is kept. */
    DEACTIVATED
}
