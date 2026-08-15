package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.criterion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CriterionControllerDocumentationTest {

    @Test
    void controllerDocumentation_doesNotMentionWeights() {
        Tag tag = CriterionController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.description().toLowerCase()).doesNotContain("peso");
        assertThat(tag.description().toLowerCase()).doesNotContain("suma de pesos");

        Method[] methods = CriterionController.class.getDeclaredMethods();
        long operationsChecked = Arrays.stream(methods)
            .filter(m -> m.isAnnotationPresent(Operation.class))
            .peek(m -> {
                Operation op = m.getAnnotation(Operation.class);
                assertThat(op.summary().toLowerCase()).doesNotContain("peso");
                assertThat(op.summary().toLowerCase()).doesNotContain("suma de pesos");
            })
            .count();

        assertThat(operationsChecked).isGreaterThan(0);
    }
}
