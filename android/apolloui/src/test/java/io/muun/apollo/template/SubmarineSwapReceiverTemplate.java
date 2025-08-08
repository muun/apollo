package io.muun.apollo.template;

import io.muun.apollo.domain.model.SubmarineSwapReceiver;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class SubmarineSwapReceiverTemplate implements TemplateLoader {

    @Override
    public void load() {
        Fixture.of(SubmarineSwapReceiver.class).addTemplate("valid", new Rule() {{
            add("alias", random("Satoshi", "Batman", "Goku"));
            add("serializedNetworkAddresses", TemplateHelpers.serializedNetworkAddresses());
            add("publicKey", TemplateHelpers.publicKeyBase58());
        }});
    }
}
