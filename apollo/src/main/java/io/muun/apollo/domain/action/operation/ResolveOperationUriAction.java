package io.muun.apollo.domain.action.operation;

import io.muun.apollo.domain.action.base.BaseAsyncAction1;
import io.muun.apollo.domain.model.OperationUri;
import io.muun.apollo.domain.model.PaymentRequest;
import io.muun.common.exception.MissingCaseError;

import rx.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ResolveOperationUriAction extends BaseAsyncAction1<OperationUri, PaymentRequest> {

    private final ResolveBitcoinUriAction resolveBitcoinUri;
    private final ResolveMuunUriAction resolveMuunUri;
    private final ResolveLnUriAction resolveLnUri;

    /**
     * Resolve an OperationUri, by fetching all necessary information from local and remote sources.
     */
    @Inject
    public ResolveOperationUriAction(ResolveBitcoinUriAction resolveBitcoinUri,
                                     ResolveMuunUriAction resolveMuunUri,
                                     ResolveLnUriAction resolveLnUri) {

        this.resolveBitcoinUri = resolveBitcoinUri;
        this.resolveMuunUri = resolveMuunUri;
        this.resolveLnUri = resolveLnUri;
    }

    @Override
    public Observable<PaymentRequest> action(OperationUri uri) {
        return Observable.defer(() -> {
            switch (uri.getScheme()) {
                case OperationUri.MUUN_SCHEME:
                    return resolveMuunUri.action(uri);

                case OperationUri.BITCOIN_SCHEME:
                    return resolveBitcoinUri.action(uri);

                case OperationUri.LN_SCHEME:
                    return resolveLnUri.action(uri);

                default:
                    throw new MissingCaseError(uri.toString(), "Operation URI resolution");
            }

        });
    }
}
