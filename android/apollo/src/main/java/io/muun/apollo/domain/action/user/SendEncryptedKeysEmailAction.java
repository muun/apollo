package io.muun.apollo.domain.action.user;

import io.muun.apollo.data.net.HoustonClient;
import io.muun.apollo.data.preferences.KeysRepository;
import io.muun.apollo.domain.action.base.BaseAsyncAction0;
import io.muun.common.rx.RxHelper;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SendEncryptedKeysEmailAction extends BaseAsyncAction0<Void> {

    private final KeysRepository keysRepository;
    private final HoustonClient houstonClient;

    /**
     * Upload the encrypted base private key (e.g to be sent via email)
     */
    @Inject
    public SendEncryptedKeysEmailAction(KeysRepository keysRepository,
                                        HoustonClient houstonClient) {

        this.keysRepository = keysRepository;
        this.houstonClient = houstonClient;
    }

    @Override
    public Observable<Void> action() {

        return keysRepository.getEncryptedBasePrivateKey()
                .flatMap(houstonClient::sendEncryptedKeysEmail)
                .map(RxHelper::toVoid);
    }
}
