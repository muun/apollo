package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import javax.validation.constraints.NotEmpty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeeBumpFunctionsJson {

    @NotEmpty
    public String uuid;

    @NotEmpty
    public List<String> functions;

    /**
     * Json constructor.
     */
    public FeeBumpFunctionsJson() {
    }

    public FeeBumpFunctionsJson(String uuid, List<String> functions) {

        this.uuid = uuid;
        this.functions = functions;
    }
}
