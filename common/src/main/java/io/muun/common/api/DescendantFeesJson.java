package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.Objects;
import javax.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DescendantFeesJson {

    @NotEmpty
    public String hash;

    @NotNull
    public Long feeInSat;

    @NotNull
    public Long sizeInVbytes;

    @NotNull
    public Long accumulatedAncestorSizeInVbytes; // including this tx

    @NotNull
    public Long accumulatedAncestorFeeInSat; // including this tx

    /**
     * Constructor.
     */
    public DescendantFeesJson(String hash,
                              Long feeInSat,
                              Long sizeInVbytes,
                              Long accumulatedAncestorSizeInVbytes,
                              Long accumulatedAncestorFeeInSat) {
        this.hash = hash;
        this.feeInSat = feeInSat;
        this.sizeInVbytes = sizeInVbytes;
        this.accumulatedAncestorSizeInVbytes = accumulatedAncestorSizeInVbytes;
        this.accumulatedAncestorFeeInSat = accumulatedAncestorFeeInSat;
    }

    /**
     * Json constructor.
     */
    public DescendantFeesJson() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final DescendantFeesJson that = (DescendantFeesJson) o;
        return hash.equals(that.hash)
                && feeInSat.equals(that.feeInSat)
                && sizeInVbytes.equals(that.sizeInVbytes)
                && accumulatedAncestorSizeInVbytes.equals(that.accumulatedAncestorSizeInVbytes)
                && accumulatedAncestorFeeInSat.equals(that.accumulatedAncestorFeeInSat);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                hash,
                feeInSat,
                sizeInVbytes,
                accumulatedAncestorSizeInVbytes,
                accumulatedAncestorFeeInSat
        );
    }
}
