package io.muun.apollo.template;

import io.muun.apollo.domain.model.PublicProfile;

import br.com.six2six.fixturefactory.Fixture;
import br.com.six2six.fixturefactory.Rule;
import br.com.six2six.fixturefactory.loader.TemplateLoader;

public class PublicProfileTemplate implements TemplateLoader {

    @Override
    public void load() {

        Fixture.of(PublicProfile.class).addTemplate("valid", new Rule() {{
            add("id", sequence(0L, 1));
            add("hid", TemplateHelpers.houstonId());
            add("firstName", firstName());
            add("lastName", lastName());
            add("profilePictureUrl", TemplateHelpers.profilePictureUrl());
        }});

        Fixture.of(PublicProfile.class).addTemplate("from server").inherits("valid", new Rule() {{
            add("id", null);
        }});
    }
}
