package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.TestExecutor;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.domain.NotificationProcessor;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.errors.NotificationProcessingError;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.common.Optional;
import io.muun.common.api.NotificationJson;
import io.muun.common.api.messages.AbstractMessage;
import io.muun.common.model.SessionStatus;

import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import rx.Completable;
import rx.Observable;
import rx.schedulers.Schedulers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class NotificationActionsTest extends BaseTest {

    @Mock
    private SigninActions signinActions;

    @Mock
    private OperationActions operationActions;

    @Mock
    private UserActions userActions;

    @Mock
    private ContactActions contactActions;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private AsyncActionStore asyncActionStore;

    private final TestExecutor executor = new TestExecutor();

    private final ExecutionTransformerFactory transformerFactory = new ExecutionTransformerFactory(
            executor,
            Schedulers.from(executor)
    );

    private final ModelObjectsMapper mapper = new ModelObjectsMapper(TestNet3Params.get());

    private NotificationProcessor notificationProcessor;
    private NotificationActions notificationActions;
    private TestMessageHandler testMessageHandler;

    @Before
    public void setUp() {
        notificationProcessor = spy(new NotificationProcessor(
                operationActions,
                contactActions,
                userActions,
                signinActions,
                mapper
        ));

        testMessageHandler = spy(new TestMessageHandler());

        notificationProcessor.addHandler(
                TestMessage.TYPE,
                TestMessage.PERMISSION,
                testMessageHandler::handleTestMessage);

        notificationActions = spy(new NotificationActions(
                notificationRepository,
                houstonClient,
                asyncActionStore,
                transformerFactory,
                notificationProcessor
        ));

        doReturn(Optional.of(SessionStatus.LOGGED_IN)).when(signinActions).getSessionStatus();
    }

    @Test
    public void onNotificationReport_isAsync() {
        // This test ensures the actual processing of a notification does not occur during the
        // execution of onNotificationReport.

        // This is important: onNotificationReport will be invoked from a GCM handler that's
        // expected to finish within a short timespan.
        final NotificationReport report = new NotificationReport();

        executor.pause();

        notificationActions.onNotificationReport(report);
        verify(notificationActions, never()).processReport(any()); // should be asynchronous

        executor.resume();
        executor.waitUntilFinished();

        verify(notificationActions, times(1)).processReport(report);
    }

    @Test
    public void processReport_isSequential() {
        final NotificationReport report1 = new NotificationReport();
        final NotificationReport report2 = new NotificationReport();

        notificationActions.onNotificationReport(report1);
        notificationActions.onNotificationReport(report2);

        executor.waitUntilFinished();

        final InOrder inOrder = inOrder(notificationActions);
        inOrder.verify(notificationActions).processReport(report1);
        inOrder.verify(notificationActions).processReport(report2);
    }

    @Test
    public void processReport_correctDispatch() {
        final NotificationJson notification = new TestNotification(1L, 0L, "hello");

        notificationActions.processNotification(notification);
        verify(testMessageHandler, times(1)).handleTestMessage(notification);
    }

    @Test(expected = NotificationProcessingError.class)
    public void processReport_incorrectDispatch() {
        final NotificationJson notification = new NotificationJson(
                1L, 0L,
                "414e27a7-a59d-44be-a0f3-41440d1de669",
                "invalidType",
                "{}"
        );

        notificationActions.processNotification(notification);
    }

    @Test
    public void processReport_detectsGapBefore() {
        final long lastProcessedId = 0L;
        final NotificationJson first = new TestNotification(2L, 1L, "first"); // gap before!
        final NotificationJson second = new TestNotification(3L, 2L, "second");

        doReturn(lastProcessedId).when(notificationRepository).getLastProcessedId();

        doReturn(Observable.just(Arrays.asList(first, second)))
                .when(houstonClient).fetchNotificationsAfter(lastProcessedId);

        final NotificationReport report = new NotificationReport(
                first.previousId, second.id, Arrays.asList(second)
        );

        notificationActions.processReport(report);

        verify(houstonClient).fetchNotificationsAfter(lastProcessedId);
    }

    @Test
    public void processReport_detectsGapAfter() {
        final long lastProcessedId = 1L;
        final NotificationJson first = new TestNotification(2L, 1L, "first");

        doReturn(lastProcessedId).when(notificationRepository).getLastProcessedId();

        doReturn(Observable.just(Arrays.asList(first)))
                .when(houstonClient).fetchNotificationsAfter(lastProcessedId);

        final NotificationReport report = new NotificationReport(
                first.previousId, first.id + 1, Arrays.asList(first) // gap after!
        );

        notificationActions.processReport(report);

        verify(houstonClient).fetchNotificationsAfter(lastProcessedId);
    }

    @Test
    public void processReport_detectNoGaps() {
        final long lastProcessedId = 1L;
        final NotificationJson first = new TestNotification(2L, 1L, "first");

        doReturn(lastProcessedId).when(notificationRepository).getLastProcessedId();

        final NotificationReport report = new NotificationReport(
                1L, first.id, Arrays.asList(first)
        );

        notificationActions.processReport(report);

        verify(houstonClient, never()).fetchNotificationsAfter(lastProcessedId);
    }

    @Test
    public void processNotificationList_stopOnError() {
        doReturn(0L).when(notificationRepository).getLastProcessedId();

        final List<NotificationJson> list = Arrays.asList(
                new TestNotification(1L, 0L, "first"),
                new ExplodingNotification(2L, 1L),
                new TestNotification(3L, 2L, "never reached")
        );

        notificationActions.processNotificationList(list);

        verify(notificationRepository, times(1)).setLastProcessedId(anyLong());

        verify(notificationActions).processNotification(list.get(0));
        verify(notificationRepository).setLastProcessedId(list.get(0).id);

        verify(notificationActions).processNotification(list.get(1));
        verify(notificationRepository, never()).setLastProcessedId(list.get(1).id);
    }

    @Test
    public void processNotificationList_confirmDelivery() {
        // NOTE: this relies on an implementation detail: `processNotificationList()` calls
        // `getLastProcessedId()` twice, once before starting and once after finishing.
        doReturn(1L).doReturn(2L).when(notificationRepository).getLastProcessedId();

        doReturn(Observable.just(null)).when(houstonClient)
                .confirmNotificationsDeliveryUntil(anyLong());

        final List<NotificationJson> list = Arrays.asList(
                new TestNotification(1L, 0L, "first"),
                new TestNotification(2L, 1L, "second")
        );

        notificationActions.processNotificationList(list);

        verify(houstonClient).confirmNotificationsDeliveryUntil(list.get(1).id);
    }

    @Test
    public void processNotification_updatesLastProcessedId() {
        final NotificationJson notification = new TestNotification(2L, 1L, "first");
        doReturn(1L).when(notificationRepository).getLastProcessedId();

        notificationActions.processNotification(notification);

        verify(notificationRepository, times(1)).setLastProcessedId(notification.id);
    }

    private static class TestMessageHandler {
        public Completable handleTestMessage(NotificationJson json) {
            return Completable.complete();
        }
    }

    private static class TestNotification extends NotificationJson {
        public TestNotification(Long id, Long previousId, String debugMsg) {
            super(
                    id, previousId,
                    "414e27a7-a59d-44be-a0f3-41440d1de669",
                    TestMessage.TYPE,
                    new HashMap<String, String>() {{
                        put("debugString", debugMsg);
                    }}
            );
        }
    }

    private static class TestMessage extends AbstractMessage {
        public static final String TYPE = "test";
        public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

        public String debugString;

        @Override
        public String getType() {
            return TYPE;
        }

        @Override public SessionStatus getPermission() {
            return PERMISSION;
        }

        @Override public String toLog() {
            return TYPE;
        }
    }

    private static class ExplodingMessageHandler {
        public Completable handleExplodingMessage(NotificationJson json) {
            return Completable.error(new RuntimeException());
        }
    }

    private static class ExplodingNotification extends NotificationJson {
        public ExplodingNotification(Long id, Long previousId) {
            super(
                    id, previousId,
                    "414e27a7-a59d-44be-a0f3-41440d1de669",
                    ExplodingMessage.TYPE,
                    new HashMap<String, String>()
            );
        }
    }

    private static class ExplodingMessage extends AbstractMessage {
        public static final String TYPE = "boom";
        public static final SessionStatus PERMISSION = SessionStatus.LOGGED_IN;

        @Override
        public String getType() {
            return TYPE;
        }

        @Override public SessionStatus getPermission() {
            return PERMISSION;
        }

        @Override public String toLog() {
            return TYPE;
        }
    }
}