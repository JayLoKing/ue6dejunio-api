package bo.edu.univalle.sis.ue6dejunio_api.application.pdc;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.pdc.PdcService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.PdcStatus;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcDomain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdcServiceTest {

    @Mock private IPdcDomain pdcDomain;
    @InjectMocks private PdcService pdcService;

    private final UUID user = UUID.randomUUID();

    private Pdc pdcWithStatus(UUID id, String status) {
        return Pdc.builder().id(id).status(status).title("T").trimester(1)
            .classGroupId(UUID.randomUUID()).build();
    }

    @Test
    void create_setsDraft() {
        UUID cg = UUID.randomUUID();
        when(pdcDomain.classGroupExists(cg)).thenReturn(true);
        when(pdcDomain.existsByClassGroupAndTrimester(cg, 1)).thenReturn(false);
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        Pdc r = pdcService.create(CreatePdcCommand.builder()
            .classGroupId(cg).trimester(1).title("Plan").build(), user);

        assertThat(r.getStatus()).isEqualTo(PdcStatus.DRAFT);
    }

    @Test
    void create_duplicate_throws() {
        UUID cg = UUID.randomUUID();
        when(pdcDomain.classGroupExists(cg)).thenReturn(true);
        when(pdcDomain.existsByClassGroupAndTrimester(cg, 1)).thenReturn(true);
        assertThatThrownBy(() -> pdcService.create(CreatePdcCommand.builder()
            .classGroupId(cg).trimester(1).title("Plan").build(), user))
            .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void publish_fromDraft_setsPublished() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));
        Pdc r = pdcService.publish(id, user);
        assertThat(r.getStatus()).isEqualTo(PdcStatus.PUBLISHED);
    }

    @Test
    void publish_fromApproved_throws() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.APPROVED)));
        assertThatThrownBy(() -> pdcService.publish(id, user))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approve_fromPublished_setsApproved() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.PUBLISHED)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));
        Pdc r = pdcService.approve(id);
        assertThat(r.getStatus()).isEqualTo(PdcStatus.APPROVED);
    }

    @Test
    void approve_fromDraft_throws() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.DRAFT)));
        assertThatThrownBy(() -> pdcService.approve(id))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void observe_fromPublished_setsObservationsAndText() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.PUBLISHED)));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        Pdc r = pdcService.observe(id, "Falta objetivo holistico");

        assertThat(r.getStatus()).isEqualTo(PdcStatus.WITH_OBSERVATIONS);
        assertThat(r.getReviewObservations()).isEqualTo("Falta objetivo holistico");
    }

    @Test
    void update_onApproved_throws() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.APPROVED)));
        assertThatThrownBy(() -> pdcService.update(id,
            bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.UpdatePdcCommand.builder()
                .title("X").build(), user))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void delete_nonDraft_throws() {
        UUID id = UUID.randomUUID();
        when(pdcDomain.findById(id)).thenReturn(Optional.of(pdcWithStatus(id, PdcStatus.PUBLISHED)));
        assertThatThrownBy(() -> pdcService.delete(id, user))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publish_clearsObservations() {
        UUID id = UUID.randomUUID();
        Pdc obs = pdcWithStatus(id, PdcStatus.WITH_OBSERVATIONS);
        obs.setReviewObservations("prev");
        when(pdcDomain.findById(id)).thenReturn(Optional.of(obs));
        when(pdcDomain.save(any(Pdc.class))).thenAnswer(i -> i.getArgument(0));

        pdcService.publish(id, user);

        ArgumentCaptor<Pdc> cap = ArgumentCaptor.forClass(Pdc.class);
        verify(pdcDomain).save(cap.capture());
        assertThat(cap.getValue().getReviewObservations()).isNull();
        assertThat(cap.getValue().getStatus()).isEqualTo(PdcStatus.PUBLISHED);
    }
}
