package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.TestExecutor;
import io.muun.apollo.data.external.AppStandbyBucketProvider;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.net.ModelObjectsMapper;
import io.muun.apollo.data.preferences.NotificationRepository;
import io.muun.apollo.domain.NotificationProcessor;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.apollo.domain.action.incoming_swap.FulfillIncomingSwapAction;
import io.muun.apollo.domain.action.operation.CreateOperationAction;
import io.muun.apollo.domain.action.operation.OperationMetadataMapper;
import io.muun.apollo.domain.action.operation.UpdateOperationAction;
import io.muun.apollo.domain.model.NotificationReport;
import io.muun.common.Optional;
import io.muun.common.api.beam.notification.NotificationJson;
import io.muun.common.api.messages.MessageOrigin;
import io.muun.common.api.messages.MessageSpec;
import io.muun.common.model.SessionStatus;

import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import rx.Completable;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private UserActions userActions;

    @Mock
    private ContactActions contactActions;

    @Mock
    private CreateOperationAction createOperationAction;

    @Mock
    private UpdateOperationAction updateOperationAction;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private AsyncActionStore asyncActionStore;

    @Mock
    private OperationMetadataMapper operationMapper;

    @Mock
    private FulfillIncomingSwapAction fulfillIncomingSwap;

    @Mock
    private NotificationService notificationService;

    private final TestExecutor executor = new TestExecutor();

    private final ModelObjectsMapper mapper = new ModelObjectsMapper(
            TestNet3Params.get()
    );

    private NotificationProcessor notificationProcessor;
    private NotificationActions notificationActions;

    private TestMessageHandler testMessageHandler;
    private ExplodingMessageHandler explodingMessageHandler;

    private long savedLastProcessedId;

    @Before
    public void setUp() {
        notificationProcessor = spy(new NotificationProcessor(
                updateOperationAction,
                createOperationAction,
                contactActions,
                userActions,
                signinActions,
                mapper,
                operationMapper,
                fulfillIncomingSwap,
                houstonClient,
                notificationService));

        testMessageHandler = spy(new TestMessageHandler());
        notificationProcessor.addHandler(TEST_SPEC, testMessageHandler);

        explodingMessageHandler = spy(new ExplodingMessageHandler());
        notificationProcessor.addHandler(EXPLODING_SPEC, explodingMessageHandler);

        // Provide realistic notificationRepository lastProcessedId, saving it to a variable:
        notificationActions = spy(new NotificationActions(
                notificationRepository,
                houstonClient,
                asyncActionStore,
                notificationProcessor,
                (AppStandbyBucketProvider) () -> AppStandbyBucketProvider.Bucket.ACTIVE,
                Schedulers.from(executor)
        ));

        savedLastProcessedId = 0;

        doAnswer(i -> savedLastProcessedId = i.getArgument(0))
                .when(notificationRepository).setLastProcessedId(anyLong());

        doAnswer(i -> savedLastProcessedId)
                .when(notificationRepository).getLastProcessedId();

        // Fake always being logged in:
        doReturn(Optional.of(SessionStatus.LOGGED_IN)).when(signinActions).getSessionStatus();
    }

    @Test
    public void onNotificationReport_isAsync() {
        // This test ensures the actual processing of a notification does not occur during the
        // execution of onNotificationReport.

        // This is important: onNotificationReport will be invoked from a GCM handler that's
        // expected to finish within a short timespan.
        final NotificationJson notif = new TestNotification(1L);

        executor.pause();

        notificationActions.onNotificationReport(reportOf(notif));
        verify(testMessageHandler, never()).call(any()); // should be asynchronous

        executor.resume();
        executor.waitUntilFinished();

        verify(testMessageHandler, times(1)).call(notif);
    }

    @Test
    public void processReport_isSequential() {
        final TestNotification notif1 = new TestNotification(1L);
        final TestNotification notif2 = new TestNotification(2L);

        notificationActions.onNotificationReport(reportOf(notif1));
        notificationActions.onNotificationReport(reportOf(notif2));

        executor.waitUntilFinished();

        final InOrder inOrder = inOrder(testMessageHandler);
        inOrder.verify(testMessageHandler).call(notif1);
        inOrder.verify(testMessageHandler).call(notif2);
    }

    @Test
    public void processReport_correctDispatch() {
        final NotificationJson notification = new TestNotification(1L);

        notificationActions.onNotificationReport(reportOf(notification));
        executor.waitUntilFinished();

        verify(testMessageHandler, times(1)).call(notification);
    }

    @Test
    public void processReport_detectsGapBefore() {
        final NotificationJson first = new TestNotification(2L); // gap before!
        final NotificationJson second = new TestNotification(3L);

        doReturn(Observable.just(Arrays.asList(first, second)))
                .when(houstonClient).fetchNotificationsAfter(anyLong());

        final NotificationReport report = new NotificationReport(
                first.previousId,
                second.id,
                Arrays.asList(first)
        );

        notificationActions.onNotificationReport(report);
        executor.waitUntilFinished();

        verify(houstonClient).fetchNotificationsAfter(0L);
    }

    @Test
    public void processReport_detectsGapAfter() {
        final NotificationJson first = new TestNotification(1L);

        doReturn(Observable.just(Arrays.asList(first)))
                .when(houstonClient).fetchNotificationsAfter(anyLong());

        final NotificationReport report = new NotificationReport(
                first.previousId,
                first.id + 1, // gap after!
                Arrays.asList(first)
        );

        notificationActions.onNotificationReport(report);
        executor.waitUntilFinished();

        verify(houstonClient).fetchNotificationsAfter(0L);
    }

    @Test
    public void processReport_detectNoGaps() {
        savedLastProcessedId = 1L;
        final NotificationJson first = new TestNotification(2L);

        final NotificationReport report = new NotificationReport(
                first.previousId,
                first.id,
                Arrays.asList(first)
        );

        notificationActions.onNotificationReport(report);
        executor.waitUntilFinished();

        verify(houstonClient, never()).fetchNotificationsAfter(savedLastProcessedId);
    }

    @Test
    public void processNotificationList_skipErrors() {
        final List<NotificationJson> list = Arrays.asList(
                new TestNotification(1L),
                new TestNotification(2L, EXPLODING_SPEC),
                new TestNotification(3L) // will be processed, even if previous fails
        );

        notificationActions.onNotificationReport(reportOf(list));
        executor.waitUntilFinished();

        verify(testMessageHandler).call(list.get(0));
        verify(explodingMessageHandler).call(list.get(1));
        verify(testMessageHandler).call(list.get(2));

        verify(notificationRepository).setLastProcessedId(list.get(2).id);
    }

    @Test
    public void processNotificationList_confirmDelivery() {

        final List<NotificationJson> list = Arrays.asList(
                new TestNotification(1L),
                new TestNotification(2L)
        );

        notificationActions.onNotificationReport(reportOf(list));
        executor.waitUntilFinished();

        verify(houstonClient).confirmNotificationsDeliveryUntil(
                eq(list.get(1).id), any(), any(), any()
        );
    }

    @Test
    public void processNotification_updatesLastProcessedId() {
        savedLastProcessedId = 1L;
        final NotificationJson notification = new TestNotification(2L);

        notificationActions.onNotificationReport(reportOf(notification));
        executor.waitUntilFinished();

        verify(notificationRepository, times(1)).setLastProcessedId(notification.id);
    }

    private static class TestMessageHandler implements Func1<NotificationJson, Completable> {
        public Completable call(NotificationJson notificationJson) {
            return Completable.complete();
        }
    }

    private static class ExplodingMessageHandler implements Func1<NotificationJson, Completable> {
        public Completable call(NotificationJson json) {
            return Completable.error(new RuntimeException());
        }
    }

    private static class TestNotification extends NotificationJson {
        public TestNotification(Long id) {
            this(id, TEST_SPEC);
        }

        public TestNotification(Long id, MessageSpec spec) {
            super(
                    id,
                    id - 1,
                    "414e27a7-a59d-44be-a0f3-41440d1de669",
                    "414e27a7-a59d-44be-a0f3-41440d1de669",
                    spec.messageType,
                    new HashMap<String, String>(),
                    null
            );
        }
    }

    public static final MessageSpec TEST_SPEC = new MessageSpec(
            "test",
            SessionStatus.CREATED,
            MessageOrigin.ANY
    );

    public static final MessageSpec EXPLODING_SPEC = new MessageSpec(
            "BOOM!",
            SessionStatus.CREATED,
            MessageOrigin.ANY
    );


    private NotificationReport reportOf(NotificationJson ...notifs) {
        return reportOf(Arrays.asList(notifs));
    }

    private NotificationReport reportOf(List<NotificationJson> notifs) {
        return new NotificationReport(
                notifs.get(0).previousId,
                notifs.get(notifs.size() - 1).id,
                notifs
        );
    }
}