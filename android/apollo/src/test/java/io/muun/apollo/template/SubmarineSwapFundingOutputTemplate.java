package io.muun.apollo.template;

import io.muun.apollo.domain.model.SubmarineSwapFundingOutput;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class SubmarineSwapFundingOutputTemplate implements TemplateLoader {

    @Override
    public void load() {
        Fixture.of(SubmarineSwapFundingOutput.class).addTemplate("valid", new Rule() {{
            add("outputAddress", TemplateHelpers.address());
            add("outputAmountInSatoshis", TemplateHelpers.satoshis());
            add("confirmationsNeeded", random(0, 1, 2));
            add("userLockTime", (int) System.currentTimeMillis() + 24 * 60 * 60);
            add("userRefundAddress", TemplateHelpers.muunAddress());
            add("serverPaymentHashInHex", TemplateHelpers.publicKeyHex());
            add("serverPublicKeyInHex", TemplateHelpers.publicKeyHex());
        }});
    }
}

