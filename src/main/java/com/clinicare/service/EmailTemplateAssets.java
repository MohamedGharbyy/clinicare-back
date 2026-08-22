package com.clinicare.service;

import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the CliniCare logo for use inside HTML emails.
 *
 * <p>The logo is delivered as an <strong>inline (CID) image part</strong>: the
 * email HTML only contains {@code <img src="cid:clinicare-logo">} and the bytes
 * travel in a separate MIME part attached by {@link EmailService}. This is the
 * email-compatible approach:
 * <ul>
 *   <li>No Angular {@code /assets/...} path, no filesystem path, no relative URL
 *       and no external host, so the logo also renders outside the application.</li>
 *   <li>Gmail does not render {@code data:} URI images and counts them towards
 *       the ~102&nbsp;KB clipping limit. The brand asset is 1888&times;1888&nbsp;px
 *       (about 166&nbsp;KB, roughly 222&nbsp;KB once base64-encoded), which both
 *       broke the logo and clipped every email.</li>
 *   <li>The source asset is re-encoded once at start-up to a small header-sized
 *       bitmap (twice the display size, so it stays sharp on HiDPI screens) while
 *       preserving its original aspect ratio.</li>
 * </ul>
 *
 * <p>If the asset cannot be read the accessors report "no logo" and the email
 * template falls back to a plain-text CliniCare wordmark.
 */
public final class EmailTemplateAssets {

    /** Content-ID of the inline logo part referenced by the email HTML. */
    public static final String LOGO_CONTENT_ID = "clinicare-logo";
    /** MIME type of the inline logo part. */
    public static final String LOGO_CONTENT_TYPE = "image/png";
    /** File name reported for the inline logo part. */
    public static final String LOGO_FILE_NAME = "clinicare-logo.png";

    /** Longest edge of the logo as displayed in the email header, in pixels. */
    static final int LOGO_DISPLAY_MAX_PX = 120;
    /** Longest edge of the embedded bitmap: 2x the display size for HiDPI screens. */
    static final int LOGO_EMBEDDED_MAX_PX = LOGO_DISPLAY_MAX_PX * 2;

    private static final String LOGO_RESOURCE = "email/clinicare-logo.png";
    private static final Logo LOGO = loadLogo();

    private EmailTemplateAssets() {
    }

    /** True when the inline logo is available. */
    public static boolean hasLogo() {
        return LOGO.bytes().length > 0;
    }

    /** PNG bytes of the header-sized logo, attached as the inline CID part. */
    public static byte[] logoBytes() {
        return LOGO.bytes().clone();
    }

    /** Width the logo is rendered at in the email header, in pixels. */
    static int logoDisplayWidthPx() {
        return LOGO.displayWidth();
    }

    /** Height the logo is rendered at in the email header, in pixels. */
    static int logoDisplayHeightPx() {
        return LOGO.displayHeight();
    }

    private record Logo(byte[] bytes, int displayWidth, int displayHeight) {
        static Logo none() {
            return new Logo(new byte[0], 0, 0);
        }
    }

    private static Logo loadLogo() {
        try {
            ClassPathResource resource = new ClassPathResource(LOGO_RESOURCE);
            try (InputStream in = resource.getInputStream()) {
                BufferedImage source = ImageIO.read(in);
                if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
                    return Logo.none();
                }
                BufferedImage embedded = resizeToLongestEdge(source, LOGO_EMBEDDED_MAX_PX);
                byte[] png = toPngBytes(embedded);
                if (png.length == 0) {
                    return Logo.none();
                }
                int[] display = fitToLongestEdge(source.getWidth(), source.getHeight(), LOGO_DISPLAY_MAX_PX);
                return new Logo(png, display[0], display[1]);
            }
        } catch (IOException | RuntimeException ex) {
            // The logo is decorative: never let a missing/unreadable asset break email sending.
            return Logo.none();
        }
    }

    /** Scales down to the given longest edge, keeping the original aspect ratio. */
    private static BufferedImage resizeToLongestEdge(BufferedImage source, int longestEdge) {
        int[] target = fitToLongestEdge(source.getWidth(), source.getHeight(), longestEdge);
        BufferedImage current = source;
        // Halve progressively first: a single large down-scale step looks aliased.
        while (current.getWidth() / 2 >= target[0] && current.getHeight() / 2 >= target[1]
                && current.getWidth() / 2 > 0 && current.getHeight() / 2 > 0) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        if (current.getWidth() == target[0] && current.getHeight() == target[1]) {
            return current;
        }
        return draw(current, target[0], target[1]);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Returns {@code [width, height]} scaled so the longest edge fits, ratio preserved. */
    private static int[] fitToLongestEdge(int width, int height, int longestEdge) {
        int longest = Math.max(width, height);
        if (longest <= longestEdge) {
            return new int[]{width, height};
        }
        double factor = (double) longestEdge / longest;
        return new int[]{
                Math.max(1, (int) Math.round(width * factor)),
                Math.max(1, (int) Math.round(height * factor))
        };
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            return new byte[0];
        }
        return out.toByteArray();
    }
}
