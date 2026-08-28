package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One of the four areas of knowledge the curriculum groups subjects under. The plan prints its
 * subjects grouped this way, so the grouping has to be data rather than a convention in the view.
 */
@Entity
@Table(name = "knowledge_areas")
@Getter
@Setter
public class KnowledgeAreaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_area", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    /** The order the areas appear in on the printed plan. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
