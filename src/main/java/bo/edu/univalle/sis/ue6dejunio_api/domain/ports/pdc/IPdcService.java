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
     * Opens a month's plan, covering the subjects the caller teaches in the course and no others.
     */
    Pdc create(CreatePdcCommand command, UUID currentUserId);

    Pdc update(UUID id, UpdatePdcCommand command, UUID currentUserId);

    /** Writes one subject's block of the plan whole. */
    Pdc writeSubject(UUID id, UUID planSubjectId, UpsertPdcSubjectCommand command, UUID currentUserId);

    Pdc getById(UUID id);

    /**
     * The plans a caller may page through.
     *
     * @param teacherId narrows to the plans one teacher takes part in; {@code null} spans the
     *                  school, which only the Director and the secretariat are entitled to — and
     *                  which never shows a draft, because an unfinished month is not handed in yet.
     */
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
