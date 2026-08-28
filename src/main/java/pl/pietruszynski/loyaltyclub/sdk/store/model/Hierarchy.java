package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Hierarchia towarowa pozycji paragonu. Backend dopasowuje po niej promocje sklepowe,
 * dlatego kod {@link #hierarchy} jest wymagany.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Hierarchy {

    /** Kod hierarchii, wymagany. */
    String hierarchy;

    /** Klasa towarowa, opcjonalna. */
    String productClass;

    /** Podklasa towarowa, opcjonalna. */
    String subclass;
}
