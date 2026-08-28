package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Metadane integracyjne zwracane przez {@code GET /api/store} i {@code GET /api/ecom}.
 * Sluza jako lekki health-check poswiadczen: odpowiedz 200 oznacza, ze konto ma
 * wlasciwa role i dostep do namespace'u.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceInfo {

    String name;
    String status;

    /** Wersja API; wypelniana przez {@code /api/ecom}, dla {@code /api/store} pozostaje {@code null}. */
    String apiVersion;

    /** Krotka podpowiedz nawigacyjna od backendu; tylko {@code /api/ecom}. */
    String docs;
}
