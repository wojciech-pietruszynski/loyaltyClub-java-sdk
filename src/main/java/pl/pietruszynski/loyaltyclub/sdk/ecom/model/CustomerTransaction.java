package pl.pietruszynski.loyaltyclub.sdk.ecom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionState;
import pl.pietruszynski.loyaltyclub.sdk.core.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Pozycja historii punktowej klienta.
 *
 * <p>Rodzaj i stan operacji sa wypelniane przy odczycie, wiec integracja nie musi
 * wnioskowac ich z pola {@link #description}.
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerTransaction {

    Long id;

    /** Punkty naliczone (dodatnie) lub wycofane (ujemne). */
    Integer points;

    String description;

    LocalDateTime timestamp;

    /** Rodzaj operacji: sprzedaz, zwrot, korekta, premia za polecenie, operacja kuponowa. */
    TransactionType type;

    /** Stan naliczenia: oczekujace, dostepne albo wygasle. */
    TransactionState state;

    /** Kwota operacji handlowej; dla operacji kuponowych i korekt recznych zero. */
    BigDecimal amount;

    /** Moment, od ktorego punkty sa dostepne do wykorzystania. */
    LocalDateTime availableFrom;

    /** Moment wygasniecia punktow; {@code null} dla operacji bez daty waznosci. */
    LocalDateTime expiresAt;
}
