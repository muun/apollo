package io.muun.apollo.template;

import io.muun.apollo.domain.model.FeeWindow;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;
import org.threeten.bp.ZonedDateTime;

import java.util.HashMap;

public class FeeWindowTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(FeeWindow.class).addTemplate("valid", new Rule() {{
            add("houstonId", TemplateHelpers.houstonId());
            add("fetchDate", ZonedDateTime.now());
            add("targetedFees", new HashMap<Integer, Double>() {
                {
                    put(1, 25.0);
                }
            });
        }});
    }
}
