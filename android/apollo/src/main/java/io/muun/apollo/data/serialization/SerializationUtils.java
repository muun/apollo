package io.muun.apollo.data.serialization;

import io.muun.apollo.data.serialization.dates.MuunZonedDateTimeDeserializer;
import io.muun.apollo.data.serialization.dates.MuunZonedDateTimeSerializer;
import io.muun.apollo.data.serialization.dates.ZonedDateTimeDeserializer;
import io.muun.apollo.data.serialization.dates.ZonedDateTimeSerializer;
import io.muun.apollo.domain.errors.MissingCurrencyError;
import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.utils.DateUtils;
import io.muun.common.dates.MuunZonedDateTime;
import io.muun.common.model.PhoneNumber;

import android.util.Base64;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.javamoney.moneta.Money;
import org.threeten.bp.ZonedDateTime;
import org.zalando.jackson.datatype.money.MoneyModule;
import timber.log.Timber;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import javax.money.MonetaryAmount;
import javax.money.MonetaryException;
import javax.money.UnknownCurrencyException;
import javax.validation.constraints.NotNull;

public final class SerializationUtils {

    public static final ObjectMapper JSON_MAPPER;

    private static final TypeFactory TYPE_FACTORY;

    static {

        final SimpleModule simpleModule =
                new SimpleModule("ApolloZonedDateTime serializer/deserializer")
                        .addSerializer(ZonedDateTime.class, new ZonedDateTimeSerializer())
                        .addDeserializer(ZonedDateTime.class, new ZonedDateTimeDeserializer())

                        .addSerializer(MuunZonedDateTime.class, new MuunZonedDateTimeSerializer())
                        .addDeserializer(
                                MuunZonedDateTime.class,
                                new MuunZonedDateTimeDeserializer()
                        )
                        .addSerializer(PhoneNumber.class, new PhoneNumberSerializer())
                        .addDeserializer(PhoneNumber.class, new PhoneNumberDeserializer())
                        .addSerializer(BitcoinAmount.class, new BitcoinAmountSerializer())
                        .addDeserializer(BitcoinAmount.class, new BitcoinAmountDeserializer());

        JSON_MAPPER = new ObjectMapper()
                .registerModule(simpleModule)
                .registerModule(new MoneyModule());

        // Allows unknown Enum values to be ignored and a predefined value specified through
        // @JsonEnumDefaultValue annotation. If enabled, but no predefined default Enum value is
        // specified, an exception will be thrown (default behaviour for unknown enums values).
        JSON_MAPPER.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        TYPE_FACTORY = JSON_MAPPER.getTypeFactory();
    }


    /**
     * Serialize an enum.
     */
    @NotNull
    public static <T extends Enum<T>> String serializeEnum(@NotNull T enumValue) {

        return enumValue.name();
    }

    /**
     * Deserialize an enum.
     */
    @NotNull
    public static <T extends Enum<T>> T deserializeEnum(@NotNull Class<T> enumClass,
                                                        @NotNull String enumString) {

        return Enum.valueOf(enumClass, enumString);
    }

    /**
     * Serialize a ZonedDateTime.
     */
    @NotNull
    public static String serializeDate(@NotNull ZonedDateTime dateValue) {

        return DateUtils.toIsoString(DateUtils.toUtc(dateValue));
    }

    /**
     * Deserialize a ZonedDateTime.
     */
    @Nullable
    public static ZonedDateTime deserializeDate(@Nullable String dateString) {

        return dateString == null ? null : DateUtils.fromIsoString(dateString);
    }

    /**
     * Serialize a class to JSON.
     */
    @NotNull
    public static <T> String serializeJson(@NotNull Class<T> jsonType, @NotNull T jsonValue) {

        try {
            return JSON_MAPPER.writerFor(jsonType).writeValueAsString(jsonValue);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a class to JSON.
     */
    @NotNull
    public static <T> String serializeJson(@NotNull TypeReference<? extends T> jsonType,
                                           @NotNull T jsonValue) {

        try {
            return JSON_MAPPER.writerFor(jsonType).writeValueAsString(jsonValue);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize a class from JSON.
     */
    @NotNull
    public static <T> T deserializeJson(@NotNull Class<T> jsonType, @NotNull String jsonString) {

        try {
            return JSON_MAPPER.readValue(jsonString, jsonType);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize a class from JSON.
     */
    @NotNull
    public static <T> T deserializeJson(@NotNull TypeReference<? extends T> jsonType,
                                        @NotNull String jsonString) {

        try {
            return JSON_MAPPER.readValue(jsonString, jsonType);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Convenience method for doing two-step conversion from given value into instance of given
     * value type (only if needed). If conversion is needed, this involves serialization and
     * deserialization.
     */
    @NotNull
    public static <T> T convertUsingMapper(Class<? extends T> jsonType, Object source) {

        return JSON_MAPPER.convertValue(source, jsonType);
    }

    /**
     * Serialize a BigDecimal.
     */
    @NotNull
    public static String serializeBigDecimal(@NotNull BigDecimal numberValue) {

        return numberValue.toString();
    }

    /**
     * Deserialize a BigDecimal.
     */
    @NotNull
    public static BigDecimal deserializeBigDecimal(@NotNull String numberString) {

        try {
            return new BigDecimal(numberString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a CurrencyUnit.
     */
    @NotNull
    public static String serializeCurrencyUnit(@NotNull CurrencyUnit currencyValue) {

        return currencyValue.getCurrencyCode();
    }

    /**
     * Deserialize a CurrencyUnit.
     */
    @NotNull
    public static CurrencyUnit deserializeCurrencyUnit(@NotNull String currencyString) {

        try {
            return Monetary.getCurrency(currencyString);

        } catch (UnknownCurrencyException e) {
            // In practice, only this type of error should arise.
            Timber.e(new MissingCurrencyError(e));
            throw new IllegalArgumentException(e);

        } catch (MonetaryException e) {
            // This more general kind can only happen when providers are incorrectly initialized.
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a MonetaryAmount.
     */
    @NotNull
    public static String serializeMonetaryAmount(@NotNull MonetaryAmount moneyValue) {

        final BigDecimal number = moneyValue.getNumber().numberValueExact(BigDecimal.class);
        final CurrencyUnit currency = moneyValue.getCurrency();

        return String.format("%s %s", serializeBigDecimal(number), serializeCurrencyUnit(currency));
    }

    /**
     * Deserialize a MonetaryAmount.
     */
    @NotNull
    public static MonetaryAmount deserializeMonetaryAmount(@NotNull String moneyString) {

        final String[] parts = moneyString.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException();
        }

        final BigDecimal number = deserializeBigDecimal(parts[0]);
        final CurrencyUnit currency = deserializeCurrencyUnit(parts[1]);

        return Money.of(number, currency);
    }

    /**
     * Serialize a BitcoinAmount.
     */
    @NotNull
    public static String serializeBitcoinAmount(@NotNull BitcoinAmount btcAmount) {
        return String.format(
                "%s;%s;%s",
                btcAmount.inSatoshis.toString(),
                serializeMonetaryAmount(btcAmount.inInputCurrency),
                serializeMonetaryAmount(btcAmount.inPrimaryCurrency)
        );
    }

    /**
     * Deserialize a BitcoinAmount.
     */
    @NotNull
    public static BitcoinAmount deserializeBitcoinAmount(@NotNull String string) {
        final String[] parts = string.split(";");
        if (parts.length != 3) {
            throw new IllegalArgumentException();
        }

        final Long inSatoshis = Long.valueOf(parts[0]);
        final MonetaryAmount inInputCurrency =  deserializeMonetaryAmount(parts[1]);
        final MonetaryAmount inPrimaryCurrency =  deserializeMonetaryAmount(parts[2]);

        return new BitcoinAmount(inSatoshis, inInputCurrency, inPrimaryCurrency);
    }

    /**
     * Serialize a list of objects to a JSON array.
     */
    public static <T> String serializeList(Class<T> itemType, List<T> items) {
        final TypeFactory typeFactory = JSON_MAPPER.getTypeFactory();

        try {
            return JSON_MAPPER
                    .writerFor(typeFactory.constructCollectionType(List.class, itemType))
                    .writeValueAsString(items);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize a list of objects from a JSON array.
     */
    public static <T> List<T> deserializeList(Class<T> itemType, String json) {
        try {
            return JSON_MAPPER
                    .readerFor(TYPE_FACTORY.constructCollectionType(List.class, itemType))
                    .readValue(json);

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a list of objects to a JSON array.
     */
    public static <K, V> String serializeMap(Class<K> keyType, Class<V> valueType, Map<K, V> map) {
        try {
            return JSON_MAPPER
                    .writerFor(TYPE_FACTORY.constructMapType(Map.class, keyType, valueType))
                    .writeValueAsString(map);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize a list of objects from a JSON array.
     */
    public static <K, V>  Map<K, V> deserializeMap(Class<K> keyType,
                                                   Class<V> valueType,
                                                   String json) {
        try {
            return JSON_MAPPER
                    .readerFor(TYPE_FACTORY.constructMapType(Map.class, keyType, valueType))
                    .readValue(json);

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a Serializable object.
     */
    @NotNull
    public static <T extends Serializable> String serializeObject(@NotNull T objectValue) {

        try {

            final ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
            final ObjectOutputStream objectStream = new ObjectOutputStream(byteArrayStream);

            objectStream.writeObject(objectValue);
            objectStream.close();

            return Base64.encodeToString(byteArrayStream.toByteArray(), Base64.NO_WRAP);

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Deserialize a Serializable object.
     */
    @SuppressWarnings("unchecked")
    @NotNull
    public static <T extends Serializable> T deserializeObject(@NotNull String objectString) {

        try {

            final byte[] data = Base64.decode(objectString, Base64.NO_WRAP);

            final ByteArrayInputStream byteArrayStream = new ByteArrayInputStream(data);
            final ObjectInputStream objectStream = new ObjectInputStream(byteArrayStream);

            final T object = (T) objectStream.readObject();
            objectStream.close();

            return object;

        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Serialize a byte array.
     */
    public static String serializeBytes(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * Deserialize a byte array.
     */
    public static byte[] deserializeBytes(String byteString) {
        return Base64.decode(byteString, Base64.NO_WRAP);
    }

    private SerializationUtils() {
        throw new AssertionError();
    }
}