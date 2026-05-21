package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchStudentRequest(
    @NotEmpty @Size(max = 500) @Valid List<CreateStudentRequest> students
) {}
