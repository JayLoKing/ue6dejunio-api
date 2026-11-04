package bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.Student;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.student.StudentDetails;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.student.IStudentService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.AuthorizationComponent;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.security.JwtAuthConverter;
import bo.edu.univalle.sis.ue6dejunio_api.infrastructure.web.error.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The edit form's request and answer. Security filters are off, as in the other web tests: who may
 * edit is exercised against a real token in {@code StudentUpdateIT}.
 */
@WebMvcTest(controllers = StudentController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@Import({StudentControllerUpdateWebTest.MockBeans.class, GlobalExceptionHandler.class})
class StudentControllerUpdateWebTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private IStudentService studentService;
    @MockitoBean private AuthorizationComponent authz;

    @TestConfiguration
    static class MockBeans {
        @Bean
        JwtDecoder jwtDecoder() {
            return mock(JwtDecoder.class);
        }

        @Bean
        JwtAuthConverter jwtAuthConverter() {
            return new JwtAuthConverter(mock(IUserDomain.class));
        }
    }

    private static final UUID ID = UUID.randomUUID();

    private static Student stored(String rude) {
        return Student.builder()
                .id(ID)
                .rudeCode(rude)
                .identityCard("C1")
                .names("Ana")
                .lastNames("Quispe")
                .birthDate(LocalDate.of(2015, 1, 1))
                .gender("F")
                .status("Effective")
                .build();
    }

    @Test
    void validBody_isPassedOn_andAnsweredWithTheStudentAndItsFlag() throws Exception {
        when(studentService.update(eq(ID), any())).thenReturn(stored(null));

        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"rudeCode": "  ", "identityCard": " C1 ",
                                         "names": " Ana ", "lastNames": "Quispe",
                                         "birthDate": "2015-01-01", "gender": "F"}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.identityCard").value("C1"))
                .andExpect(jsonPath("$.incomplete").value(true));

        ArgumentCaptor<StudentDetails> sent = ArgumentCaptor.forClass(StudentDetails.class);
        verify(studentService).update(eq(ID), sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(
                        new StudentDetails(
                                null, "C1", "Ana", "Quispe", LocalDate.of(2015, 1, 1), "F"));
    }

    @Test
    void namesOnly_isValid() throws Exception {
        when(studentService.update(eq(ID), any())).thenReturn(stored("R1"));

        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content("{\"names\": \"Ana\", \"lastNames\": \"Quispe\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void blankNames_areRefused() throws Exception {
        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content("{\"names\": \" \", \"lastNames\": \"Quispe\"}"))
                .andExpect(status().isBadRequest());
        verify(studentService, never()).update(any(), any());
    }

    @Test
    void aGenderOutsideMAndF_isRefused() throws Exception {
        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"names": "Ana", "lastNames": "Quispe", "gender": "X"}
                                        """))
                .andExpect(status().isBadRequest());
        verify(studentService, never()).update(any(), any());
    }

    @Test
    void aBirthDateInTheFuture_isRefused() throws Exception {
        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"names": "Ana", "lastNames": "Quispe",
                                         "birthDate": "2999-01-01"}
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRudeLongerThanTwentyCharacters_isRefused() throws Exception {
        mvc.perform(
                        put("/api/students/{id}", ID)
                                .contentType("application/json")
                                .content(
                                        """
                                        {"names": "Ana", "lastNames": "Quispe",
                                         "rudeCode": "123456789012345678901"}
                                        """))
                .andExpect(status().isBadRequest());
    }
}
