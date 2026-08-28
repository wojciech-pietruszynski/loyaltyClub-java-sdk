package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Pojedyncza pozycja paragonu przekazywana przy rejestracji sprzedazy lub zwrotu. */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreTransactionItem {

    /** Numer pozycji na paragonie, opcjonalny. */
    String cartPosition;

    /** Kod EAN towaru, wymagany. */
    String ean;

    /** Nazwa towaru, wymagana. */
    String name;

    /** Hierarchia towarowa, wymagana. */
    Hierarchy hierarchy;

    /** Cena pozycji, wymagana. */
    ItemPrice price;
}
