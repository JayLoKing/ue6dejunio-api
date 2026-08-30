package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.CreateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.UpdateAdaptationCommand;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.CreateAdaptationRequest;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.UpdateAdaptationRequest;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptationControllerTest {

    @Mock private IAdaptationService adaptationService;
    @InjectMocks private AdaptationController controller;
    @Captor private ArgumentCaptor<PageQuery> pageQueryCaptor;
    @Captor private ArgumentCaptor<CreateAdaptationCommand> createCaptor;
    @Captor private ArgumentCaptor<UpdateAdaptationCommand> updateCaptor;

    private static JwtAuthenticationToken tokenOf(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(), userId.toString());
    }

    private static Adaptation adaptation(String conditionType) {
        return new Adaptation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Ana Paz",
            conditionType, "Contenido", "Metodología", "Criterio",
            UUID.randomUUID(), UUID.randomUUID(), null, null);
    }

    // Paging without an ORDER BY is paging over an undefined order: SQL is free to return the rows
    // differently between two calls, so page 2 can repeat a row page 1 already showed and drop
    // another for good. The controller is what states the ordering, so this is where it is pinned.
    @Test
    void list_asksForAStableOrder() {
        UUID planId = UUID.randomUUID();
        when(adaptationService.listByPlan(eq(planId), pageQueryCaptor.capture()))
            .thenReturn(PageResult.<Adaptation>empty(PageQuery.of(0, 20)));

        controller.list(planId, 1, 20);

        assertThat(pageQueryCaptor.getValue().sort())
            .containsExactly(SortField.asc("student.lastNames"), SortField.asc("student.names"));
    }

    @Test
    void list_translatesTheOneBasedOffsetToAZeroBasedPage() {
        UUID planId = UUID.randomUUID();
        when(adaptationService.listByPlan(eq(planId), pageQueryCaptor.capture()))
            .thenReturn(PageResult.<Adaptation>empty(PageQuery.of(2, 15)));

        controller.list(planId, 3, 15);

        assertThat(pageQueryCaptor.getValue().page()).isEqualTo(2);
        assertThat(pageQueryCaptor.getValue().size()).isEqualTo(15);
    }

    // The controller is the only place the request becomes a command. A column it forgets to copy
    // is accepted, answered with a 200, and never stored — the teacher fills the form and the
    // condition disappears without an error saying so.
    @Test
    void create_carriesTheConditionTheFormAsksFor() {
        UUID author = UUID.randomUUID();
        when(adaptationService.create(createCaptor.capture()))
            .thenReturn(adaptation("Discapacidad"));

        var response = controller.create(new CreateAdaptationRequest(
            UUID.randomUUID(), UUID.randomUUID(), "Discapacidad",
            "Contenido", "Metodología", "Criterio"), tokenOf(author));

        assertThat(createCaptor.getValue().conditionType()).isEqualTo("Discapacidad");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().conditionType()).isEqualTo("Discapacidad");
    }

    @Test
    void update_carriesTheCorrectedCondition() {
        UUID author = UUID.randomUUID();
        when(adaptationService.update(any(UUID.class), updateCaptor.capture()))
            .thenReturn(adaptation("TEA"));

        var response = controller.update(UUID.randomUUID(), new UpdateAdaptationRequest(
            "TEA", "Contenido", "Metodología", "Criterio"), tokenOf(author));

        assertThat(updateCaptor.getValue().conditionType()).isEqualTo("TEA");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().conditionType()).isEqualTo("TEA");
    }
}
