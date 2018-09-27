package io.muun.apollo;

import io.muun.apollo.data.logging.Logger;

import br.com.six2six.fixturefactory.loader.FixtureFactoryLoader;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public abstract class BaseTest {

    protected static final NetworkParameters NETWORK = TestNet3Params.get();

    @BeforeClass
    public static void classSetUp() {
        FixtureFactoryLoader.loadTemplates("io.muun.apollo.template");
        Logger.setLogToCrashlytics(false);
    }
}
