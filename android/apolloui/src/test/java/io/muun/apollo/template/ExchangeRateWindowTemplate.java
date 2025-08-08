package io.muun.apollo.template;

import io.muun.apollo.TestUtils;
import io.muun.apollo.domain.model.ExchangeRateWindow;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;
import com.fasterxml.jackson.core.type.TypeReference;
import org.threeten.bp.ZonedDateTime;

import java.util.List;
import java.util.Map;

public class ExchangeRateWindowTemplate implements TemplateLoader {

    @Override
    public void load() {

        final List<Map<String, Double>> json = TestUtils.loadJson(
                "exchange_rate_window.json",
                new TypeReference<List<Map<String, Double>>>() {}
        );

        Fixture.of(ExchangeRateWindow.class).addTemplate("valid", new Rule() {{
            add("windowHid", TemplateHelpers.houstonId());
            add("fetchDate", ZonedDateTime.now());
            add("rates", random(json.toArray()));
        }});
    }
}
