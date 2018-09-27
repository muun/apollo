package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository;
import io.muun.apollo.data.preferences.FeeWindowRepository;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.model.ExchangeRateWindow;
import io.muun.apollo.domain.model.FeeWindow;
import io.muun.apollo.domain.model.RealTimeData;

import br.com.six2six.fixturefactory.Fixture;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import rx.Observable;

import static io.muun.apollo.TestUtils.fetchItemFromObservable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

public class SyncActionsTest extends BaseTest {

    @Mock
    private ExchangeRateWindowRepository exchangeRateWindowRepository;

    @Mock
    private FeeWindowRepository feeWindowRepository;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private AsyncActionStore asyncActionStore;

    private SyncActions syncActions;

    @Before
    public void setUp() {
        syncActions = new SyncActions(
                exchangeRateWindowRepository,
                feeWindowRepository,
                houstonClient,
                asyncActionStore
        );
    }

    @Test
    public void syncRealTimeData() {

        final FeeWindow feeWindow = Fixture
                .from(FeeWindow.class).gimme("valid");

        final ExchangeRateWindow exchangeRateWindow = Fixture
                .from(ExchangeRateWindow.class).gimme("valid");

        final RealTimeData realTimeData = new RealTimeData(
                feeWindow,
                exchangeRateWindow
        );

        doReturn(Observable.just(realTimeData))
                .when(houstonClient).fetchRealTimeData();

        fetchItemFromObservable(syncActions.syncRealTimeData());

        verify(feeWindowRepository).store(feeWindow);
        verify(exchangeRateWindowRepository).store(exchangeRateWindow);
    }
}