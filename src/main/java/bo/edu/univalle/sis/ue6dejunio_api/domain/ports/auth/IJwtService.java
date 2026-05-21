package bo.edu.univalle.sis.ue6dejunio_api.domain.ports.auth;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.auth.AuthenticatedUser;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;

public interface IJwtService {
    AuthenticatedUser issueToken(User user, String gradeName, String parallelName);
}
