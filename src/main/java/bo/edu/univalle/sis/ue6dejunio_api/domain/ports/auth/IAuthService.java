package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.ChangePasswordCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.LoginCommand;

public interface IAuthService {
    AuthenticatedUser login(LoginCommand command);
    void changePassword(ChangePasswordCommand command);
    void forgotPassword(String email);
    void resetPassword(String token, String newPassword);
}
