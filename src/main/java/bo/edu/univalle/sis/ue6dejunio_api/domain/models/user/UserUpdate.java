package bo.edu.univalle.sis.ue6dejunio_api.domain.models.user;

/**
 * What an edit by the Director left behind: the user as it now reads, and whether fixing the
 * address also sent a fresh temporary password to it. The web tells the Director so, because a new
 * mail went out on their behalf.
 */
public record UserUpdate(User user, boolean credentialsResent) {}
