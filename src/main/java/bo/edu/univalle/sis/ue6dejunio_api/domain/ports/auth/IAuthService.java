package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;
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

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);
}
