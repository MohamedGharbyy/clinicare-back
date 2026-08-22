package com.clinicare.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the single, shared CliniCare HTML email layout used by every
 * transactional email (verification, appointment, cancellation, ban and account
 * deletion notifications).
 *
 * <p>The layout is deliberately small and Gmail-safe:
 * <ul>
 *   <li><strong>Structure:</strong> header (inline CliniCare logo) &rarr;
 *       notification title &rarr; short message &rarr; relevant information
 *       &rarr; optional action/instruction &rarr; footer. The important
 *       information is always at the top of the message.</li>
 *   <li><strong>Size:</strong> the rendered document stays a few kilobytes.
 *       Gmail clips messages whose HTML part is larger than ~102&nbsp;KB, so the
 *       logo is referenced as an inline {@code cid:} image (a separate MIME part)
 *       instead of being inlined as a huge base64 {@code data:} URI.</li>
 *   <li><strong>Compatibility:</strong> table-based layout, inline styles only,
 *       no JavaScript, no external stylesheet, no {@code <style>} block, no
 *       Angular {@code /assets/...} paths, no filesystem or relative paths, and
 *       no CSS that Gmail strips (such as {@code object-fit}).</li>
 *   <li><strong>Responsiveness:</strong> a fluid {@code width:100%} card capped
 *       at {@code max-width:600px} plus a viewport meta tag, so the email stays
 *       readable on desktop and on mobile-sized views without media queries.</li>
 * </ul>
 *
 * <p>All caller-supplied text is plain text and is HTML-escaped here, which keeps
 * the generated markup valid and prevents content from breaking the layout.
 */
public final class EmailTemplate {

    /** CliniCare primary brand blue (--clini-blue-600 in the Angular theme). */
    static final String PRIMARY_COLOR = "#38B6FF";
    /** Maximum rendered width of the email card, in pixels. */
    static final int MAX_WIDTH_PX = 600;

    private static final String HEADING_COLOR = "#0f172a";
    private static final String TEXT_COLOR = "#334155";
    private static final String MUTED_COLOR = "#64748b";
    private static final String BORDER_COLOR = "#e2e8f0";
    private static final String SURFACE_COLOR = "#f8fafc";
    private static final String PAGE_COLOR = "#f4f6f9";
    private static final String FONT_STACK = "Arial,Helvetica,sans-serif";

    private EmailTemplate() {
    }

    /** Starts a new email with the given notification title. */
    public static Builder titled(String title) {
        return new Builder(title);
    }

    /** Fluent builder for the shared layout. All text arguments are plain text. */
    public static final class Builder {

        private final String title;
        private String message;
        private String code;
        private final List<String[]> details = new ArrayList<>();
        private List<String> tableHeaders = List.of();
        private final List<List<String>> tableRows = new ArrayList<>();
        private String action;

        private Builder(String title) {
            this.title = title;
        }

        /** Short introductory sentence shown directly under the title. */
        public Builder message(String text) {
            this.message = text;
            return this;
        }

        /** Highlighted single value, used for the email verification code. */
        public Builder code(String value) {
            this.code = value;
            return this;
        }

        /** Adds one label/value row to the "relevant information" block. */
        public Builder detail(String label, String value) {
            if (value != null && !value.isBlank()) {
                details.add(new String[]{label, value});
            }
            return this;
        }

        /** Adds a compact summary table (used when several items are affected). */
        public Builder table(List<String> headers, List<List<String>> rows) {
            this.tableHeaders = headers == null ? List.of() : headers;
            this.tableRows.clear();
            if (rows != null) {
                this.tableRows.addAll(rows);
            }
            return this;
        }

        /** Optional closing action or instruction. */
        public Builder action(String text) {
            this.action = text;
            return this;
        }

        /** Renders the complete, self-contained HTML document. */
        public String render() {
            StringBuilder body = new StringBuilder(768);
            body.append("<h1 style=\"margin:0 0 12px;font-family:").append(FONT_STACK)
                    .append(";font-size:20px;line-height:1.3;font-weight:bold;color:").append(HEADING_COLOR)
                    .append(";\">").append(escapeHtml(title)).append("</h1>");

            if (isPresent(message)) {
                body.append(paragraph(message, TEXT_COLOR, "15px"));
            }
            if (isPresent(code)) {
                body.append(codeBlock(code));
            }
            if (!details.isEmpty()) {
                body.append(detailsTable());
            }
            if (!tableRows.isEmpty()) {
                body.append(summaryTable());
            }
            if (isPresent(action)) {
                body.append("<p style=\"margin:16px 0 0;font-family:").append(FONT_STACK)
                        .append(";font-size:14px;line-height:1.5;color:").append(MUTED_COLOR).append(";\">")
                        .append(escapeHtml(action)).append("</p>");
            }
            return document(escapeHtml(title), body.toString());
        }

        private String detailsTable() {
            StringBuilder sb = new StringBuilder(256);
            sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                    .append(" style=\"width:100%;border:1px solid ").append(BORDER_COLOR)
                    .append(";border-radius:6px;font-family:").append(FONT_STACK).append(";\">");
            for (int i = 0; i < details.size(); i++) {
                String separator = i == 0 ? "" : "border-top:1px solid " + BORDER_COLOR + ";";
                sb.append("<tr><td style=\"padding:8px 12px;").append(separator)
                        .append("font-size:13px;color:").append(MUTED_COLOR).append(";\">")
                        .append(escapeHtml(details.get(i)[0])).append("</td>")
                        .append("<td style=\"padding:8px 12px;").append(separator)
                        .append("font-size:14px;font-weight:bold;color:").append(HEADING_COLOR).append(";\">")
                        .append(escapeHtml(details.get(i)[1])).append("</td></tr>");
            }
            return sb.append("</table>").toString();
        }

        private String summaryTable() {
            StringBuilder sb = new StringBuilder(320);
            sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                    .append(" style=\"width:100%;border:1px solid ").append(BORDER_COLOR)
                    .append(";border-radius:6px;font-family:").append(FONT_STACK).append(";\">");
            if (!tableHeaders.isEmpty()) {
                sb.append("<tr>");
                for (String header : tableHeaders) {
                    sb.append("<th align=\"left\" style=\"padding:8px 12px;font-size:13px;font-weight:bold;color:")
                            .append(MUTED_COLOR).append(";\">").append(escapeHtml(header)).append("</th>");
                }
                sb.append("</tr>");
            }
            for (List<String> row : tableRows) {
                sb.append("<tr>");
                for (String cell : row) {
                    sb.append("<td style=\"padding:8px 12px;border-top:1px solid ").append(BORDER_COLOR)
                            .append(";font-size:14px;color:").append(HEADING_COLOR).append(";\">")
                            .append(escapeHtml(cell)).append("</td>");
                }
                sb.append("</tr>");
            }
            return sb.append("</table>").toString();
        }

        private String codeBlock(String value) {
            return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                    + " style=\"margin:0 0 16px;\"><tr><td align=\"center\""
                    + " style=\"background-color:#eaf7ff;border:1px solid #b8e6ff;border-radius:8px;"
                    + "padding:14px 24px;font-family:" + FONT_STACK + ";font-size:30px;font-weight:bold;"
                    + "letter-spacing:6px;color:" + HEADING_COLOR + ";\">"
                    + escapeHtml(value) + "</td></tr></table>";
        }
    }

    private static String paragraph(String text, String color, String fontSize) {
        return "<p style=\"margin:0 0 16px;font-family:" + FONT_STACK + ";font-size:" + fontSize
                + ";line-height:1.5;color:" + color + ";\">" + escapeHtml(text) + "</p>";
    }
    /**
     * Wraps the content in the branded shell: a centred, fluid card with the
     * CliniCare logo in the header and a short footer.
     */
    private static String document(String escapedTitle, String bodyHtml) {
        return "<!DOCTYPE html>"
                + "<html lang=\"en\"><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>"
                + "<title>" + escapedTitle + "</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:" + PAGE_COLOR + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                + " style=\"background-color:" + PAGE_COLOR + ";\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"" + MAX_WIDTH_PX + "\" cellpadding=\"0\" cellspacing=\"0\""
                + " border=\"0\" style=\"width:100%;max-width:" + MAX_WIDTH_PX + "px;background-color:#ffffff;"
                + "border:1px solid " + BORDER_COLOR + ";border-radius:8px;\">"
                + "<tr><td align=\"center\" style=\"padding:20px 24px 16px;border-bottom:3px solid "
                + PRIMARY_COLOR + ";\">" + logoHtml() + "</td></tr>"
                + "<tr><td style=\"padding:24px;\">" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:14px 24px;background-color:" + SURFACE_COLOR
                + ";border-top:1px solid " + BORDER_COLOR + ";font-family:" + FONT_STACK
                + ";font-size:12px;line-height:1.5;color:" + MUTED_COLOR + ";\">"
                + "CliniCare Support &mdash; automated message, please do not reply."
                + "</td></tr></table></td></tr></table></body></html>";
    }

    /**
     * The header logo. The image itself travels as an inline (CID) MIME part
     * attached by {@link EmailService}, which keeps the HTML tiny and renders in
     * Gmail. Explicit equal width/height attributes preserve the square aspect
     * ratio of the CliniCare asset and stop clients from scaling it up.
     */
    static String logoHtml() {
        if (!EmailTemplateAssets.hasLogo()) {
            // Text wordmark fallback if the asset cannot be loaded.
            return "<span style=\"font-family:" + FONT_STACK + ";font-size:22px;font-weight:bold;color:"
                    + PRIMARY_COLOR + ";\">CliniCare</span>";
        }
        int width = EmailTemplateAssets.logoDisplayWidthPx();
        int height = EmailTemplateAssets.logoDisplayHeightPx();
        return "<img src=\"cid:" + EmailTemplateAssets.LOGO_CONTENT_ID + "\" alt=\"CliniCare\""
                + " width=\"" + width + "\" height=\"" + height + "\""
                + " style=\"display:block;margin:0 auto;width:" + width + "px;height:" + height
                + "px;border:0;outline:none;text-decoration:none;\"/>";
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
