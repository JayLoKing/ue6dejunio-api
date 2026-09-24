package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "subjects")
@Getter
@Setter
public class SubjectEntity {
    @Id
    @UuidGenerator
    @Column(name = "id_subject", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // The curriculum plan prints its subjects grouped by area, so the area belongs to the subject
    // rather than to the view that renders it.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "id_area", nullable = false)
    private KnowledgeAreaEntity area;

    @Column(name = "is_technical")
    private boolean technical;

    @Column(name = "is_active")
    private boolean active;
}
