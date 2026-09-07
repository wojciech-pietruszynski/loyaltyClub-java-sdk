package pl.pietruszynski.loyaltyclub.sdk.core.model;

import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;

/**
 * Limity stronicowania wymuszane przez backend ({@code PageRequests}). SDK sprawdza je
 * lokalnie, bo naruszenie i tak konczy sie po stronie serwera bledem 400 — a numer strony
 * i jej rozmiar sa znane, zanim zadanie ruszy w siec.
 */
public final class PageRequests {

    /** Rozmiar strony przyjmowany przez backend, gdy klient go nie poda. */
    public static final int DEFAULT_PAGE_SIZE = 25;

    /** Gorny limit rozmiaru strony; powyzej backend odrzuca zadanie. */
    public static final int MAX_PAGE_SIZE = 200;

    private PageRequests() {
    }

    /** @throws LoyaltyClubValidationException gdy numer strony jest ujemny */
    public static Integer validatePage(Integer page) {
        if (page != null && page < 0) {
            throw new LoyaltyClubValidationException("page nie moze byc ujemne, bylo: " + page);
        }
        return page;
    }

    /** @throws LoyaltyClubValidationException gdy rozmiar strony wykracza poza {@code 1..}{@value #MAX_PAGE_SIZE} */
    public static Integer validateSize(Integer size) {
        if (size == null) {
            return null;
        }
        if (size < 1) {
            throw new LoyaltyClubValidationException("size musi byc wieksze od zera, bylo: " + size);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new LoyaltyClubValidationException(
                    "size nie moze przekraczac " + MAX_PAGE_SIZE + ", bylo: " + size);
        }
        return size;
    }
}
