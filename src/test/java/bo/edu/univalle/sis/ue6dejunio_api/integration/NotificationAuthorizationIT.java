package bo.edu.univalle.sis.ue6dejunio_api.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: only the Director sends a notification.
 *
 * <p>A notification carries the school's authority — it summons someone to the office, it says a
 * plan was observed. Left open to anyone holding a token, a teacher could message another teacher
 * or the secretary in the system's own voice, and nothing in the inbox would say it was not the
 * Director who wrote it.
 *
 * <p>The endpoint was reachable by any authenticated user: {@code SecurityConfig} only asked for
 * authentication on the route and the method carried no authority check of its own.
 */
class NotificationAuthorizationIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private String bodyTo(UUID receiver) throws Exception {
        return json.writeValueAsString(Map.of(
            "receiver_id", receiver.toString(),
            "type", "SUMMONS",
            "message", "Aproximese a direccion."));
    }

    private void send(UUID sender, String role, UUID receiver, int expectedStatus) throws Exception {
        mvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyTo(receiver))
                .header("Authorization", "Bearer " + tokenFor(sender, role)))
            .andExpect(status().is(expectedStatus));
    }

    @Test
    void aTeacherCannotSendANotification() throws Exception {
        send(seedUser("Teacher", false), "Teacher", seedUser("Teacher", false), 403);
    }

    @Test
    void aSecretaryCannotSendANotification() throws Exception {
        send(seedUser("Secretary", false), "Secretary", seedUser("Teacher", false), 403);
    }

    @Test
    void theDirectorSends() throws Exception {
        send(seedUser("Director", false), "Director", seedUser("Teacher", false), 200);
    }

    @Test
    void theDirectorWritesToTheSecretaryToo() throws Exception {
        send(seedUser("Director", false), "Director", seedUser("Secretary", false), 200);
    }

    /**
     * A notification about a plan is written by the plan changing, and by nothing else.
     *
     * <p>Left open, the Director could post PDC_APPROVED by hand and a teacher would read that
     * their plan was approved while it still sat unreviewed — the inbox saying one thing and the
     * plan another, with no way to tell which is true. Those three types belong to the listener.
     */
    @Test
    void theDirectorCannotHandWriteWhatTheSystemAnnounces() throws Exception {
        UUID director = seedUser("Director", false);
        UUID teacher = seedUser("Teacher", false);

        for (String systemOnly : new String[]{"PDC_PUBLISHED", "PDC_APPROVED", "PDC_OBSERVED"}) {
            mvc.perform(post("/api/notifications")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of(
                        "receiver_id", teacher.toString(),
                        "type", systemOnly,
                        "message", "Tu plan fue aprobado.")))
                    .header("Authorization", "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isBadRequest());
        }
    }

    // The Director supervises teachers and the secretary. Another Director is a peer, and himself
    // is nobody — a notification is how the school reaches someone who answers to it.
    @Test
    void theDirectorDoesNotWriteToAnotherDirector() throws Exception {
        send(seedUser("Director", false), "Director", seedUser("Director", false), 400);
    }

    @Test
    void theDirectorDoesNotWriteToHimself() throws Exception {
        UUID director = seedUser("Director", false);

        send(director, "Director", director, 400);
    }
}
