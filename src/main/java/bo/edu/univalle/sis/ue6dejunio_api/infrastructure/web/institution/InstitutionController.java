package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.institution;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.institution.IInstitutionService;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto.InstitutionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/institution")
@Tag(name = "Institution", description = "Encabezado de los documentos oficiales")
@SecurityRequirement(name = "bearerAuth")
public class InstitutionController {

    private final IInstitutionService institutionService;

    public InstitutionController(IInstitutionService institutionService) {
        this.institutionService = institutionService;
    }

    @GetMapping
    @Operation(summary = "Datos de la unidad educativa que encabezan los documentos oficiales")
    public ResponseEntity<InstitutionResponse> current() {
        return ResponseEntity.ok(InstitutionResponse.from(institutionService.current()));
    }
}
