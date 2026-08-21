package com.clinicare.entity;

/**
 * The intended use of a {@link VerificationToken}. The table currently supports
 * email confirmation; the enum leaves room for other single-use token purposes
 * without changing the schema.
 */
public enum TokenPurpose {
    EMAIL_VERIFICATION
}
