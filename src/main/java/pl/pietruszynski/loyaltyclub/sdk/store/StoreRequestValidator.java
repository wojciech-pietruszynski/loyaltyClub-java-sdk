package pl.pietruszynski.loyaltyclub.sdk.store;

import pl.pietruszynski.loyaltyclub.sdk.core.exception.LoyaltyClubValidationException;
import pl.pietruszynski.loyaltyclub.sdk.core.util.Validate;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreReturnRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreSaleRequest;
import pl.pietruszynski.loyaltyclub.sdk.store.model.StoreTransactionItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * Walidacja zadan sklepowych po stronie klienta. Odwzorowuje ograniczenia
 * {@code StoreTransactionService} i bean validation backendu, zeby oczywisty blad
 * kasy nie kosztowal round-tripu zakonczonego HTTP 400.
 *
 * <p>Kontrola zgodnosci sumy pozycji z kwota paragonu uzywa tej samej normalizacji,
 * co backend: zaokraglenie do dwoch miejsc w trybie HALF_UP.
 */
public final class StoreRequestValidator {

    private static final int AMOUNT_SCALE = 2;
    private static final int MAX_COUNTRY_CODE_LENGTH = 3;

    private StoreRequestValidator() {
    }

    /** Normalizuje kod kraju tak samo jak backend: trim i wielkie litery. */
    public static String normalizeCountryCode(String countryCode) {
        String normalized = Validate.requireText(countryCode, "countryCode").toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_COUNTRY_CODE_LENGTH) {
            throw new LoyaltyClubValidationException(
                    "countryCode moze miec najwyzej " + MAX_COUNTRY_CODE_LENGTH + " znaki, bylo: " + normalized);
        }
        return normalized;
    }

    public static void validateSale(StoreSaleRequest request) {
        Validate.requireNonNull(request, "request");
        Validate.requireText(request.getCustomerNumber(), "customerNumber");
        Validate.requireText(request.getSourceTransactionNumber(), "sourceTransactionNumber");
        validateItems(request.getItems());
        validateTotalAmount(request.getTotalAmount(), request.getItems());
    }

    public static void validateReturn(StoreReturnRequest request) {
        Validate.requireNonNull(request, "request");
        Validate.requireText(request.getCustomerNumber(), "customerNumber");
        Validate.requireText(request.getSourceTransactionNumber(), "sourceTransactionNumber");
        Validate.requireText(request.getSaleTransactionNumber(), "saleTransactionNumber");
        validateItems(request.getItems());
        validateTotalAmount(request.getTotalAmount(), request.getItems());
    }

    private static void validateItems(List<StoreTransactionItem> items) {
        Validate.requireNotEmpty(items, "items");
        for (int index = 0; index < items.size(); index++) {
            StoreTransactionItem item = items.get(index);
            String prefix = "items[" + index + "].";
            Validate.requireNonNull(item, "items[" + index + "]");
            Validate.requireText(item.getEan(), prefix + "ean");
            Validate.requireText(item.getName(), prefix + "name");
            Validate.requireNonNull(item.getHierarchy(), prefix + "hierarchy");
            Validate.requireText(item.getHierarchy().getHierarchy(), prefix + "hierarchy.hierarchy");
            Validate.requireNonNull(item.getPrice(), prefix + "price");
            Validate.requireNonNegative(item.getPrice().getAmount(), prefix + "price.amount");
            Validate.requireText(item.getPrice().getCurrency(), prefix + "price.currency");
        }
    }

    private static void validateTotalAmount(BigDecimal totalAmount, List<StoreTransactionItem> items) {
        Validate.requirePositive(totalAmount, "totalAmount");

        BigDecimal itemsTotal = items.stream()
                .map(item -> item.getPrice().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        BigDecimal normalizedTotal = totalAmount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (normalizedTotal.compareTo(itemsTotal) != 0) {
            throw new LoyaltyClubValidationException(
                    "totalAmount musi rownac sie sumie cen pozycji: " + normalizedTotal + " != " + itemsTotal);
        }
    }
}
