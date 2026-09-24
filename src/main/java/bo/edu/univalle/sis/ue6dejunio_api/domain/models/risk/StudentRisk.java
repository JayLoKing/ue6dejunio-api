package bo.edu.univalle.sis.ue6dejunio_api.domain.models.risk;

/**
 * A prediction with the two names needed to read it: whose it is, and about which subject.
 *
 * <p>Carried alongside the prediction rather than looked up afterwards. A list of predictions is a
 * list of uuids, and every caller that has to show one ends up asking for the student and the
 * subject one row at a time — the same list turned into two hundred queries, once per reader.
 */
public record StudentRisk(
        RiskPrediction prediction,
        String studentNames,
        String studentLastNames,
        String subjectName) {

    /** How the school writes a name: last names first, the way every listing already sorts. */
    public String studentFullName() {
        return studentLastNames + " " + studentNames;
    }
}
