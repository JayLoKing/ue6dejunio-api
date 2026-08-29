package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * One row of a subject's weekly table: what is taught that week, through the four moments of the
 * formative process, with the resources, periods and evaluation criteria it is judged by.
 */
@Entity
@Table(name = "curriculum_plan_entries")
@Getter
@Setter
public class CurriculumPlanEntryEntity {

    @Id
    @UuidGenerator
    @Column(name = "id_plan_entry", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_plan_subject", nullable = false)
    private CurriculumPlanSubjectEntity planSubject;

    /** Free text: the form carries "Semana 1" but also spans like "Semanas 3 y 4". */
    @Column(name = "week_label", nullable = false, length = 60)
    private String weekLabel;

    @Column(name = "contents")
    private String contents;

    @Column(name = "practice")
    private String practice;

    @Column(name = "theory")
    private String theory;

    @Column(name = "valuation")
    private String valuation;

    @Column(name = "production")
    private String production;

    @Column(name = "resources")
    private String resources;

    /** How many teaching periods the week's work takes. */
    @Column(name = "periods")
    private Integer periods;

    @Column(name = "criteria_being")
    private String criteriaBeing;

    @Column(name = "criteria_knowing")
    private String criteriaKnowing;

    @Column(name = "criteria_doing")
    private String criteriaDoing;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
