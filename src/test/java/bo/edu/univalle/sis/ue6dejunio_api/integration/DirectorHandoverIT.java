package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The handover endpoints: opening it (creating or promoting the incoming Director), reading it,
 * cancelling it, and who may do each. What happens when the incoming Director sets a password is
 * {@link DirectorHandoverCompletionIT}.
 */
class DirectorHandoverIT extends AbstractIntegrationTest {

    private static final String URL = "/api/users/director-handover";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockitoBean private IEmailService emailService;

    private UUID director;

    @BeforeEach
    void seed() {
        director = seedUser("Director", false);
    }

    private String bearer(UUID id, String role) {
        return "Bearer " + tokenFor(id, role);
    }

    private ResultActions start(UUID caller, String role, Map<String, Object> body)
            throws Exception {
        return mvc.perform(
                post(URL)
                        .header("Authorization", bearer(caller, role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)));
    }

    private ResultActions startAsDirector(Map<String, Object> body) throws Exception {
        return start(director, "Director", body);
    }

    private static Map<String, Object> newPerson() {
        return Map.of(
                "email",
                "luis.rojas@ue6.test",
                "ci",
                "7654321",
                "names",
                "Luis",
                "lastNames",
                "Rojas",
                "phone",
                "70011122");
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id_user = ?", String.class, id);
    }

    private String roleOf(UUID id) {
        return jdbc.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id_role = u.id_role "
                        + "WHERE u.id_user = ?",
                String.class,
                id);
    }

    private boolean activeOf(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT is_active FROM users WHERE id_user = ?", Boolean.class, id));
    }

    private int handoverRows() {
        return jdbc.queryForObject("SELECT count(*) FROM director_handovers", Integer.class);
    }

    private UUID idByEmail(String email) {
        return jdbc.queryForObject("SELECT id_user FROM users WHERE email = ?", UUID.class, email);
    }

    // ---- opening: a new account -------------------------------------------------------------

    @Test
    void start_withANewPerson_createsADirector_opensTheRow_andMailsTheCredentials()
            throws Exception {
        String body =
                startAsDirector(newPerson())
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.id").isNotEmpty())
                        .andExpect(jsonPath("$.incomingCreated").value(true))
                        .andExpect(jsonPath("$.startedAt").isNotEmpty())
                        .andExpect(jsonPath("$.outgoing.id").value(director.toString()))
                        .andExpect(jsonPath("$.outgoing.email").value(emailOf(director)))
                        .andExpect(jsonPath("$.incoming.fullName").value("Luis Rojas"))
                        .andExpect(jsonPath("$.incoming.email").value("luis.rojas@ue6.test"))
                        .andExpect(jsonPath("$.incoming.mustChangePassword").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        UUID incoming = idByEmail("luis.rojas@ue6.test");
        assertThat(roleOf(incoming)).isEqualTo("Director");
        assertThat(activeOf(incoming)).isTrue();
        assertThat(roleOf(director)).isEqualTo("Director");
        assertThat(activeOf(director)).isTrue();
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(emailService)
                .sendWelcomeCredentials(
                        org.mockito.ArgumentMatchers.eq("luis.rojas@ue6.test"),
                        org.mockito.ArgumentMatchers.eq("Luis Rojas"),
                        raw.capture());
        String stored =
                jdbc.queryForObject(
                        "SELECT password FROM users WHERE id_user = ?", String.class, incoming);
        assertThat(passwordEncoder.matches(raw.getValue(), stored)).isTrue();
        assertThat(body).doesNotContain(raw.getValue()).doesNotContain(stored);
        assertThat(handoverRows()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT incoming_created FROM director_handovers", Boolean.class))
                .isTrue();
    }

    @Test
    void start_withANewPersonMissingTheirData_is400_andNothingIsCreated() throws Exception {
        startAsDirector(Map.of("email", "luis.rojas@ue6.test")).andExpect(status().isBadRequest());

        assertThat(handoverRows()).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE email = ?",
                                Integer.class,
                                "luis.rojas@ue6.test"))
                .isZero();
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void start_withATakenCi_is409() throws Exception {
        UUID other = seedUser("Teacher", false);
        String ci =
                jdbc.queryForObject("SELECT ci FROM users WHERE id_user = ?", String.class, other);
        Map<String, Object> body =
                Map.of(
                        "email", "luis.rojas@ue6.test",
                        "ci", ci,
                        "names", "Luis",
                        "lastNames", "Rojas");

        startAsDirector(body).andExpect(status().isConflict());

        assertThat(handoverRows()).isZero();
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void start_withAnInvalidEmailOrCi_is400() throws Exception {
        startAsDirector(Map.of("email", "not-an-email")).andExpect(status().isBadRequest());
        startAsDirector(
                        Map.of(
                                "email", "luis.rojas@ue6.test",
                                "ci", "12",
                                "names", "Luis",
                                "lastNames", "Rojas"))
                .andExpect(status().isBadRequest());
        startAsDirector(Map.of()).andExpect(status().isBadRequest());
        assertThat(handoverRows()).isZero();
    }

    // ---- opening: promoting an existing user ------------------------------------------------

    @Test
    void start_withAnExistingTeacher_promotesThem_keepingTheirAssignmentsAndTechnicalFlag()
            throws Exception {
        UUID teacher = seedUser("Teacher", true);
        UUID course = seedCourse(teacher, "A");
        UUID classGroup = seedClassGroup(course, teacher, "Matematicas");
        String email = emailOf(teacher);
        String oldHash =
                jdbc.queryForObject(
                        "SELECT password FROM users WHERE id_user = ?", String.class, teacher);

        startAsDirector(Map.of("email", email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incomingCreated").value(false))
                .andExpect(jsonPath("$.incoming.id").value(teacher.toString()))
                .andExpect(jsonPath("$.incoming.mustChangePassword").value(true));

        assertThat(roleOf(teacher)).isEqualTo("Director");
        assertThat(activeOf(teacher)).isTrue();
        String newHash =
                jdbc.queryForObject(
                        "SELECT password FROM users WHERE id_user = ?", String.class, teacher);
        assertThat(newHash).isNotEqualTo(oldHash);
        Integer previous =
                jdbc.queryForObject(
                        "SELECT previous_role_id FROM director_handovers", Integer.class);
        assertThat(previous)
                .isEqualTo(
                        jdbc.queryForObject(
                                "SELECT id_role FROM roles WHERE name = 'Teacher'", Integer.class));
        // The homeroom and the class group are NOT reassigned: they still name the promoted user.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_homeroom_teacher FROM courses WHERE id_course = ?",
                                UUID.class,
                                course))
                .isEqualTo(teacher);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT id_teacher FROM class_groups WHERE id_class_group = ?",
                                UUID.class,
                                classGroup))
                .isEqualTo(teacher);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_technical FROM users WHERE id_user = ?",
                                Boolean.class,
                                teacher))
                .isTrue();
        verify(emailService).sendWelcomeCredentials(anyString(), anyString(), anyString());
    }

    // ---- opening: refusals ------------------------------------------------------------------

    @Test
    void start_toOneself_is409() throws Exception {
        startAsDirector(Map.of("email", emailOf(director))).andExpect(status().isConflict());

        assertThat(handoverRows()).isZero();
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void start_toAnotherDirector_is409() throws Exception {
        UUID other = seedUser("Director", false);

        startAsDirector(Map.of("email", emailOf(other))).andExpect(status().isConflict());

        assertThat(handoverRows()).isZero();
    }

    @Test
    void start_toADeactivatedUser_is409() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        jdbc.update("UPDATE users SET is_active = false WHERE id_user = ?", teacher);

        startAsDirector(Map.of("email", emailOf(teacher))).andExpect(status().isConflict());

        assertThat(roleOf(teacher)).isEqualTo("Teacher");
        assertThat(handoverRows()).isZero();
    }

    @Test
    void aSecondHandover_whileOneIsPending_is409_andTheFirstIsUntouched() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        UUID teacher = seedUser("Teacher", false);

        startAsDirector(Map.of("email", emailOf(teacher))).andExpect(status().isConflict());

        assertThat(handoverRows()).isEqualTo(1);
        assertThat(roleOf(teacher)).isEqualTo("Teacher");
    }

    // ---- reading ----------------------------------------------------------------------------

    @Test
    void get_withNoHandover_is204() throws Exception {
        mvc.perform(get(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void get_whilePending_describesIt_toBothDirectors() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        UUID incoming = idByEmail("luis.rojas@ue6.test");

        mvc.perform(get(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomingCreated").value(true))
                .andExpect(jsonPath("$.outgoing.id").value(director.toString()))
                .andExpect(jsonPath("$.incoming.id").value(incoming.toString()))
                .andExpect(jsonPath("$.incoming.mustChangePassword").value(true));
        mvc.perform(get(URL).header("Authorization", bearer(incoming, "Director")))
                .andExpect(status().isOk());
    }

    // ---- cancelling -------------------------------------------------------------------------

    @Test
    void cancel_aCreatedIncoming_deactivatesThem_andLeavesTheOutgoingAlone() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        UUID incoming = idByEmail("luis.rojas@ue6.test");

        mvc.perform(delete(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent());

        assertThat(activeOf(incoming)).isFalse();
        assertThat(activeOf(director)).isTrue();
        assertThat(roleOf(director)).isEqualTo("Director");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM director_handovers "
                                        + "WHERE cancelled_at IS NOT NULL AND closed_at IS NULL",
                                Integer.class))
                .isEqualTo(1);
        mvc.perform(get(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent());
    }

    @Test
    void cancel_aPromotedIncoming_getsTheOldRoleBack_andStaysActive() throws Exception {
        UUID teacher = seedUser("Teacher", true);
        startAsDirector(Map.of("email", emailOf(teacher))).andExpect(status().isCreated());
        assertThat(roleOf(teacher)).isEqualTo("Director");

        mvc.perform(delete(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent());

        assertThat(roleOf(teacher)).isEqualTo("Teacher");
        assertThat(activeOf(teacher)).isTrue();
        assertThat(activeOf(director)).isTrue();
    }

    @Test
    void cancel_withNothingPending_is404() throws Exception {
        mvc.perform(delete(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_byTheIncomingDirector_is403_andTheHandoverStaysPending() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        UUID incoming = idByEmail("luis.rojas@ue6.test");

        mvc.perform(delete(URL).header("Authorization", bearer(incoming, "Director")))
                .andExpect(status().isForbidden());

        assertThat(activeOf(incoming)).isTrue();
        mvc.perform(get(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isOk());
    }

    @Test
    void afterACancel_aNewHandoverCanBeOpened() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        mvc.perform(delete(URL).header("Authorization", bearer(director, "Director")))
                .andExpect(status().isNoContent());
        UUID teacher = seedUser("Teacher", false);

        startAsDirector(Map.of("email", emailOf(teacher))).andExpect(status().isCreated());

        assertThat(handoverRows()).isEqualTo(2);
    }

    // ---- who may call -----------------------------------------------------------------------

    @Test
    void teacherAndSecretary_get403_onEveryHandoverEndpoint() throws Exception {
        UUID teacher = seedUser("Teacher", false);
        UUID secretary = seedUser("Secretary", false);

        for (Map.Entry<UUID, String> caller :
                Map.of(teacher, "Teacher", secretary, "Secretary").entrySet()) {
            String auth = bearer(caller.getKey(), caller.getValue());
            start(caller.getKey(), caller.getValue(), newPerson())
                    .andExpect(status().isForbidden());
            mvc.perform(get(URL).header("Authorization", auth)).andExpect(status().isForbidden());
            mvc.perform(delete(URL).header("Authorization", auth))
                    .andExpect(status().isForbidden());
        }

        assertThat(handoverRows()).isZero();
        verify(emailService, never()).sendWelcomeCredentials(any(), any(), any());
    }

    @Test
    void anonymous_gets401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(delete(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- the handover is the ONLY door -------------------------------------------------------

    @Test
    void theUserForm_stillRefusesToMintADirector() throws Exception {
        Integer directorRole =
                jdbc.queryForObject(
                        "SELECT id_role FROM roles WHERE name = 'Director'", Integer.class);
        UUID teacher = seedUser("Teacher", false);

        mvc.perform(
                        post("/api/users")
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "ci", "9998887",
                                                        "names", "Eva",
                                                        "lastNames", "Soto",
                                                        "email", "eva@ue6.test",
                                                        "roleId", directorRole))))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/users/{id}", teacher)
                                .header("Authorization", bearer(director, "Director"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("roleId", directorRole))))
                .andExpect(status().isBadRequest());

        assertThat(roleOf(teacher)).isEqualTo("Teacher");
        assertThat(handoverRows()).isZero();
    }

    // ---- resend during a pending handover ---------------------------------------------------

    @Test
    void resendCredentials_toThePendingIncomingDirector_works() throws Exception {
        startAsDirector(newPerson()).andExpect(status().isCreated());
        UUID incoming = idByEmail("luis.rojas@ue6.test");

        mvc.perform(
                        post("/api/users/{id}/resend-credentials", incoming)
                                .header("Authorization", bearer(director, "Director")))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.times(2))
                .sendWelcomeCredentials(
                        org.mockito.ArgumentMatchers.eq("luis.rojas@ue6.test"),
                        anyString(),
                        anyString());
    }
}
