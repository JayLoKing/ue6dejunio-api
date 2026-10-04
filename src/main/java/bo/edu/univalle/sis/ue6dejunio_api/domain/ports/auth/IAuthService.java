package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.UpdateProfileCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import java.util.UUID;

public interface IAuthService {
    AuthenticatedUser login(LoginCommand command);

    /**
     * Issues a fresh token for a session that is still inside its window, so working past the
     * access TTL does not mean being thrown out mid-sentence.
     *
     * <p>Takes the user id the caller's token already proved, not the token itself: whether the
     * bearer is genuine is the resource server's job, and it has already run by the time this is
     * called. What this adds is the part a signature cannot answer — the account is re-read, so one
     * closed in the meantime stops renewing, and the teacher's homeroom claims are recomputed
     * rather than carried over.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException if the
     *     school has closed the account
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException if the
     *     user behind the token no longer exists
     */
    AuthenticatedUser refresh(UUID userId);

    void changePassword(ChangePasswordCommand command);

    /**
     * Lets a user correct their own names, last names and phone, and answers with the session as it
     * now reads: the token's name claim is stale after an edit, so the caller gets the fresh values
     * the same way a refresh would.
     *
     * <p>CI, email, role and active are not parameters on purpose; the school owns those.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException if the
     *     account is closed
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException if the
     *     user behind the token no longer exists
     */
    AuthenticatedUser updateProfile(UpdateProfileCommand command);

    /**
     * The caller's own row, for the profile form: the token has only a joined full name, and the
     * form edits names and last names apart.
     *
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.UserInactiveException if the
     *     account is closed
     * @throws bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException if the
     *     user behind the token no longer exists
     */
    User getProfile(UUID userId);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);
}
