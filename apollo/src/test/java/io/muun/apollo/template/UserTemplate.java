package io.muun.apollo.template;

import io.muun.apollo.domain.model.User;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class UserTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(User.class).addTemplate("valid", new Rule() {{
            add("hid", TemplateHelpers.houstonId());
            add("firstName", firstName());
            add("lastName", lastName());
            add("phoneNumber", TemplateHelpers.phoneNumber());
            add("profilePictureUrl", TemplateHelpers.profilePictureUrl());
            add("primaryCurrency", TemplateHelpers.currency());
        }});
    }
}
