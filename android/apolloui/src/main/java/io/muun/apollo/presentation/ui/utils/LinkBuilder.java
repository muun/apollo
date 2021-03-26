package io.muun.apollo.presentation.ui.utils;

import io.muun.apollo.domain.model.SubmarineSwapReceiver;
import io.muun.apollo.presentation.analytics.Analytics;
import io.muun.apollo.presentation.analytics.AnalyticsEvent;
import io.muun.apollo.presentation.app.di.PerApplication;
import io.muun.apollo.presentation.ui.view.RichText;
import io.muun.common.bitcoinj.NetworkParametersHelper;

import android.content.Context;
import android.text.TextUtils;
import org.bitcoinj.core.NetworkParameters;

import javax.inject.Inject;

@PerApplication
public class LinkBuilder {

    private final Context context;
    private final NetworkParameters network;
    private final Analytics analytics;

    @Inject
    LinkBuilder(Context context, NetworkParameters network, Analytics analytics) {
        this.context = context;
        this.network = network;
        this.analytics = analytics;
    }

    /**
     * Create a RichText containing a clickable link to a transaction in a block explorer.
     */
    public RichText transactionLink(String txId) {
        return createLink(txId, rawTransactionLink(txId), "block_explorer_tx");
    }

    /**
     * Create a plain-text link to a transaction in a block explorer.
     */
    public String rawTransactionLink(String txId) {
        final String urlRoot = isTestnet(network)
                ? "https://mempool.space/testnet/tx/"
                : "https://mempool.space/tx/";

        return urlRoot + txId;
    }

    /**
     * Create a RichText containing a clickable link to an address in a block explorer.
     */
    public RichText addressLink(String address) {

        final String urlRoot = isTestnet(network)
                ? "https://mempool.space/testnet/address/"
                : "https://mempool.space/address/";

        return createLink(address, urlRoot + address, "block_explorer_address");
    }

    /**
     * Create a RichText containing a clickable link to a lightning node in an explorer.
     */
    public RichText lightningNodeLink(SubmarineSwapReceiver receiver) {
        return lightningNodeLink(receiver, getLnLinkText(receiver));
    }

    /**
     * Create a RichText containing a clickable link to a lightning node in an explorer.
     */
    public RichText lightningNodeLink(SubmarineSwapReceiver receiver, String linkText) {

        final String urlRoot = isTestnet(network)
                ? "https://1ml.com/testnet/node/"
                : "https://1ml.com/node/";

        return createLink(linkText, urlRoot + receiver.getPublicKey(), "node_explorer");
    }

    /**
     * Create a RichText containing a clickable link to the recovery tool repository.
     */
    public RichText recoveryToolLink() {
        final String url = "https://github.com/muun/recovery";

        return createLink(url, url, "recovery_tool");
    }

    private String getLnLinkText(SubmarineSwapReceiver receiver) {
        if (!TextUtils.isEmpty(receiver.getAlias())) {
            return receiver.getAlias();
        }

        return receiver.getFormattedDestination();
    }

    /**
     * Create a RichText containing a clickable link.
     */
    private RichText createLink(String text, String url, String trackingName) {
        return new RichText(text).setLink(() -> {
            ExtensionsKt.openInBrowser(context, url);
            analytics.report(new AnalyticsEvent.E_OPEN_WEB(trackingName, url));

        });
    }

    private static boolean isTestnet(NetworkParameters network) {
        return NetworkParametersHelper.isTestingNetwork(network);
    }
}
