package bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The message of a duplicate is read by whoever hit it, not by whoever wrote the check.
 *
 * <p>The web prints this string verbatim in a toast. It used to be {@code Recurso duplicado:
 * email=ana@ue6.bo} — a field name, an equals sign and a value — so the Director creating an
 * account got handed a log line and had to translate it.
 */
class DuplicateResourceExceptionTest {

    @Test
    void email_namesTheThingTheSchoolCallsIt() {
        assertThat(new DuplicateResourceException("email", "ana@ue6.bo"))
                .hasMessage("El correo ana@ue6.bo ya está registrado con un usuario del sistema.");
    }

    @Test
    void ci_namesTheThingTheSchoolCallsIt() {
        assertThat(new DuplicateResourceException("ci", "1234567"))
                .hasMessage("El CI 1234567 ya está registrado con un usuario del sistema.");
    }

    @Test
    void name_readsAsASentence() {
        assertThat(new DuplicateResourceException("name", "Primaria"))
                .hasMessage("Ya existe un registro con el nombre \"Primaria\".");
    }

    /**
     * An unmapped field still reads as a sentence.
     *
     * <p>Half the callers pass a composite key like {@code curso (grado+paralelo+anio)}, which is
     * already a description rather than a column. The fallback frames it instead of printing the
     * raw pair, and the value is kept because for a composite it is the only part that says which
     * record collided.
     */
    @Test
    void anUnmappedField_isFramedRatherThanPrinted() {
        assertThat(new DuplicateResourceException("curso (grado+paralelo+anio)", "1-A-2026"))
                .hasMessage(
                        "Ya existe un registro con esos datos: curso (grado+paralelo+anio) = 1-A-2026.");
    }

    /**
     * The address is named, not implied.
     *
     * <p>A Director pasting a list of new staff is not always looking at the field they typed it
     * into, and "ya existe un usuario con ese correo" leaves them hunting for which one.
     */
    @Test
    void email_namesTheAddressThatCollided() {
        assertThat(new DuplicateResourceException("email", "ana@ue6.bo").getMessage())
                .contains("ana@ue6.bo");
    }
}
