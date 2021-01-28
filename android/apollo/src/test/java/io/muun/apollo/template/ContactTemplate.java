package io.muun.apollo.template;

import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.PublicProfile;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class ContactTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(Contact.class).addTemplate("valid", new Rule() {{
            add("id", sequence(0L, 1));
            add("hid", TemplateHelpers.houstonIdBuiltInLong());
            add("publicProfile", one(PublicProfile.class, "valid"));
            add("publicKey", TemplateHelpers.contactPublicKey());
            add("cosigningPublicKey", TemplateHelpers.contactPublicKey());
            add("maxAddressVersion", 2);
            add("lastDerivationIndex", random(Long.class, range(0, 100000)));
        }});

        Fixture.of(Contact.class).addTemplate("from server").inherits("valid", new Rule() {{
            add("id", null);
            add("publicProfile", one(PublicProfile.class, "from server"));
        }});
    }
}
