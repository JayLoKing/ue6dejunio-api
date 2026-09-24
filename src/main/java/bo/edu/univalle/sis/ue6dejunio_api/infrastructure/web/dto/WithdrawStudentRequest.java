package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason one of the school's categories: "Retiro Voluntario", "Transferencia" or "Otro"
 * @param note the Director's own words. Required beside "Otro", which is the category for a reason
 *     this list does not have and so has to say what it was; optional beside the others
 */
public record WithdrawStudentRequest(@NotBlank String reason, @Size(max = 500) String note) {}
