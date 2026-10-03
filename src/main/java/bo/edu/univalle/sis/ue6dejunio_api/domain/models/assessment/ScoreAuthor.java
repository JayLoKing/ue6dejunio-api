package bo.edu.univalle.sis.ue6dejunio_api.domain.models.assessment;

import java.util.UUID;

/**
 * Who wrote a score, as the notebook shows them. The name is resolved when the score is read, so a
 * renamed teacher appears under the current name; {@code fullName} is null only when the account no
 * longer resolves.
 */
public record ScoreAuthor(UUID id, String fullName) {}
