package bo.edu.univalle.sis.ue6dejunio_api.application.services.user;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.Normalizer;

@Component
public class UsernameGenerator {

    private static final SecureRandom RAND = new SecureRandom();
    private static final int MAX_ATTEMPTS = 50;

    private final IUserDomain userDomain;

    public UsernameGenerator(IUserDomain userDomain) {
        this.userDomain = userDomain;
    }

    public String generateUnique(String names, String lastNames) {
        String lastPart = take3Letters(lastNames);
        String namePart = take3Letters(names);
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = "6J" + lastPart + namePart + randomDigits4() + "UE";
            if (!userDomain.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar username único tras " + MAX_ATTEMPTS + " intentos");
    }

    private static String take3Letters(String input) {
        if (input == null) return "XXX";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
            .replace("ñ", "N").replace("Ñ", "N")
            .replaceAll("[^A-Za-z]", "")
            .toUpperCase();
        if (normalized.length() >= 3) return normalized.substring(0, 3);
        StringBuilder sb = new StringBuilder(normalized);
        while (sb.length() < 3) sb.append('X');
        return sb.toString();
    }

    private static String randomDigits4() {
        return String.format("%04d", RAND.nextInt(10000));
    }
}
