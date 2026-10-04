package bo.edu.univalle.sis.ue6dejunio_api.application.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bo.edu.univalle.sis.ue6dejunio_api.application.services.handover.DirectorHandoverService;
import bo.edu.univalle.sis.ue6dejunio_api.application.services.user.PasswordGenerator;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ConflictException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.DuplicateResourceException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ForbiddenException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ResourceNotFoundException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.exceptions.ValidationException;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandover;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.DirectorHandoverDetail;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.handover.StartDirectorHandoverCommand;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.role.Role;
import bo.edu.univalle.sis.ue6dejunio_api.domain.models.user.User;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.handover.IDirectorHandoverDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.mail.IEmailService;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.role.IRoleDomain;
import bo.edu.univalle.sis.ue6dejunio_api.domain.ports.user.IUserDomain;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class DirectorHandoverServiceTest {

    private static final Role DIRECTOR = new Role(1, "Director");
    private static final Role TEACHER = new Role(3, "Teacher");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-03T15:00:00Z"), ZoneId.of("UTC"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 15, 0);

    @Mock private IDirectorHandoverDomain handovers;
    @Mock private IUserDomain userDomain;
    @Mock private IRoleDomain roleDomain;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordGenerator passwordGenerator;
    @Mock private IEmailService emailService;

    private DirectorHandoverService service;
    private User outgoing;

    @BeforeEach
    void setUp() {
        service =
                new DirectorHandoverService(
                        handovers,
                        userDomain,
                        roleDomain,
                        passwordEncoder,
                        passwordGenerator,
                        emailService,
                        CLOCK);
        outgoing = user("Rosa", "Mamani", "rosa@ue6.bo", DIRECTOR, true);
    }

    private static User user(String names, String last, String email, Role role, boolean active) {
        return User.builder()
                .id(UUID.randomUUID())
                .ci("CI-" + names)
                .names(names)
                .lastNames(last)
                .email(email)
                .password("$old$")
                .mustChangePassword(false)
                .technical(true)
                .role(role)
                .active(active)
                .build();
    }

    private StartDirectorHandoverCommand byEmail(String email) {
        return new StartDirectorHandoverCommand(outgoing.getId(), email, null, null, null, null);
    }

    private StartDirectorHandoverCommand newAccount(String ci, String names, String last) {
        return new StartDirectorHandoverCommand(
                outgoing.getId(), "nuevo@ue6.bo", ci, names, last, "70011122");
    }

    private void callerIsAnActiveDirector() {
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(handovers.findPending()).thenReturn(Optional.empty());
    }

    private void stubPasswordAndPersistence() {
        when(passwordGenerator.generate()).thenReturn("Tmp#Pass1");
        when(passwordEncoder.encode("Tmp#Pass1")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(handovers.save(any(DirectorHandover.class)))
                .thenAnswer(
                        inv -> {
                            DirectorHandover h = inv.getArgument(0);
                            return new DirectorHandover(
                                    UUID.randomUUID(),
                                    h.outgoingId(),
                                    h.incomingId(),
                                    h.incomingCreated(),
                                    h.previousRoleId(),
                                    h.startedAt(),
                                    null,
                                    null);
                        });
    }

    private void nothingWasWritten() {
        verify(userDomain, never()).save(any());
        verify(handovers, never()).save(any());
        verify(userDomain, never()).deactivate(any());
        verifyNoInteractions(emailService);
    }

    // ---- start: promote ---------------------------------------------------------------------

    @Test
    void start_promotesAnExistingTeacher_storingThePreviousRole_andMailsTheCredentials() {
        User teacher = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, true);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(teacher));
        when(roleDomain.findByName("Director")).thenReturn(Optional.of(DIRECTOR));
        stubPasswordAndPersistence();

        DirectorHandoverDetail detail = service.start(byEmail("ana@ue6.bo"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(DIRECTOR);
        assertThat(saved.getValue().getPassword()).isEqualTo("$hashed$");
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        assertThat(saved.getValue().isActive()).isTrue();
        ArgumentCaptor<DirectorHandover> row = ArgumentCaptor.forClass(DirectorHandover.class);
        verify(handovers).save(row.capture());
        assertThat(row.getValue().outgoingId()).isEqualTo(outgoing.getId());
        assertThat(row.getValue().incomingId()).isEqualTo(teacher.getId());
        assertThat(row.getValue().incomingCreated()).isFalse();
        assertThat(row.getValue().previousRoleId()).isEqualTo(TEACHER.id());
        assertThat(row.getValue().startedAt()).isEqualTo(NOW);
        assertThat(detail.incomingCreated()).isFalse();
        assertThat(detail.outgoing()).isSameAs(outgoing);
        assertThat(detail.incoming().getId()).isEqualTo(teacher.getId());
        assertThat(detail.startedAt()).isEqualTo(NOW);
        verify(emailService).sendWelcomeCredentials("ana@ue6.bo", "Ana Quispe", "Tmp#Pass1");
    }

    @Test
    void start_theMailGoesOutOnlyAfterTheRowIsStored() {
        User teacher = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, true);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(teacher));
        when(roleDomain.findByName("Director")).thenReturn(Optional.of(DIRECTOR));
        stubPasswordAndPersistence();

        service.start(byEmail("ana@ue6.bo"));

        InOrder order = inOrder(handovers, emailService);
        order.verify(handovers).save(any(DirectorHandover.class));
        order.verify(emailService).sendWelcomeCredentials(anyString(), anyString(), anyString());
    }

    @Test
    void start_trimsTheEmailBeforeLookingItUp() {
        User teacher = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, true);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(teacher));
        when(roleDomain.findByName("Director")).thenReturn(Optional.of(DIRECTOR));
        stubPasswordAndPersistence();

        service.start(byEmail("  ana@ue6.bo "));

        verify(userDomain).findByEmail("ana@ue6.bo");
    }

    // ---- start: create ----------------------------------------------------------------------

    @Test
    void start_createsANewDirector_whenNobodyHasTheEmail() {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findByName("Director")).thenReturn(Optional.of(DIRECTOR));
        stubPasswordAndPersistence();

        DirectorHandoverDetail detail = service.start(newAccount("1234567", "Luis", "Rojas"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        User created = saved.getValue();
        assertThat(created.getCi()).isEqualTo("1234567");
        assertThat(created.getNames()).isEqualTo("Luis");
        assertThat(created.getLastNames()).isEqualTo("Rojas");
        assertThat(created.getPhone()).isEqualTo("70011122");
        assertThat(created.getEmail()).isEqualTo("nuevo@ue6.bo");
        assertThat(created.getRole()).isEqualTo(DIRECTOR);
        assertThat(created.getPassword()).isEqualTo("$hashed$");
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.isActive()).isTrue();
        assertThat(created.isTechnical()).isFalse();
        ArgumentCaptor<DirectorHandover> row = ArgumentCaptor.forClass(DirectorHandover.class);
        verify(handovers).save(row.capture());
        assertThat(row.getValue().incomingCreated()).isTrue();
        assertThat(row.getValue().previousRoleId()).isNull();
        assertThat(detail.incomingCreated()).isTrue();
        verify(emailService).sendWelcomeCredentials("nuevo@ue6.bo", "Luis Rojas", "Tmp#Pass1");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void start_aNewAccountWithoutCi_isRefused(String ci) {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(newAccount(ci, "Luis", "Rojas")))
                .isInstanceOf(ValidationException.class);
        nothingWasWritten();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void start_aNewAccountWithoutNames_isRefused(String names) {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(newAccount("1234567", names, "Rojas")))
                .isInstanceOf(ValidationException.class);
        nothingWasWritten();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void start_aNewAccountWithoutLastNames_isRefused(String last) {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(newAccount("1234567", "Luis", last)))
                .isInstanceOf(ValidationException.class);
        nothingWasWritten();
    }

    @Test
    void start_aNewAccountWhoseCiIsTaken_isADuplicate() {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());
        when(userDomain.existsByCi("1234567")).thenReturn(true);

        assertThatThrownBy(() -> service.start(newAccount("1234567", "Luis", "Rojas")))
                .isInstanceOf(DuplicateResourceException.class);
        nothingWasWritten();
    }

    @Test
    void start_withoutADirectorRoleInTheCatalogue_failsBeforeWritingAnything() {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("nuevo@ue6.bo")).thenReturn(Optional.empty());
        when(userDomain.existsByCi("1234567")).thenReturn(false);
        when(roleDomain.findByName("Director")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(newAccount("1234567", "Luis", "Rojas")))
                .isInstanceOf(ResourceNotFoundException.class);
        nothingWasWritten();
    }

    // ---- start: guards ----------------------------------------------------------------------

    @Test
    void start_aBlankEmail_isRefused() {
        callerIsAnActiveDirector();

        assertThatThrownBy(() -> service.start(byEmail("  ")))
                .isInstanceOf(ValidationException.class);
        nothingWasWritten();
    }

    @Test
    void start_aCallerWhoIsNotInTheDatabase_is404() {
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ResourceNotFoundException.class);
        nothingWasWritten();
    }

    @Test
    void start_aCallerWhoIsNoLongerAnActiveDirector_isForbidden() {
        User demoted = user("Rosa", "Mamani", "rosa@ue6.bo", TEACHER, true);
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.of(demoted));

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ForbiddenException.class);
        nothingWasWritten();
    }

    @Test
    void start_aDeactivatedCaller_isForbidden() {
        User closed = user("Rosa", "Mamani", "rosa@ue6.bo", DIRECTOR, false);
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ForbiddenException.class);
        nothingWasWritten();
    }

    @Test
    void start_whileAnotherHandoverIsPending_is409() {
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(handovers.findPending())
                .thenReturn(
                        Optional.of(
                                new DirectorHandover(
                                        UUID.randomUUID(),
                                        outgoing.getId(),
                                        UUID.randomUUID(),
                                        true,
                                        null,
                                        NOW,
                                        null,
                                        null)));

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ConflictException.class);
        nothingWasWritten();
    }

    @Test
    void start_handingOverToOneself_is409() {
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("rosa@ue6.bo")).thenReturn(Optional.of(outgoing));

        assertThatThrownBy(() -> service.start(byEmail("rosa@ue6.bo")))
                .isInstanceOf(ConflictException.class);
        nothingWasWritten();
    }

    @Test
    void start_toAUserWhoIsAlreadyDirector_is409() {
        User otherDirector = user("Pedro", "Lima", "pedro@ue6.bo", DIRECTOR, true);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("pedro@ue6.bo")).thenReturn(Optional.of(otherDirector));

        assertThatThrownBy(() -> service.start(byEmail("pedro@ue6.bo")))
                .isInstanceOf(ConflictException.class);
        nothingWasWritten();
    }

    @Test
    void start_toADeactivatedUser_is409() {
        User closed = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, false);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ConflictException.class);
        nothingWasWritten();
    }

    @Test
    void start_whenTheRowIsRefusedByTheIndex_noMailIsSent() {
        User teacher = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, true);
        callerIsAnActiveDirector();
        when(userDomain.findByEmail("ana@ue6.bo")).thenReturn(Optional.of(teacher));
        when(roleDomain.findByName("Director")).thenReturn(Optional.of(DIRECTOR));
        when(passwordGenerator.generate()).thenReturn("Tmp#Pass1");
        when(passwordEncoder.encode("Tmp#Pass1")).thenReturn("$hashed$");
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(handovers.save(any(DirectorHandover.class)))
                .thenThrow(new ConflictException("Ya hay un cambio de Director en curso"));

        assertThatThrownBy(() -> service.start(byEmail("ana@ue6.bo")))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(emailService);
    }

    // ---- findPending ------------------------------------------------------------------------

    @Test
    void findPending_withNoneInProgress_isEmpty() {
        when(handovers.findPending()).thenReturn(Optional.empty());

        assertThat(service.findPending()).isEmpty();
    }

    @Test
    void findPending_loadsBothAccounts() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        DirectorHandover row = pendingRow(incoming, true, null);
        when(handovers.findPending()).thenReturn(Optional.of(row));
        when(userDomain.findById(outgoing.getId())).thenReturn(Optional.of(outgoing));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));

        DirectorHandoverDetail detail = service.findPending().orElseThrow();

        assertThat(detail.id()).isEqualTo(row.id());
        assertThat(detail.outgoing()).isSameAs(outgoing);
        assertThat(detail.incoming()).isSameAs(incoming);
        assertThat(detail.incomingCreated()).isTrue();
        assertThat(detail.startedAt()).isEqualTo(NOW);
    }

    // ---- cancel -----------------------------------------------------------------------------

    private DirectorHandover pendingRow(User incoming, boolean created, Integer previousRole) {
        return new DirectorHandover(
                UUID.randomUUID(),
                outgoing.getId(),
                incoming.getId(),
                created,
                previousRole,
                NOW,
                null,
                null);
    }

    @Test
    void cancel_aCreatedIncoming_isDeactivated_andTheRowIsCancelled() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        DirectorHandover row = pendingRow(incoming, true, null);
        when(handovers.findPending()).thenReturn(Optional.of(row));

        service.cancel(outgoing.getId());

        verify(userDomain).deactivate(incoming.getId());
        verify(userDomain, never()).deactivate(outgoing.getId());
        verify(userDomain, never()).save(any());
        ArgumentCaptor<DirectorHandover> saved = ArgumentCaptor.forClass(DirectorHandover.class);
        verify(handovers).save(saved.capture());
        assertThat(saved.getValue().cancelledAt()).isEqualTo(NOW);
        assertThat(saved.getValue().closedAt()).isNull();
    }

    @Test
    void cancel_aPromotedIncoming_getsThePreviousRoleBack_andStaysActive() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        incoming.setMustChangePassword(true);
        DirectorHandover row = pendingRow(incoming, false, TEACHER.id());
        when(handovers.findPending()).thenReturn(Optional.of(row));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(roleDomain.findById(TEACHER.id())).thenReturn(Optional.of(TEACHER));
        when(userDomain.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(outgoing.getId());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userDomain).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(TEACHER);
        assertThat(saved.getValue().isActive()).isTrue();
        verify(userDomain, never()).deactivate(any());
        ArgumentCaptor<DirectorHandover> row2 = ArgumentCaptor.forClass(DirectorHandover.class);
        verify(handovers).save(row2.capture());
        assertThat(row2.getValue().cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cancel_withNothingPending_is404() {
        when(handovers.findPending()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(outgoing.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        nothingWasWritten();
    }

    @Test
    void cancel_byTheIncomingDirector_isForbidden_andNothingChanges() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        when(handovers.findPending()).thenReturn(Optional.of(pendingRow(incoming, true, null)));

        assertThatThrownBy(() -> service.cancel(incoming.getId()))
                .isInstanceOf(ForbiddenException.class);
        nothingWasWritten();
    }

    @Test
    void cancel_byAnyoneElse_isForbidden() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        when(handovers.findPending()).thenReturn(Optional.of(pendingRow(incoming, true, null)));

        assertThatThrownBy(() -> service.cancel(UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
        nothingWasWritten();
    }

    @Test
    void cancel_aPromotedRowWithoutAPreviousRole_failsLoudly_andChangesNothing() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        when(handovers.findPending()).thenReturn(Optional.of(pendingRow(incoming, false, null)));

        assertThatThrownBy(() -> service.cancel(outgoing.getId()))
                .isInstanceOf(IllegalStateException.class);
        nothingWasWritten();
    }

    @Test
    void cancel_aPromotedRowWhosePreviousRoleIsGone_is404_andChangesNothing() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        when(handovers.findPending()).thenReturn(Optional.of(pendingRow(incoming, false, 99)));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));
        when(roleDomain.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(outgoing.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        nothingWasWritten();
    }

    // ---- completeFor ------------------------------------------------------------------------

    @Test
    void completeFor_theIncomingDirector_closesTheRow_andDeactivatesTheOutgoing() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        DirectorHandover row = pendingRow(incoming, true, null);
        when(handovers.findPendingByIncoming(incoming.getId())).thenReturn(Optional.of(row));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));

        boolean closed = service.completeFor(incoming.getId());

        assertThat(closed).isTrue();
        verify(userDomain).deactivate(outgoing.getId());
        verify(userDomain, never()).deactivate(incoming.getId());
        ArgumentCaptor<DirectorHandover> saved = ArgumentCaptor.forClass(DirectorHandover.class);
        verify(handovers).save(saved.capture());
        assertThat(saved.getValue().closedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().cancelledAt()).isNull();
    }

    @Test
    void completeFor_theRowIsClosedBeforeTheOutgoingAccount() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, true);
        when(handovers.findPendingByIncoming(incoming.getId()))
                .thenReturn(Optional.of(pendingRow(incoming, true, null)));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));

        service.completeFor(incoming.getId());

        InOrder order = inOrder(handovers, userDomain);
        order.verify(handovers).save(any(DirectorHandover.class));
        order.verify(userDomain).deactivate(outgoing.getId());
    }

    @Test
    void completeFor_aUserWhoIsNotTheIncoming_doesNothing() {
        UUID someone = UUID.randomUUID();
        when(handovers.findPendingByIncoming(someone)).thenReturn(Optional.empty());

        assertThat(service.completeFor(someone)).isFalse();
        nothingWasWritten();
    }

    @Test
    void completeFor_anIncomingWhoIsNoLongerDirector_leavesTheHandoverPending() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", TEACHER, true);
        when(handovers.findPendingByIncoming(incoming.getId()))
                .thenReturn(Optional.of(pendingRow(incoming, false, TEACHER.id())));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));

        assertThat(service.completeFor(incoming.getId())).isFalse();
        nothingWasWritten();
    }

    @Test
    void completeFor_anIncomingWhoIsDeactivated_leavesTheHandoverPending() {
        User incoming = user("Ana", "Quispe", "ana@ue6.bo", DIRECTOR, false);
        when(handovers.findPendingByIncoming(incoming.getId()))
                .thenReturn(Optional.of(pendingRow(incoming, true, null)));
        when(userDomain.findById(incoming.getId())).thenReturn(Optional.of(incoming));

        assertThat(service.completeFor(incoming.getId())).isFalse();
        nothingWasWritten();
    }
}
