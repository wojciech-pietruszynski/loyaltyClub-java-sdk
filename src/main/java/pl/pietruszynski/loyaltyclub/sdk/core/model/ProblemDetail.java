package pl.pietruszynski.loyaltyclub.sdk.core.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cialo bledu w formacie RFC 7807, ktore backend zwraca przez {@code GlobalExceptionHandler}.
 * Dodatkowe pola spoza specyfikacji (np. {@code errors} przy bledzie walidacji) laduja
 * w {@link #getProperties()}.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProblemDetail {

    private String type;
    private String title;
    private Integer status;
    private String detail;
    private String instance;

    private final Map<String, Object> properties = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonAnySetter
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Mapa {@code pole -> komunikat} z odpowiedzi 400 dla bledu walidacji bean validation.
     * Pusta, gdy backend nie dolaczyl wlasciwosci {@code errors}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getFieldErrors() {
        Object errors = properties.get("errors");
        if (!(errors instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
            result.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().toString());
        }
        return Collections.unmodifiableMap(result);
    }
}
