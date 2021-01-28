package io.muun.apollo.presentation.app;

import io.muun.apollo.data.external.Globals;

import android.annotation.SuppressLint;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.timber.StethoTree;
import com.github.moduth.blockcanary.BlockCanary;
import com.github.moduth.blockcanary.BlockCanaryContext;
import timber.log.Timber;

@SuppressLint("Registered")
public class ApolloDebugApplication extends ApolloApplication {

    @Override
    protected void setupDebugTools() {
        super.setupDebugTools();

        // The release flavor should override this with an empty method on ApolloProdApplication.
        // We are leaving the extra check for redundancy.
        if (Globals.INSTANCE.isDebugBuild()) {
            Stetho.initializeWithDefaults(this);
            Timber.plant(new StethoTree());

            BlockCanary.install(this, new AppBlockCanaryContext()).start();
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