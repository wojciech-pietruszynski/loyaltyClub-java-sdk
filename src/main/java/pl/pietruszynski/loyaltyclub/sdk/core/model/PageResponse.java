package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * Koperta stronicowanej odpowiedzi zwracana przez warianty {@code .../paged}.
 *
 * <p>Backend celowo nie serializuje {@code org.springframework.data.domain.Page},
 * tylko wlasny rekord o stabilnym ksztalcie — ta klasa jest jego odpowiednikiem
 * po stronie klienta.
 *
 * @param <T> typ elementu strony
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageResponse<T> {

    /** Elementy biezacej strony; nigdy nie jest {@code null} po deserializacji poprawnej odpowiedzi. */
    List<T> content;

    /** Numer strony liczony od zera. */
    int page;

    /** Zadany rozmiar strony. */
    int size;

    /** Laczna liczba elementow w calej kolekcji. */
    long totalElements;

    /** Laczna liczba stron. */
    int totalPages;

    boolean first;
    boolean last;

    /** Elementy strony, z pusta lista zamiast {@code null}. */
    public List<T> contentOrEmpty() {
        return content == null ? List.of() : content;
    }

    /** Czy istnieje kolejna strona — skrot na {@code !last}. */
    public boolean hasNext() {
        return !last;
    }

    /** Numer kolejnej strony albo {@code -1}, gdy biezaca jest ostatnia. */
    public int nextPage() {
        return last ? -1 : page + 1;
    }
}
