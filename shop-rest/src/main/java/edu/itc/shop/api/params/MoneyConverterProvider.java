package edu.itc.shop.api.params;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import edu.itc.shop.domain.Money;

/**
 * Converts {@code "5 EUR"} query/path/header values to {@link Money}. A parse failure becomes a 400:
 * Jersey would otherwise swallow an IllegalArgumentException (null value) or turn other errors into a
 * 404 for query parameters.
 */
@Provider
public class MoneyConverterProvider implements ParamConverterProvider {

    private static final ParamConverter<Money> MONEY = new ParamConverter<>() {
        @Override
        public Money fromString(String value) {
            if (value == null) return null;
            try {
                return Money.parse(value);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }

        @Override
        public String toString(Money value) {
            return value == null ? null : value.toString();
        }
    };

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
        return rawType == Money.class ? (ParamConverter<T>) MONEY : null;
    }
}
