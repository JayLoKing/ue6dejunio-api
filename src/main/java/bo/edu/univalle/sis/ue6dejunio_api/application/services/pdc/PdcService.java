package bo.edu.univalle.sis.ue6dejunio_api.application.services.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class PdcService implements IPdcService {

    private static final Set<String> EDITABLE = Set.of(PdcStatus.DRAFT, PdcStatus.WITH_OBSERVATIONS);
    private static final Set<String> REVIEWABLE = Set.of(PdcStatus.PUBLISHED, PdcStatus.UNDER_REVIEW);

    private final IPdcDomain pdcDomain;

    public PdcService(IPdcDomain pdcDomain) {
        this.pdcDomain = pdcDomain;
    }

    @Override
    @Transactional
    public Pdc create(CreatePdcCommand c, UUID currentUserId) {
        if (!pdcDomain.classGroupExists(c.classGroupId())) {
            throw new ResourceNotFoundException("ClassGroup", c.classGroupId());
        }
        if (pdcDomain.existsByClassGroupAndTrimester(c.classGroupId(), c.trimester())) {
            throw new DuplicateResourceException("PDC (class_group + trimestre)",
                c.classGroupId() + "/T" + c.trimester());
        }
        Pdc pdc = Pdc.builder()
            .classGroupId(c.classGroupId())
            .trimester(c.trimester())
            .status(PdcStatus.DRAFT)
            .title(c.title())
            .holisticObjective(c.holisticObjective())
            .learningObjective(c.learningObjective())
            .contents(c.contents())
            .practiceActivities(c.practiceActivities())
            .theoryActivities(c.theoryActivities())
            .valuationActivities(c.valuationActivities())
            .productionActivities(c.productionActivities())
            .resources(c.resources())
            .startDate(c.startDate())
            .endDate(c.endDate())
            .criteriaBeing(c.criteriaBeing())
            .criteriaKnowing(c.criteriaKnowing())
            .criteriaDoing(c.criteriaDoing())
            .criteriaDeciding(c.criteriaDeciding())
            .build();
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional
    public Pdc update(UUID id, UpdatePdcCommand c, UUID currentUserId) {
        Pdc pdc = getById(id);
        if (!EDITABLE.contains(pdc.getStatus())) {
            throw new IllegalStateException("Solo se puede editar en estado Draft o With Observations. Actual: " + pdc.getStatus());
        }
        if (c.title() != null) pdc.setTitle(c.title());
        if (c.holisticObjective() != null) pdc.setHolisticObjective(c.holisticObjective());
        if (c.learningObjective() != null) pdc.setLearningObjective(c.learningObjective());
        if (c.contents() != null) pdc.setContents(c.contents());
        if (c.practiceActivities() != null) pdc.setPracticeActivities(c.practiceActivities());
        if (c.theoryActivities() != null) pdc.setTheoryActivities(c.theoryActivities());
        if (c.valuationActivities() != null) pdc.setValuationActivities(c.valuationActivities());
        if (c.productionActivities() != null) pdc.setProductionActivities(c.productionActivities());
        if (c.resources() != null) pdc.setResources(c.resources());
        if (c.startDate() != null) pdc.setStartDate(c.startDate());
        if (c.endDate() != null) pdc.setEndDate(c.endDate());
        if (c.criteriaBeing() != null) pdc.setCriteriaBeing(c.criteriaBeing());
        if (c.criteriaKnowing() != null) pdc.setCriteriaKnowing(c.criteriaKnowing());
        if (c.criteriaDoing() != null) pdc.setCriteriaDoing(c.criteriaDoing());
        if (c.criteriaDeciding() != null) pdc.setCriteriaDeciding(c.criteriaDeciding());
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional(readOnly = true)
    public Pdc getById(UUID id) {
        return pdcDomain.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PDC", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Pdc> list(UUID classGroupId, Integer trimester, String status, Pageable pageable) {
        return pdcDomain.list(classGroupId, trimester, status, pageable);
    }

    @Override
    @Transactional
    public Pdc publish(UUID id, UUID currentUserId) {
        Pdc pdc = getById(id);
        if (!EDITABLE.contains(pdc.getStatus())) {
            throw new IllegalStateException("Solo se puede publicar desde Draft o With Observations. Actual: " + pdc.getStatus());
        }
        pdc.setStatus(PdcStatus.PUBLISHED);
        pdc.setReviewObservations(null);
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional
    public Pdc approve(UUID id) {
        Pdc pdc = getById(id);
        if (!REVIEWABLE.contains(pdc.getStatus())) {
            throw new IllegalStateException("Solo se puede aprobar PDC Published o Under Review. Actual: " + pdc.getStatus());
        }
        pdc.setStatus(PdcStatus.APPROVED);
        pdc.setReviewObservations(null);
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional
    public Pdc observe(UUID id, String observations) {
        Pdc pdc = getById(id);
        if (!REVIEWABLE.contains(pdc.getStatus())) {
            throw new IllegalStateException("Solo se puede observar PDC Published o Under Review. Actual: " + pdc.getStatus());
        }
        pdc.setStatus(PdcStatus.WITH_OBSERVATIONS);
        pdc.setReviewObservations(observations);
        return pdcDomain.save(pdc);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID currentUserId) {
        Pdc pdc = getById(id);
        if (!PdcStatus.DRAFT.equals(pdc.getStatus())) {
            throw new IllegalStateException("Solo se puede eliminar un PDC en Draft. Actual: " + pdc.getStatus());
        }
        pdcDomain.deleteById(id);
    }
}
