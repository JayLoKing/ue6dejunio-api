package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.adaptation;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageResult;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.SortField;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.adaptation.Adaptation;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.adaptation.IAdaptationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptationControllerTest {

    @Mock private IAdaptationService adaptationService;
    @InjectMocks private AdaptationController controller;
    @Captor private ArgumentCaptor<PageQuery> pageQueryCaptor;

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
}
