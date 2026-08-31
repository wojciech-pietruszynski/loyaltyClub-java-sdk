package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import pl.pietruszynski.loyaltyclub.sdk.core.model.CustomerStatus;

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

    /** Biezacy stan punktow na koncie klienta — to, czym klient moze dysponowac. */
    Integer loyaltyPoints;

    /**
     * Dorobek punktowy, czyli podstawa progu lojalnosciowego. Rosnie ze sprzedaza
     * i premiami za polecenia; wymiana punktow na kupon go nie obniza.
     */
    Integer lifetimePoints;

    /** Kod progu lojalnosciowego, np. {@code SILVER}. */
    String loyaltyTierCode;

    /** Kod polecajacy klienta. */
    String referralCode;

    /**
     * Stan konta. Konto inne niz {@link CustomerStatus#ACTIVE} nie bierze udzialu
     * w operacjach punktowych — backend odrzuci wtedy sprzedaz i wymiane punktow.
     */
    CustomerStatus status;
}
