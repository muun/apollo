package io.muun.apollo.template;

import io.muun.apollo.domain.model.BitcoinAmount;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.model.SubmarineSwap;
import io.muun.common.crypto.hd.Schema;
import io.muun.common.model.OperationDirection;
import io.muun.common.model.OperationStatus;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;
import org.threeten.bp.ZonedDateTime;

public class OperationTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(Operation.class).addTemplate("incoming internal", new Rule() {{
            add("id", sequence(0L, 1));
            add("hid", TemplateHelpers.houstonIdBuiltInLong());
            add("direction", OperationDirection.INCOMING);
            add("isBitcoin", false);
            add("senderProfile", one(PublicProfile.class, "valid"));
            add("senderIsExternal", false);
            add("receiverProfile", one(PublicProfile.class, "valid"));
            add("receiverIsExternal", false);
            add("receiverAddress", TemplateHelpers.address());
            add("receiverAddressDerivationPath", Schema.getExternalKeyPath() + "/1");
            add("amount", one(BitcoinAmount.class, "valid"));
            add("fee", one(BitcoinAmount.class, "bitcoin input"));
            add("confirmations", random(Long.class, range(0, 6)));
            add("hash", TemplateHelpers.hash256());
            add("description", regex("[a-z ]{10,40}"));
            add("status", uniqueRandom(OperationStatus.class));
            add("creationDate", ZonedDateTime.now());
            add("exchangeRateWindowHid", TemplateHelpers.houstonId());
            add("swap", one(SubmarineSwap.class, "valid"));
        }});

        Fixture.of(Operation.class)
                .addTemplate("outgoing internal")
                .inherits("incoming internal", new Rule() {{
                    add("direction", OperationDirection.OUTGOING);
                }});

        // doesn't work due to a bug when setting a property to null
        Fixture.of(Operation.class)
                .addTemplate("incoming external")
                .inherits("incoming internal", new Rule() {{
                    add("isBitcoin", true);
                    add("senderProfile", null);
                    add("senderIsExternal", true);
                    add("amount", one(BitcoinAmount.class, "bitcoin input"));
                }});

        // doesn't work due to a bug when setting a property to null
        Fixture.of(Operation.class)
                .addTemplate("outgoing external")
                .inherits("incoming internal", new Rule() {{
                    add("direction", OperationDirection.OUTGOING);
                    add("isBitcoin", true);
                    add("receiverProfile", null);
                    add("receiverIsExternal", true);
                    add("receiverAddressDerivationPath", null);
                }});

        Fixture.of(Operation.class)
                .addTemplate("valid")
                .inherits("incoming internal", new Rule());
    }
}
