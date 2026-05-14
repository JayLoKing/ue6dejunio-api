package bo.edu.univalle.sis.ue6dejunio_api.application.services.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasswordGenerator {

    private static final SecureRandom RAND = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 12;

    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        sb.append(UPPER.charAt(RAND.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RAND.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RAND.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(RAND.nextInt(SYMBOLS.length())));
        for (int i = sb.length(); i < LENGTH; i++) {
            sb.append(ALL.charAt(RAND.nextInt(ALL.length())));
        }
        return shuffle(sb.toString());
    }

    private static String shuffle(String s) {
        char[] chars = s.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RAND.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
    }
}
