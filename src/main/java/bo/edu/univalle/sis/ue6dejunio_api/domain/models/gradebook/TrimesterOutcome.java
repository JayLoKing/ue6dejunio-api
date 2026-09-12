package bo.edu.univalle.sis.ue6dejunio_api.domain.models.gradebook;

/**
 * How a trimester closed for one student: how many areas they passed and how many they failed.
 *
 * <p>The two do not have to add up to the number of areas the student takes. An area with no mark
 * that trimester is counted in neither — it was never judged, and calling it failed would tell a
 * parent their child failed a subject nobody marked.
 */
public record TrimesterOutcome(int trimester, int passedAreas, int failedAreas) {}
