package io.muun.common.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AndroidAppInfoJson {

    public String name;

    public String label;

    public Integer icon;

    public Boolean debuggable;

    public Boolean persistent;


    /**
     * Json constructor.
     */
    @SuppressWarnings("unused") // Jackson requires it
    public AndroidAppInfoJson() {
    }

    /**
     * Code constructor.
     */
    public AndroidAppInfoJson(
            String name,
            String label,
            Integer icon,
            Boolean debuggable,
            Boolean persistent
    ) {
        this.name = name;
        this.label = label;
        this.icon = icon;
        this.debuggable = debuggable;
        this.persistent = persistent;
    }
}
