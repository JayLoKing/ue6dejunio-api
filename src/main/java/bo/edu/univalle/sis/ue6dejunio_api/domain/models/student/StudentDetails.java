package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.time.LocalDate;

/**
 * Everything about a student that identifies them, written whole: a null here stores a null.
 *
 * <p>The status and its explanation are not in it. Those belong to {@code updateStatus}, the only
 * write that records who decided.
 */
public record StudentDetails(
        String rudeCode,
        String identityCard,
        String names,
        String lastNames,
        LocalDate birthDate,
        String gender) {}
