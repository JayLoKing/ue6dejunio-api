package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpsertPdcSubjectCommand;

import java.util.List;
import java.util.UUID;

public interface IPdcService {

    /**
     * Opens a month's plan.
     *
     * @param mayPlanEverySubject whether the caller plans the course as a whole rather than only
     *                            what they teach in it — the homeroom teacher and the Director do,
     *                            a specialist does not. Passed in because who holds that authority
     *                            is a question about roles, and roles live at the edge.
     */
    Pdc create(CreatePdcCommand command, UUID currentUserId, boolean mayPlanEverySubject);

    Pdc update(UUID id, UpdatePdcCommand command, UUID currentUserId);

    /** Writes one subject's block of the plan whole. */
    Pdc writeSubject(UUID id, UUID planSubjectId, UpsertPdcSubjectCommand command, UUID currentUserId);

    Pdc getById(UUID id);

    PageResult<Pdc> list(UUID courseId, Integer trimester, String status, UUID teacherId,
                         PageQuery pageQuery);

    /**
     * Copies a plan that reached review into the other parallels of its grade, one draft each.
     * This is the rotation: one teacher writes the month, the others start from that copy and
     * adjust it for their own course.
     *
     * @return the plans created, which excludes courses that already held one for that month.
     */
    List<Pdc> copyToSiblingCourses(UUID id, UUID currentUserId);

    Pdc publish(UUID id, UUID currentUserId);

    Pdc approve(UUID id, UUID currentUserId);

    Pdc observe(UUID id, String observations, UUID currentUserId);

    void delete(UUID id);
}
