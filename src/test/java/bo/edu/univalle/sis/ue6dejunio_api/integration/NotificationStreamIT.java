package bo.edu.univalle.sis.ue6dejunio_api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spec: a reader's inbox reaches them without being asked for.
 *
 * <p>An integration test rather than a unit one because none of what could break here is in the
 * registry: it is the route being authenticated, the response being an event stream at all, and the
 * listener running after the send commits. A mocked registry answers all three the same way whether
 * the wiring exists or not.
 */
class NotificationStreamIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private MvcResult openStream(UUID reader, String role) throws Exception {
        return mvc.perform(
                        get("/api/notifications/stream")
                                .header("Authorization", "Bearer " + tokenFor(reader, role)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /** The stream is somebody's inbox. Without a token there is no somebody. */
    @Test
    void withoutAToken_theStreamIsRefused() throws Exception {
        mvc.perform(get("/api/notifications/stream")).andExpect(status().isUnauthorized());
    }

    /**
     * The first event is what flushes the response headers. Without it the browser sits on an open
     * request it cannot tell apart from one that never connected, and the client's fallback poll
     * would be switched off on the strength of a connection that is not there.
     */
    @Test
    void anOpenStream_saysSoStraightAway() throws Exception {
        MvcResult stream = openStream(seedUser("Teacher", false), "Teacher");

        assertThat(stream.getResponse().getContentType())
                .startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(stream.getResponse().getContentAsString()).contains("event:ready");
    }

    /**
     * The whole chain, end to end: the Director writes, the send commits, and the teacher holding a
     * stream open is told to go and read it.
     *
     * <p>The event carries the notification's id and not a word of its text. What was said stays
     * behind the inbox endpoint, which is the one place that decides who may read it.
     */
    @Test
    void aNotificationWritten_nudgesTheStreamOfItsReceiver() throws Exception {
        UUID director = seedUser("Director", false);
        UUID teacher = seedUser("Teacher", false);
        MvcResult stream = openStream(teacher, "Teacher");

        mvc.perform(
                        post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "receiver_id", teacher.toString(),
                                                        "type", "SUMMONS",
                                                        "message", "Aproximese a direccion.")))
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk());

        String delivered = stream.getResponse().getContentAsString();
        assertThat(delivered).contains("event:notification");
        assertThat(delivered).doesNotContain("Aproximese a direccion.");
    }

    /** Somebody else's inbox growing is not this reader's business. */
    @Test
    void aNotificationForSomebodyElse_leavesTheStreamQuiet() throws Exception {
        UUID director = seedUser("Director", false);
        UUID reader = seedUser("Teacher", false);
        UUID somebodyElse = seedUser("Teacher", false);
        MvcResult stream = openStream(reader, "Teacher");

        mvc.perform(
                        post("/api/notifications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "receiver_id", somebodyElse.toString(),
                                                        "type", "SUMMONS",
                                                        "message", "Aproximese a direccion.")))
                                .header(
                                        "Authorization",
                                        "Bearer " + tokenFor(director, "Director")))
                .andExpect(status().isOk());

        assertThat(stream.getResponse().getContentAsString()).doesNotContain("event:notification");
    }
}
