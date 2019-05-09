package io.muun.apollo.domain;

import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.data.preferences.UserRepository;
import io.muun.apollo.domain.action.OperationActions;
import io.muun.apollo.domain.action.UserActions;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import javax.inject.Inject;

public class NotificationCenter {

    private final UserRepository userRepository;

    private final OperationActions operationActions;
    private final UserActions userActions;


    private final ExecutionTransformerFactory transformerFactory;

    /**
     * Constructor.
     */
    @Inject
    public NotificationCenter(UserRepository userRepository,
                              OperationActions operationActions,
                              UserActions userActions,
                              ExecutionTransformerFactory transformerFactory) {

        this.userRepository = userRepository;
        this.operationActions = operationActions;
        this.userActions = userActions;
        this.transformerFactory = transformerFactory;
    }

    /**
     * Watch the count of pending notifications.
     */
    public Observable<Integer> watchCount() {
        return Observable.combineLatest(
                watchSetUpRecoveryCode(),
                watchVerifyEmail(),

                RxHelper::countTrue
        );
    }

    /**
     * Watch whether the user should set up her recovery code.
     */
    public Observable<Boolean> watchSetUpRecoveryCode() {
        if (userRepository.hasRecoveryCode()) {
            return Observable.just(false); // small optimization to avoid reading balance
        }

        return Observable
                .combineLatest(
                        operationActions.watchBalance(),
                        userRepository.watchHasRecoveryCode(),
                        (balance, hasRecoveryCode) -> (balance > 0 && !hasRecoveryCode)
                )
                .compose(transformerFactory.getAsyncExecutor());
    }

    /**
     * Watch whether the user should verify her email.
     */
    public Observable<Boolean> watchVerifyEmail() {
        return userActions.watchForEmailVerification()
                .map(isEmailVerified -> !isEmailVerified);
    }
}
