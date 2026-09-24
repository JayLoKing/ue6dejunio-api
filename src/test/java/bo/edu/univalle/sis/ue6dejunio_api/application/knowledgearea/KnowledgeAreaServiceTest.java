package bo.edu.univalle.sis.ue6dejunio_api.application.knowledgearea;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.knowledgearea.KnowledgeAreaService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.CreateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.KnowledgeArea;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.knowledgearea.UpdateKnowledgeAreaCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.knowledgearea.IKnowledgeAreaDomain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The areas of knowledge the curriculum groups subjects under.
 *
 * <p>A small catalogue that everything academic hangs off: every subject belongs to one, and the
 * printed plan is laid out in their order. What is worth pinning is what the service refuses —
 * deleting one out from under its subjects, and two areas answering to the same name.
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeAreaServiceTest {

    @Mock private IKnowledgeAreaDomain areaDomain;
    @InjectMocks private KnowledgeAreaService service;

    private static final String NAME = "Cosmos y Pensamiento";

    private KnowledgeArea area(Integer id, String name, Integer order) {
        return new KnowledgeArea(id, name, order);
    }

    @Test
    void create_savesTheArea() {
        when(areaDomain.existsByName(NAME)).thenReturn(false);
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        KnowledgeArea created = service.create(new CreateKnowledgeAreaCommand(NAME, 2));

        assertThat(created.name()).isEqualTo(NAME);
        assertThat(created.displayOrder()).isEqualTo(2);
    }

    /**
     * The order is where the area prints on the plan, not something the school thinks about while
     * naming one. Left out, the new area goes last, which is where a new one belongs.
     */
    @Test
    void create_withoutAnOrder_putsTheAreaLast() {
        when(areaDomain.existsByName(NAME)).thenReturn(false);
        when(areaDomain.maxDisplayOrder()).thenReturn(4);
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        KnowledgeArea created = service.create(new CreateKnowledgeAreaCommand(NAME, null));

        assertThat(created.displayOrder()).isEqualTo(5);
    }

    @Test
    void create_intoAnEmptyCatalogue_startsAtOne() {
        when(areaDomain.existsByName(NAME)).thenReturn(false);
        when(areaDomain.maxDisplayOrder()).thenReturn(0);
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(new CreateKnowledgeAreaCommand(NAME, null)).displayOrder())
                .isEqualTo(1);
    }

    @Test
    void create_duplicateName_refused() {
        when(areaDomain.existsByName(NAME)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateKnowledgeAreaCommand(NAME, 1)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(areaDomain, never()).save(any());
    }

    @Test
    void update_renamesAndReorders() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 1)));
        when(areaDomain.existsByName("Comunidad y Sociedad")).thenReturn(false);
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1, new UpdateKnowledgeAreaCommand("Comunidad y Sociedad", 3));

        ArgumentCaptor<KnowledgeArea> saved = ArgumentCaptor.forClass(KnowledgeArea.class);
        verify(areaDomain).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(1);
        assertThat(saved.getValue().name()).isEqualTo("Comunidad y Sociedad");
        assertThat(saved.getValue().displayOrder()).isEqualTo(3);
    }

    /** Saving an area without touching its name is not a duplicate of itself. */
    @Test
    void update_keepingTheSameName_isAllowed() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 1)));
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1, new UpdateKnowledgeAreaCommand(NAME, 1));

        verify(areaDomain).save(any());
    }

    @Test
    void update_toANameAlreadyTaken_refused() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 1)));
        when(areaDomain.existsByName("Comunidad y Sociedad")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        1,
                                        new UpdateKnowledgeAreaCommand("Comunidad y Sociedad", 1)))
                .isInstanceOf(DuplicateResourceException.class);
        verify(areaDomain, never()).save(any());
    }

    /** Leaving the order out of an edit means leaving it where it was, not resetting it. */
    @Test
    void update_withoutAnOrder_leavesItWhereItWas() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 3)));
        when(areaDomain.save(any())).thenAnswer(i -> i.getArgument(0));

        service.update(1, new UpdateKnowledgeAreaCommand(NAME, null));

        ArgumentCaptor<KnowledgeArea> saved = ArgumentCaptor.forClass(KnowledgeArea.class);
        verify(areaDomain).save(saved.capture());
        assertThat(saved.getValue().displayOrder()).isEqualTo(3);
    }

    @Test
    void update_unknownArea_notFound() {
        when(areaDomain.findById(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9, new UpdateKnowledgeAreaCommand(NAME, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Every subject must name an area, and the database enforces it with ON DELETE RESTRICT.
     * Answered here rather than left to the constraint, which would surface as a 500 and say
     * nothing about what the school has to do first.
     */
    @Test
    void delete_areaThatStillHasSubjects_refused() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 1)));
        when(areaDomain.hasSubjects(1)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1)).isInstanceOf(ConflictException.class);
        verify(areaDomain, never()).deleteById(any());
    }

    @Test
    void delete_emptyArea_isRemoved() {
        when(areaDomain.findById(1)).thenReturn(Optional.of(area(1, NAME, 1)));
        when(areaDomain.hasSubjects(1)).thenReturn(false);

        service.delete(1);

        verify(areaDomain).deleteById(1);
    }

    @Test
    void delete_unknownArea_notFound() {
        when(areaDomain.findById(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9)).isInstanceOf(ResourceNotFoundException.class);
    }
}
