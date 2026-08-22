package com.clinicare.service;

import com.clinicare.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sends account lifecycle emails through {@link EmailService}, keeping email
 * construction out of {@link AdminService}.
 *
 * <p>Currently this covers the account deletion notification. Reliability rules:
 * <ul>
 *   <li>The recipient address and first name are <strong>snapshotted</strong> when
 *       the notification is requested, so the email never depends on the deleted
 *       account still being readable from the database.</li>
 *   <li>Delivery is deferred until the surrounding transaction
 *       <strong>commits</strong>. A deletion that fails or is rolled back therefore
 *       never produces an email. When no transaction is active the email is sent
 *       immediately (the deletion has already been applied by the caller).</li>
 *   <li>Each successful deletion notifies exactly once. Re-deleting an account is
 *       rejected upstream by {@code AdminService}, so no duplicate email is sent.</li>
 *   <li>An email failure is caught and logged; it never propagates, so a failed
 *       email can never roll back or undo the account deletion.</li>
 * </ul>
 */
@Service
public class AccountNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AccountNotificationService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");
    private static final String DELETED_SUBJECT = "Your CliniCare account has been deleted";
    private static final String DISABLED_SUBJECT = "Your CliniCare account has been disabled";
    private static final String ENABLED_SUBJECT = "Your CliniCare account has been re-enabled";
    private static final String BANNED_SUBJECT = "Your CliniCare account has been temporarily banned";

    private final EmailService emailService;

    public AccountNotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Notifies the address that was associated with the account that the
     * CliniCare account has been deleted. Call this only after the deletion has
     * been applied; the email is released once the transaction commits.
     *
     * @param deletedUser the account as it was just before/while being deleted
     * @param deletedAt   when the deletion happened
     */
    public void notifyAccountDeleted(User deletedUser, LocalDateTime deletedAt) {
        if (deletedUser == null) {
            return;
        }
        // Snapshot everything the email needs: the entity/DB row is not consulted again.
        String recipient = deletedUser.getEmail();
        String firstName = deletedUser.getFirstName();
        LocalDateTime when = deletedAt != null ? deletedAt : LocalDateTime.now();

        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipped account deletion email: the deleted account has no email address (user id={})",
                    deletedUser.getId());
            return;
        }
        afterCommit(() -> sendAccountDeleted(recipient, firstName, when));
    }

    /**
     * Notifies the user that their account has been disabled by an administrator.
     * The email is released once the surrounding transaction commits.
     */
    public void notifyAccountDisabled(User user, LocalDateTime when) {
        if (user == null) {
            return;
        }
        String recipient = user.getEmail();
        String firstName = user.getFirstName();
        LocalDateTime timestamp = when != null ? when : LocalDateTime.now();

        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipped account disabled email: the account has no email address (user id={})",
                    user.getId());
            return;
        }
        afterCommit(() -> sendAccountDisabled(recipient, firstName, timestamp));
    }

    /**
     * Notifies the user that their account has been re-enabled by an administrator.
     * The email is released once the surrounding transaction commits.
     */
    public void notifyAccountEnabled(User user, LocalDateTime when) {
        if (user == null) {
            return;
        }
        String recipient = user.getEmail();
        String firstName = user.getFirstName();
        LocalDateTime timestamp = when != null ? when : LocalDateTime.now();

        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipped account enabled email: the account has no email address (user id={})",
                    user.getId());
            return;
        }
        afterCommit(() -> sendAccountEnabled(recipient, firstName, timestamp));
    }

    /**
     * Notifies the user that their account has been temporarily banned by an
     * administrator. The email is released once the surrounding transaction commits.
     */
    public void notifyAccountBanned(User user, LocalDateTime banExpiresAt) {
        if (user == null) {
            return;
        }
        String recipient = user.getEmail();
        String firstName = user.getFirstName();

        if (recipient == null || recipient.isBlank()) {
            log.warn("Skipped account banned email: the account has no email address (user id={})",
                    user.getId());
            return;
        }
        afterCommit(() -> sendAccountBanned(recipient, firstName, banExpiresAt));
    }

    private void sendAccountDeleted(String recipient, String firstName, LocalDateTime deletedAt) {
        try {
            emailService.sendHtmlMessage(recipient, DELETED_SUBJECT,
                    buildAccountDeletedHtml(recipient, firstName, deletedAt));
        } catch (Exception ex) {
            // Log safely: never include credentials or the email body.
            log.error("Failed to send account deletion email to {}: {}", recipient, ex.getMessage());
        }
    }

    private void sendAccountDisabled(String recipient, String firstName, LocalDateTime when) {
        try {
            emailService.sendHtmlMessage(recipient, DISABLED_SUBJECT,
                    buildAccountDisabledHtml(recipient, firstName, when));
        } catch (Exception ex) {
            log.error("Failed to send account disabled email to {}: {}", recipient, ex.getMessage());
        }
    }

    private void sendAccountEnabled(String recipient, String firstName, LocalDateTime when) {
        try {
            emailService.sendHtmlMessage(recipient, ENABLED_SUBJECT,
                    buildAccountEnabledHtml(recipient, firstName, when));
        } catch (Exception ex) {
            log.error("Failed to send account enabled email to {}: {}", recipient, ex.getMessage());
        }
    }

    private void sendAccountBanned(String recipient, String firstName, LocalDateTime banExpiresAt) {
        try {
            emailService.sendHtmlMessage(recipient, BANNED_SUBJECT,
                    buildAccountBannedHtml(recipient, firstName, banExpiresAt));
        } catch (Exception ex) {
            log.error("Failed to send account banned email to {}: {}", recipient, ex.getMessage());
        }
    }

    private String buildAccountDeletedHtml(String recipient, String firstName, LocalDateTime deletedAt) {
        String greeting = firstName == null || firstName.isBlank() ? "there" : firstName;
        return EmailTemplate.titled("Your CliniCare account has been deleted")
                .message("Hello " + greeting + ", your CliniCare account has been deleted by a CliniCare "
                        + "administrator. You can no longer sign in to CliniCare with this email address.")
                .detail("Account", recipient)
                .detail("Deleted on", deletedAt.format(DATE_FMT))
                .action("Your past medical records are kept securely by the clinic. If you believe this was a "
                        + "mistake, please contact CliniCare Support. To use CliniCare again you will need to "
                        + "register a new account.")
                .render();
    }

    private String buildAccountDisabledHtml(String recipient, String firstName, LocalDateTime when) {
        String greeting = firstName == null || firstName.isBlank() ? "there" : firstName;
        return EmailTemplate.titled("Your CliniCare account has been disabled")
                .message("Hello " + greeting + ", your CliniCare account has been disabled by a CliniCare "
                        + "administrator. You will not be able to sign in until an administrator re-enables it.")
                .detail("Account", recipient)
                .detail("Disabled on", when.format(DATE_FMT))
                .action("If you believe this was a mistake, please contact CliniCare Support.")
                .render();
    }

    private String buildAccountEnabledHtml(String recipient, String firstName, LocalDateTime when) {
        String greeting = firstName == null || firstName.isBlank() ? "there" : firstName;
        return EmailTemplate.titled("Your CliniCare account has been re-enabled")
                .message("Hello " + greeting + ", your CliniCare account has been re-enabled by a CliniCare "
                        + "administrator. You can now sign in to CliniCare again.")
                .detail("Account", recipient)
                .detail("Re-enabled on", when.format(DATE_FMT))
                .action("If you have any questions, please contact CliniCare Support.")
                .render();
    }

    private String buildAccountBannedHtml(String recipient, String firstName, LocalDateTime banExpiresAt) {
        String greeting = firstName == null || firstName.isBlank() ? "there" : firstName;
        String expiry = banExpiresAt != null ? banExpiresAt.format(DATE_FMT) : "a date to be determined by the clinic";
        return EmailTemplate.titled("Your CliniCare account has been temporarily banned")
                .message("Hello " + greeting + ", your CliniCare account has been temporarily banned by a "
                        + "CliniCare administrator. You will not be able to sign in while the ban is active.")
                .detail("Account", recipient)
                .detail("Ban expires on", expiry)
                .action("If you believe this was a mistake, please contact CliniCare Support. You will be able to "
                        + "sign in again once the ban period ends.")
                .render();
    }

    /**
     * Runs the action after the current transaction commits, or immediately when
     * the caller is not transactional.
     */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
