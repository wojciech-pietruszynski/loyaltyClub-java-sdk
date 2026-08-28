package pl.pietruszynski.loyaltyclub.sdk.core.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Pomocnik do skladania sciezek URI. */
public final class Uris {

    private Uris() {
    }

    /**
     * Koduje pojedynczy segment sciezki. Numer klienta czy kod kuponu moga zawierac znaki
     * wymagajace escapowania, a wklejone surowo rozjechalyby routing po stronie backendu.
     */
    public static String encodePathSegment(String segment) {
        return URLEncoder.encode(segment == null ? "" : segment, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
