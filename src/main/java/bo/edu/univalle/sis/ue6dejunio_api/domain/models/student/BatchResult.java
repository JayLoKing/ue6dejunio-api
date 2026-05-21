package bo.edu.univalle.sis.ue6dejunio_api.domain.models.student;

import java.util.List;
import java.util.UUID;

public record BatchResult(
    int total,
    int created,
    int failed,
    List<CreatedItem> createdItems,
    List<FailedItem> failedItems
) {
    public record CreatedItem(int index, UUID id, String rudeCode) {}
    public record FailedItem(int index, String rudeCode, String reason) {}
}
