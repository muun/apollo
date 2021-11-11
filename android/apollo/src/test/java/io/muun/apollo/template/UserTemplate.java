package io.muun.apollo.template;

import io.muun.apollo.domain.model.user.User;
import io.muun.apollo.domain.model.user.UserPhoneNumber;
import io.muun.apollo.domain.model.user.UserProfile;
import io.muun.common.Optional;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class UserTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(User.class).addTemplate("valid", new Rule() {{
            add("hid", TemplateHelpers.houstonId());
            add("email", TemplateHelpers.email());
            add("isEmailVerified", false);
            add("phoneNumber", Optional.<UserPhoneNumber>empty());
            add("profile", Optional.<UserProfile>empty());
            add("primaryCurrency", TemplateHelpers.currency());
            add("hasRecoveryCode", false);
            add("hasP2PEnabled", false);
        }});

    }
}
