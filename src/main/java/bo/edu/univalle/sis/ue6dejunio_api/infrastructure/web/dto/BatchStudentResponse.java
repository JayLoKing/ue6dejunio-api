package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.dto;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.BatchResult;

import java.util.List;
import java.util.UUID;

public record BatchStudentResponse(
    int total,
    int created,
    int failed,
    List<Created> createdItems,
    List<Failed> failedItems
) {
    public record Created(int index, UUID id, String rudeCode) {}
    public record Failed(int index, String rudeCode, String reason) {}

    public static BatchStudentResponse from(BatchResult r) {
        List<Created> c = r.createdItems().stream()
            .map(i -> new Created(i.index(), i.id(), i.rudeCode())).toList();
        List<Failed> f = r.failedItems().stream()
            .map(i -> new Failed(i.index(), i.rudeCode(), i.reason())).toList();
        return new BatchStudentResponse(r.total(), r.created(), r.failed(), c, f);
    }
}
