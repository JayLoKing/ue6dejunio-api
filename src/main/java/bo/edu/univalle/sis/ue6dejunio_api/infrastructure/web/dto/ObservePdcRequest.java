package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ObservePdcRequest(@NotBlank String observations) {}
