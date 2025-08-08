package io.muun.apollo.template;

import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.apollo.domain.model.SubmarineSwapFundingOutput;
import io.muun.apollo.domain.model.SubmarineSwapReceiver;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;
import org.threeten.bp.Duration;
import org.threeten.bp.ZonedDateTime;

public class SubmarineSwapTemplate implements TemplateLoader {

    @Override
    public void load() {
        Fixture.of(SubmarineSwap.class).addTemplate("valid", new Rule() {{
            add("id", TemplateHelpers.houstonId());
            add("houstonUuid", TemplateHelpers.uuid());
            add("invoice", TemplateHelpers.lnInvoice());
            add("receiver", one(SubmarineSwapReceiver.class, "valid"));
            add("fundingOutput", one(SubmarineSwapFundingOutput.class, "valid"));
            add("sweepFeeInSatoshis", TemplateHelpers.satoshis());
            add("lightningFeeInSatoshis", TemplateHelpers.satoshis());
            add("expiresAt", ZonedDateTime.now().plus(Duration.ofHours(12)));
            add("payedAt", ZonedDateTime.now());
            add("preimageInHex", TemplateHelpers.publicKeyHex());
        }});
    }
}
