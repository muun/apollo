package io.muun.apollo.presentation.app;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.presentation.ui.utils.OS;

import com.github.moduth.blockcanary.BlockCanary;
import com.github.moduth.blockcanary.BlockCanaryContext;

public class ApolloDebugApplication extends ApolloApplication {

    @Override
    protected void setupDebugTools() {
        super.setupDebugTools();

        // The release flavor should override this with an empty method on ApolloProdApplication.
        // We are leaving the extra check for redundancy.
        if (Globals.INSTANCE.isDebugBuild()) {
            if (!OS.INSTANCE.requiresPendingIntentMutabilityFlags()) {
                // For latest android versions (require mutability flags), don't install blockCanary
                BlockCanary.install(this, new AppBlockCanaryContext()).start();
            }
        }
    }

    public static class AppBlockCanaryContext extends BlockCanaryContext {

        @Override
        public int provideBlockThreshold() {
            return 300;
        }

        @Override
        public String providePath() {
            return "/data/" + Globals.INSTANCE.getApplicationId() + "/files/blocks";
        }
    }
}