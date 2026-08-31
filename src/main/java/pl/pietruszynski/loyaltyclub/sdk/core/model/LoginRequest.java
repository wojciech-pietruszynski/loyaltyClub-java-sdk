package pl.pietruszynski.loyaltyclub.sdk.core.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Poswiadczenia wysylane do punktu logowania. Backend uzywa tego samego ksztaltu
 * dla wszystkich przestrzeni ({@code /api/admin/auth}, {@code /api/store/auth},
 * {@code /api/ecom/auth}), wiec SDK trzyma jeden model zamiast kopii na kanal.
 */
@Value
@Builder
@Jacksonized
public class LoginRequest {

    String username;
    String password;
}
