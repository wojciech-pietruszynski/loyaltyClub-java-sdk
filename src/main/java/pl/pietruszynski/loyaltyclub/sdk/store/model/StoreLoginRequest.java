package pl.pietruszynski.loyaltyclub.sdk.store.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Poswiadczenia uzytkownika sklepu wysylane do {@code POST /api/store/auth/login}. */
@Value
@Builder
@Jacksonized
public class StoreLoginRequest {

    String username;
    String password;
}
