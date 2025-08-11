package io.muun.apollo.template;

import io.muun.apollo.domain.model.PhoneContact;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class PhoneContactTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(PhoneContact.class).addTemplate("valid", new Rule() {{
            add("id", sequence(0L, 1));
            add("internalId", regex("\\d{10,15}"));
            add("name", firstName());
            add("phoneNumber", TemplateHelpers.phoneNumber());
            add("phoneNumberHash", TemplateHelpers.hash256());
        }});
    }
}
