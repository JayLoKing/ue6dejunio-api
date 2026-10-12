package bo.edu.univalle.sis.ue6dejunio_api.domain.models.courseenrollment;

/**
 * A roster row a load left out because its RUDE code and its identity card belong to two different
 * students on record, so the row names nobody for certain.
 *
 * @param rudeCode the RUDE code the row carried
 * @param identityCard the identity card the row carried
 * @param fullName the name the row carried, as the file printed it
 * @param reason who each of the two identifiers belongs to, for the person reading the result
 */
public record EnrollConflict(
        String rudeCode, String identityCard, String fullName, String reason) {}
