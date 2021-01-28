package io.muun.apollo.template;

import io.muun.apollo.domain.model.BitcoinAmount;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

import javax.money.Monetary;

public class BitcoinAmountTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(BitcoinAmount.class).addTemplate("valid", new Rule() {{
            add("inSatoshis", TemplateHelpers.satoshis());
            add("inInputCurrency", TemplateHelpers.money());
            add("inPrimaryCurrency", TemplateHelpers.money());
        }});

        Fixture.of(BitcoinAmount.class).addTemplate("bitcoin input").inherits("valid", new Rule() {{
            add("inInputCurrency", TemplateHelpers.money(Monetary.getCurrency("BTC")));
        }});
    }
}
