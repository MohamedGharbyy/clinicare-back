package com.clinicare.exception;

/**
 * Thrown when a verification-code resend is requested before the configured
 * cooldown has elapsed. Mapped to HTTP 429 Too Many Requests; the remaining
 * wait is exposed via {@link #getRetryAfterSeconds()} so the client can show a
 * live countdown. Only applies to a genuine pending-verification account the
 * caller already knows about (the verification screen), so it does not aid
 * email enumeration.
 */
public class VerificationResendCooldownException extends RuntimeException {

    private final long retryAfterSeconds;

    public VerificationResendCooldownException(long retryAfterSeconds) {
        super("Please wait before requesting another verification code.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
