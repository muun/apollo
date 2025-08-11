package io.muun.apollo;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.data.external.UserFacingErrorMessages;
import io.muun.apollo.data.logging.LoggingContext;

import br.com.six2six.fixturefactory.loader.FixtureFactoryLoader;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public abstract class BaseTest {

    @BeforeClass
    public static void classSetUp() {
        FixtureFactoryLoader.loadTemplates("io.muun.apollo.template");
        LoggingContext.setSendToCrashlytics(false);

        UserFacingErrorMessages.INSTANCE = mock(UserFacingErrorMessages.class, invocation -> "Foo");
        Globals.INSTANCE = mock(Globals.class);
    }
}
