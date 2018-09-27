package io.muun.apollo.domain.action;

import io.muun.apollo.BaseTest;
import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.base.AsyncActionStore;
import io.muun.common.Optional;
import io.muun.common.api.SetupChallengeResponse;
import io.muun.common.crypto.ChallengePrivateKey;
import io.muun.common.crypto.ChallengePublicKey;
import io.muun.common.crypto.ChallengeType;
import io.muun.common.model.challenge.ChallengeSetup;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import rx.Observable;
import rx.observers.TestSubscriber;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SigninActionsTest extends BaseTest {

    private static final String SOME_ENCRYPTED_MUUN_KEY = "SOME_ENCRYPTED_MUUN_KEY";
    private static final String SOME_USER_INPUT = "ABCD";
    private static final byte[] SOME_SALT = new byte[32];
    private static final ChallengePublicKey SOME_CHALLENGE_PUBLIC_KEY =
            ChallengePrivateKey.fromUserInput(SOME_USER_INPUT, SOME_SALT).getChallengePublicKey();

    @Mock
    private AsyncActionStore asyncActionStore;

    @Mock
    private KeysRepository keysRepository;

    @Mock
    private HoustonClient houstonClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserActions userActions;

    @InjectMocks
    private SigninActions signinActions;

    private Observable<SetupChallengeResponse> someChallengeResponse;


    @Before
    public void setUp() {
        when(userActions.buildChallengeSetup(any(ChallengeType.class), any(String.class)))
                .thenReturn(Observable.just(new ChallengeSetup(
                    ChallengeType.RECOVERY_CODE,
                    SOME_CHALLENGE_PUBLIC_KEY,
                    new byte[32],
                    SOME_ENCRYPTED_MUUN_KEY,
                    1
                )));

        someChallengeResponse = Observable.just(
                new SetupChallengeResponse(SOME_ENCRYPTED_MUUN_KEY)
        );
    }

    @Test
    public void whenSetupChallengeKey_andBothChallengesAreSet_shouldStoreEncryptedMuunKey() {
        when(houstonClient.setupChallenge(any(ChallengeSetup.class)))
                .thenReturn(someChallengeResponse);


        withTestSubscriber((testSubscriber) ->
                signinActions.setupChallenge(ChallengeType.RECOVERY_CODE, SOME_USER_INPUT)
                        .subscribe(testSubscriber)
        );

        verify(keysRepository).storeEncryptedMuunPrivateKey(SOME_ENCRYPTED_MUUN_KEY);
    }

    @Test
    public void whenSetupChallengeKey_andMuunKeyIsNull_shouldNotStoreEncryptedMuunKey() {
        when(houstonClient.setupChallenge(any(ChallengeSetup.class)))
                .thenReturn(someChallengeResponse.map((response) -> {
                    response.muunKey = null;
                    return response;
                }));

        withTestSubscriber((testSubscriber) ->
                signinActions.setupChallenge(ChallengeType.RECOVERY_CODE, SOME_USER_INPUT)
                        .subscribe(testSubscriber)
        );

        verify(keysRepository, never()).storeEncryptedMuunPrivateKey(anyString());
    }

    @Test
    public void whenSetupChallengeKeySuccess_shouldStorePublicChallengeKey() {

        final ArgumentCaptor<ChallengeSetup> challenge =
                ArgumentCaptor.forClass(ChallengeSetup.class);
        when(houstonClient.setupChallenge(challenge.capture())).thenReturn(someChallengeResponse);

        withTestSubscriber((testSubscriber) ->
                signinActions.setupChallenge(ChallengeType.RECOVERY_CODE, SOME_USER_INPUT)
                        .subscribe(testSubscriber)
        );

        verify(userActions).storeChallengeKey(
                eq(challenge.getValue().type),
                eq(challenge.getValue().publicKey)
        );
    }

    private static <T> void withTestSubscriber(Optional.Consumer<TestSubscriber<T>> block) {
        final TestSubscriber<T> testSubscriber = TestSubscriber.create();

        block.consume(testSubscriber);

        // Unless we assert this, errors on Rx events will fail silently.
        testSubscriber.assertNoErrors();
        testSubscriber.assertCompleted();

        // Make sure we consume all events before finishing so we don't run into race conditions
        // when asserting.
        testSubscriber.awaitTerminalEvent();
    }
}
