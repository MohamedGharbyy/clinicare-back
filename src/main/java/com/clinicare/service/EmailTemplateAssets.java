package com.clinicare.service;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Loads CliniCare brand assets for use inside HTML emails.
 *
 * <p>Assets are embedded as self-contained {@code data:} URIs so that email
 * recipients can render them without any dependency on the local application,
 * the Angular dev server, or an external host. This keeps the emails working
 * outside the development environment.</p>
 */
public final class EmailTemplateAssets {

    private static final String LOGO_DATA_URI = loadLogoDataUri();

    private EmailTemplateAssets() {
    }

    /** Returns a {@code data:image/png;base64,...} URI for the CliniCare logo. */
    public static String logoDataUri() {
        return LOGO_DATA_URI;
    }

    private static String loadLogoDataUri() {
        try {
            ClassPathResource resource = new ClassPathResource("email/clinicare-logo.png");
            try (InputStream in = resource.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                String encoded = Base64.getEncoder().encodeToString(bytes);
                return "data:image/png;base64," + encoded;
            }
        } catch (IOException ex) {
            return "";
        }
    }
}
