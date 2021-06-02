package io.muun.apollo.presentation.app.di;

import io.muun.apollo.data.di.DataComponent;
import io.muun.apollo.presentation.app.ApolloApplication;
import io.muun.apollo.presentation.ui.base.di.ActivityComponent;
import io.muun.apollo.presentation.ui.base.di.FragmentComponent;

import dagger.Component;

@PerApplication
@Component(dependencies = DataComponent.class)
public interface ApplicationComponent {

    void inject(ApolloApplication application);

    FragmentComponent fragmentComponent();

    ActivityComponent activityComponent();

}
