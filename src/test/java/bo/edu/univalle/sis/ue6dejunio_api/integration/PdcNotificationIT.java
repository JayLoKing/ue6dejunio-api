package bo.edu.univalle.sis.ue6dejunio_api.integration;

import bo.edu.univalle.sis.ue6dejunio_api.domain.models.common.PageQuery;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.Notification;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.notification.NotificationType;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.CreatePdcCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.pdc.Pdc;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.notification.INotificationService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.pdc.IPdcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec: the plan's own state changes are what write the notification, not the screen that
 * triggered them.
 *
 * <p>Until now the web sent these by hand after a successful call, which meant a plan approved
 * from anywhere else told nobody, and a browser that lost the connection between the two requests
 * left an approved plan whose author never heard. The rule belongs where the status changes.
 *
 * <p>The listener runs after commit, so a state change that never landed cannot announce itself —
 * pinned below, because that is the whole reason it is not a plain method call.
 */
class PdcNotificationIT extends AbstractIntegrationTest {

    @Autowired private IPdcService pdcService;
    @Autowired private INotificationService notificationService;

    private UUID teacher;
    private UUID director;
    private UUID course;

    @BeforeEach
    void setUp() {
        teacher = seedUser("Teacher", false);
        director = seedUser("Director", false);
        course = seedCourse(teacher, "A");
        seedClassGroup(course, teacher, "Matematicas");
    }

    private Pdc august() {
        return pdcService.create(new CreatePdcCommand(course, 4, 2,
            LocalDate.of(2026, 8, 3), LocalDate.of(2026, 9, 4),
            "Fortalecemos la práctica de valores sociocomunitarios.", null, null, null),
            teacher);
    }

    private List<Notification> inboxOf(UUID user) {
        return notificationService.inbox(user, false, PageQuery.of(0, 20)).content();
    }

    // A plan handed in is the Director's cue to review it. Nobody else can act on it.
    @Test
    void handingAPlanInTellsTheDirector() {
        Pdc plan = august();

        pdcService.publish(plan.getId(), teacher);

        assertThat(inboxOf(director)).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.PDC_PUBLISHED);
            assertThat(n.resourceType()).isEqualTo("CURRICULUM_PLAN");
            assertThat(n.resourceId()).isEqualTo(plan.getId());
        });
        assertThat(inboxOf(teacher)).isEmpty();
    }

    // A school with two Directors has two people who might pick the review up. Telling the first
    // one found leaves the other blind to a plan that is waiting.
    @Test
    void everyActiveDirectorIsTold() {
        UUID second = seedUser("Director", false);
        Pdc plan = august();

        pdcService.publish(plan.getId(), teacher);

        assertThat(inboxOf(director)).hasSize(1);
        assertThat(inboxOf(second)).hasSize(1);
    }

    @Test
    void approvingTellsTheAuthor() {
        Pdc plan = august();
        pdcService.publish(plan.getId(), teacher);

        pdcService.approve(plan.getId(), director);

        assertThat(inboxOf(teacher)).singleElement().satisfies(n ->
            assertThat(n.type()).isEqualTo(NotificationType.PDC_APPROVED));
    }

    // An observation without what to correct is useless to the person who has to correct it.
    @Test
    void observingTellsTheAuthorWhatToCorrect() {
        Pdc plan = august();
        pdcService.publish(plan.getId(), teacher);

        pdcService.observe(plan.getId(), "Falta el objetivo de agosto", director);

        assertThat(inboxOf(teacher)).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo(NotificationType.PDC_OBSERVED);
            assertThat(n.message()).contains("Falta el objetivo de agosto");
        });
    }

    /**
     * The reason this is an event after commit and not a call inside the service.
     *
     * <p>Publishing a plan that is already published is refused, and the transaction rolls back.
     * A notification written by a plain method call would already be gone — or worse, committed on
     * its own — and the teacher would read that a plan was handed in when nothing was.
     */
    @Test
    void aStateChangeThatWasRefusedAnnouncesNothing() {
        Pdc plan = august();
        pdcService.publish(plan.getId(), teacher);
        int alreadyThere = inboxOf(director).size();

        assertThatThrownBy(() -> pdcService.publish(plan.getId(), teacher))
            .isInstanceOf(RuntimeException.class);

        assertThat(inboxOf(director)).hasSize(alreadyThere);
    }

    // The system wrote it, not a person. Putting a name on it would credit someone for a line
    // nobody typed.
    @Test
    void whatTheSystemWritesCarriesNoSender() {
        Pdc plan = august();

        pdcService.publish(plan.getId(), teacher);

        assertThat(inboxOf(director).get(0).senderId()).isNull();
    }
}
