package pl.pietruszynski.loyaltyclub.sdk.store.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Rejestracja zwrotu — wycofanie punktow naliczonych wczesniejsza sprzedaza.
 *
 * <p>Zwrot musi wskazywac numer transakcji sprzedazy przez {@link #saleTransactionNumber}.
 * Backend odrzuci zadanie, gdy kraj zwrotu nie zgadza sie z krajem sprzedazy, punkty juz
 * wygasly albo kwota zwrotu przekracza pozostala wartosc sprzedazy.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreReturnRequest {

    /** Numer klienta w programie lojalnosciowym, wymagany. */
    String customerNumber;

    /** Zwracane pozycje; lista nie moze byc pusta. */
    @Singular
    List<StoreTransactionItem> items;

    /** Wartosc zwrotu; musi byc dodatnia i rowna sumie cen pozycji. */
    BigDecimal totalAmount;

    /** Numer transakcji zwrotu w systemie zrodlowym; wymagany i unikalny. */
    String sourceTransactionNumber;

    /** Numer pierwotnej transakcji sprzedazy, ktorej dotyczy zwrot; wymagany. */
    String saleTransactionNumber;

    /** Moment zwrotu; gdy pominiety, backend przyjmuje czas biezacy. */
    LocalDateTime purchaseTimestamp;
}
