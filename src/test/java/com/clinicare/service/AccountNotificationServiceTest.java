package com.clinicare.service;

import com.clinicare.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Verifies the delivery guarantees of the account deletion email:
 * <ul>
 *   <li>it is released only after the deletion transaction commits;</li>
 *   <li>a rolled-back deletion sends nothing;</li>
 *   <li>the recipient is snapshotted, so the email does not depend on the
 *       deleted account still being readable.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AccountNotificationServiceTest {

    @Mock private EmailService emailService;

    @Captor private ArgumentCaptor<String> toCaptor;
    @Captor private ArgumentCaptor<String> subjectCaptor;
    @Captor private ArgumentCaptor<String> htmlCaptor;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private User account() {
        User user = new User();
        user.setId(7L);
        user.setEmail("deleted.user@example.com");
        user.setFirstName("Jane");
        user.setLastName("Doe");
        return user;
    }

    private List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }

    @Test
    void withinTransaction_emailIsSentOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AccountNotificationService service = new AccountNotificationService(emailService);

        service.notifyAccountDeleted(account(), LocalDateTime.of(2026, 3, 4, 10, 0));

        // Nothing is sent while the deletion is still in flight.
        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());

        synchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("deleted.user@example.com");
        assertThat(htmlCaptor.getValue()).contains("Your CliniCare account has been deleted")
                .contains("4 March 2026");
    }

    @Test
    void rolledBackTransaction_sendsNoEmail() {
        TransactionSynchronizationManager.initSynchronization();
        AccountNotificationService service = new AccountNotificationService(emailService);

        service.notifyAccountDeleted(account(), LocalDateTime.now());

        // The deletion failed: only afterCompletion runs, never afterCommit.
        synchronizations().forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void recipientIsSnapshotted_soTheDeletedAccountRowIsNotNeeded() {
        TransactionSynchronizationManager.initSynchronization();
        AccountNotificationService service = new AccountNotificationService(emailService);
        User user = account();

        service.notifyAccountDeleted(user, LocalDateTime.now());

        // Simulate the account no longer being readable after the deletion.
        user.setEmail(null);
        user.setFirstName(null);
        synchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(emailService).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("deleted.user@example.com");
        assertThat(htmlCaptor.getValue()).contains("Hello Jane");
    }

    @Test
    void withoutTransaction_emailIsSentImmediately() {
        new AccountNotificationService(emailService).notifyAccountDeleted(account(), LocalDateTime.now());

        verify(emailService, times(1)).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void accountWithoutEmailAddress_sendsNothing() {
        User user = account();
        user.setEmail(null);

        new AccountNotificationService(emailService).notifyAccountDeleted(user, LocalDateTime.now());

        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void emailFailureIsSwallowed() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> new AccountNotificationService(emailService)
                .notifyAccountDeleted(account(), LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    // ---------------------------------------------------------------------------
    // Account disabled / enabled / banned notifications
    // ---------------------------------------------------------------------------

    @Test
    void notifyAccountDisabled_sendsEmailWithCorrectSubject() {
        User user = account();
        new AccountNotificationService(emailService).notifyAccountDisabled(user, LocalDateTime.of(2026, 4, 1, 9, 0));

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("deleted.user@example.com");
        assertThat(htmlCaptor.getValue()).contains("Your CliniCare account has been disabled")
                .contains("Hello Jane")
                .contains("1 April 2026");
    }

    @Test
    void notifyAccountEnabled_sendsEmailWithCorrectSubject() {
        User user = account();
        new AccountNotificationService(emailService).notifyAccountEnabled(user, LocalDateTime.of(2026, 4, 2, 9, 0));

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("deleted.user@example.com");
        assertThat(htmlCaptor.getValue()).contains("Your CliniCare account has been re-enabled")
                .contains("Hello Jane")
                .contains("2 April 2026");
    }

    @Test
    void notifyAccountBanned_sendsEmailWithCorrectSubject() {
        User user = account();
        new AccountNotificationService(emailService)
                .notifyAccountBanned(user, LocalDateTime.of(2026, 4, 3, 9, 0));

        verify(emailService, times(1)).sendHtmlMessage(toCaptor.capture(), anyString(), htmlCaptor.capture());
        assertThat(toCaptor.getValue()).isEqualTo("deleted.user@example.com");
        assertThat(htmlCaptor.getValue()).contains("Your CliniCare account has been temporarily banned")
                .contains("Hello Jane")
                .contains("3 April 2026");
    }

    @Test
    void notifyAccountDisabled_withoutEmailAddress_sendsNothing() {
        User user = account();
        user.setEmail(null);

        new AccountNotificationService(emailService).notifyAccountDisabled(user, LocalDateTime.now());

        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void notifyAccountEnabled_withoutEmailAddress_sendsNothing() {
        User user = account();
        user.setEmail(null);

        new AccountNotificationService(emailService).notifyAccountEnabled(user, LocalDateTime.now());

        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void notifyAccountBanned_withoutEmailAddress_sendsNothing() {
        User user = account();
        user.setEmail(null);

        new AccountNotificationService(emailService).notifyAccountBanned(user, LocalDateTime.now());

        verify(emailService, never()).sendHtmlMessage(anyString(), anyString(), anyString());
    }

    @Test
    void notifyAccountDisabled_emailFailureIsSwallowed() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> new AccountNotificationService(emailService)
                .notifyAccountDisabled(account(), LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyAccountEnabled_emailFailureIsSwallowed() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> new AccountNotificationService(emailService)
                .notifyAccountEnabled(account(), LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    @Test
    void notifyAccountBanned_emailFailureIsSwallowed() {
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(emailService).sendHtmlMessage(anyString(), anyString(), anyString());

        assertThatCode(() -> new AccountNotificationService(emailService)
                .notifyAccountBanned(account(), LocalDateTime.now()))
                .doesNotThrowAnyException();
    }
}
