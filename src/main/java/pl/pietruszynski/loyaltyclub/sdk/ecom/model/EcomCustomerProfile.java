package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Profil lojalnosciowy klienta widziany przez sklep internetowy. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcomCustomerProfile {

    Long customerId;
    String customerNumber;
    String firstName;
    String lastName;
    String email;
    String phoneNumber;
    String country;

    /** Biezacy stan punktow na koncie klienta. */
    Integer loyaltyPoints;

    /** Kod progu lojalnosciowego, np. {@code SILVER}. */
    String loyaltyTierCode;

    /** Kod polecajacy klienta. */
    String referralCode;
}
