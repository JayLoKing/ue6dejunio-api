package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spec: a notification says what it is, what it is about, and what happened to it.
 *
 * <p>An inbox of bare sentences cannot tell a note about the notebook from a summons to the
 * office, and the Director who writes "aproximese a direccion urgentemente" has no way to know it
 * reached anyone. The columns exist for those two questions, and the mapping is checked against a
 * real database because a service test mocks the port: a column the adapter never carries reads
 * exactly like one the sender left empty.
 */
class NotificationSubjectIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private INotificationService notificationService;

    private UUID director;
    private UUID teacher;

    @BeforeEach
    void setUp() {
        director = seedUser("Director", false);
        teacher = seedUser("Teacher", false);
    }

    private String body(Map<String, Object> fields) throws Exception {
        Map<String, Object> all = new HashMap<>(fields);
        all.putIfAbsent("receiver_id", teacher.toString());
        all.putIfAbsent("message", "Aproximese a direccion.");
        return json.writeValueAsString(all);
    }

    private void send(Map<String, Object> fields, int expectedStatus) throws Exception {
        mvc.perform(post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(fields))
                .header("Authorization", "Bearer " + tokenFor(director, "Director")))
            .andExpect(status().is(expectedStatus));
    }

    /** The teacher's own inbox, as the teacher sees it. */
    private JsonNode inbox() throws Exception {
        String payload = mvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(payload).get("content");
    }

    @Test
    void aNotificationKeepsWhatItIsAndWhatItIsAboutThroughARoundTrip() throws Exception {
        UUID plan = UUID.randomUUID();
        send(Map.of(
            "type", "PDC_PROGRESS",
            "resource_type", "CURRICULUM_PLAN",
            "resource_id", plan.toString()), 200);

        JsonNode row = inbox().get(0);

        assertThat(row.get("type").asText()).isEqualTo("PDC_PROGRESS");
        assertThat(row.get("resourceType").asText()).isEqualTo("CURRICULUM_PLAN");
        assertThat(row.get("resourceId").asText()).isEqualTo(plan.toString());
    }

    // The catalog covers the reasons the school writes to someone. A subject the Director types by
    // hand is only for what the catalog does not name.
    @Test
    void aCustomNotificationCarriesTheSubjectTheDirectorTyped() throws Exception {
        send(Map.of("type", "CUSTOM", "subject", "Reunion de padres"), 200);

        assertThat(inbox().get(0).get("subject").asText()).isEqualTo("Reunion de padres");
    }

    // A row with no subject in an inbox is indistinguishable from the next one. The catalog types
    // are their own subject, so this is the one case where the sender has to say it.
    @Test
    void aCustomNotificationWithoutASubjectIsRefused() throws Exception {
        send(new HashMap<>(Map.of("type", "CUSTOM")), 400);
    }

    @Test
    void aCatalogTypeNeedsNoSubjectOfItsOwn() throws Exception {
        send(Map.of("type", "ATTENDANCE"), 200);
    }

    // The half that drifts if nobody guards it. A catalog type already is its heading, so a
    // subject beside it leaves the row carrying two of them and the inbox choosing one.
    @Test
    void aCatalogTypeWithASubjectOfItsOwnIsRefused() throws Exception {
        send(Map.of("type", "ATTENDANCE", "subject", "Otra cosa"), 400);
    }

    @Test
    void aTypeOutsideTheCatalogIsRefused() throws Exception {
        send(Map.of("type", "WHATEVER"), 400);
    }

    /**
     * The receipt the Director asked for. Delivery is the server saying it handed the row over —
     * the only moment it can honestly claim to know, since nothing else on the receiver's side
     * reports back.
     */
    @Test
    void theFirstTimeTheInboxCarriesItTheNotificationIsMarkedDelivered() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);

        assertThat(inbox().get(0).get("deliveredAt").isNull())
            .as("nothing has been handed over yet on the first read")
            .isFalse();
    }

    /**
     * The stamp is in the row, not only in the answer that reported it.
     *
     * <p>Delivery is written by a bulk statement, which bypasses the persistence context: the
     * entities the inbox is holding keep their old null for the rest of the transaction. That is
     * safe only because nothing writes the stamp into them — leave them clean and Hibernate
     * flushes nothing back over the update; touch them and the snapshot goes out on commit and
     * undoes it. The distinction is invisible in the first response, so it is checked here by
     * asking again in a second request, against a transaction that has to load the row afresh.
     */
    @Test
    void theDeliveryStampSurvivesTheTransactionThatWroteIt() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);

        // Read through the service rather than the JSON: what is under test is what the row holds
        // and keeps holding, and the wire format is already pinned by the tests above.
        LocalDateTime first = firstDeliveredAt();

        assertThat(jdbc.queryForObject("SELECT delivered_at FROM notifications", Object.class))
            .as("the row itself, read outside the transaction that stamped it")
            .isNotNull();
        assertThat(firstDeliveredAt())
            .as("the same instant on every later read, not a fresh one each time")
            .isEqualTo(first);
    }

    private LocalDateTime firstDeliveredAt() {
        return notificationService.inbox(teacher, false, PageQuery.of(0, 20))
            .content().get(0).deliveredAt();
    }

    @Test
    void anUnreadNotificationHasNoReadStamp() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);

        JsonNode row = inbox().get(0);
        assertThat(row.get("readAt").isNull()).isTrue();
        assertThat(row.get("read").asBoolean()).isFalse();
    }

    @Test
    void readingItStampsWhenItWasRead() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);
        String id = inbox().get(0).get("id").asText();

        mvc.perform(post("/api/notifications/" + id + "/read")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.read").value(true))
            .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    /**
     * What the badge reads after the receiver clears it.
     *
     * <p>The field is called unread, and this answered with the number of rows it had just
     * updated: clearing seven notifications replied {@code {"unread": 7}} and left the bell
     * showing seven the receiver had already dismissed.
     */
    @Test
    void clearingTheInboxAnswersWithNoneLeftUnread() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);
        send(Map.of("type", "ATTENDANCE"), 200);

        mvc.perform(post("/api/notifications/read-all")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unread").value(0));
    }

    // The unread listing is the badge the receiver sees. It reads the stamp now, not a boolean
    // kept beside it, so there is no second place for the same fact to go stale.
    @Test
    void theUnreadListingLeavesOutWhatWasAlreadyRead() throws Exception {
        send(Map.of("type", "SUMMONS"), 200);
        send(Map.of("type", "ATTENDANCE"), 200);
        String id = inbox().get(0).get("id").asText();

        mvc.perform(post("/api/notifications/" + id + "/read")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk());

        mvc.perform(get("/api/notifications?unreadOnly=true")
                .header("Authorization", "Bearer " + tokenFor(teacher, "Teacher")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));
    }
}
