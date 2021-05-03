package io.muun.apollo.presentation.app;

import io.muun.apollo.R;
import io.muun.apollo.data.external.NotificationService;
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory;
import io.muun.apollo.domain.model.Contact;
import io.muun.apollo.domain.model.Operation;
import io.muun.apollo.domain.model.PublicProfile;
import io.muun.apollo.domain.selector.CurrencyDisplayModeSelector;
import io.muun.apollo.presentation.ui.helper.BitcoinHelper;
import io.muun.apollo.presentation.ui.helper.MoneyHelper;
import io.muun.apollo.presentation.ui.home.HomeActivity;
import io.muun.apollo.presentation.ui.operation_detail.OperationDetailActivity;
import io.muun.common.model.OperationDirection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import rx.Observable;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import static io.muun.common.utils.Preconditions.checkArgument;
import static io.muun.common.utils.Preconditions.checkNotNull;

public class NotificationServiceImpl implements NotificationService {

    private static final int PROFILE_PICTURE_HEIGHT = 250;

    private static final int PROFILE_PICTURE_WIDTH = 250;

    private static final int NOTIFICATION_IMAGE_HEIGHT = 32;

    private static final int NOTIFICATION_IMAGE_WIDTH = 32;

    private final Context context;
    private final ExecutionTransformerFactory executionTransformerFactory;
    private final CurrencyDisplayModeSelector currencyDisplayModeSel;

    /**
     * Creates a NotificationServiceImpl.
     */
    @Inject
    public NotificationServiceImpl(Context applicationContext,
                                   ExecutionTransformerFactory executionTransformerFactory,
                                   CurrencyDisplayModeSelector currencyDisplayModeSel) {

        this.context = applicationContext;
        this.executionTransformerFactory = executionTransformerFactory;
        this.currencyDisplayModeSel = currencyDisplayModeSel;
    }

    /**
     * Show a notification for a new incoming operation.
     */
    public void showNewOperationNotification(@NotNull Operation operation) {

        checkNotNull(operation.getId());
        checkArgument(operation.direction == OperationDirection.INCOMING);

        if (operation.isExternal) {
            showNewOperationFromNetworkNotification(operation);

        } else {
            checkNotNull(operation.senderProfile);
            showNewOperationFromUserNotification(operation);
        }
    }

    /**
     * Show a notification for a new contact.
     */
    public void showNewContactNotification(@NotNull Contact contact) {

        final String title = context.getResources().getString(
                R.string.notifications_new_contact,
                contact.publicProfile.getFullName());

        final Intent intent = HomeActivity.getStartActivityIntent(context);

        showNotificationFromContact(contact.publicProfile, title, null, intent);
    }

    private void showNewOperationFromNetworkNotification(Operation op) {

        final String title = context.getResources().getString(
                R.string.notifications_amount_received,
                BitcoinHelper.formatShortBitcoinAmount(
                        op.amount.inSatoshis,
                        currencyDisplayModeSel.get()
                )
        );

        final String content = context.getResources()
                .getString(R.string.history_external_incoming_operation_description);

        final Intent intent = OperationDetailActivity.getStartActivityIntent(context, op.getId());

        showNotificationWithDrawable(title, content, intent, R.drawable.detail_bitcoin_logo);
    }

    /**
     * Show a notification for a failed operation.
     */
    public void showOperationFailedNotification(long opId) {
        final String title = context.getResources()
                .getString(R.string.notifications_op_failed_title);

        final String content = context.getResources()
                .getString(R.string.notifications_op_failed_content);

        final Intent intent = OperationDetailActivity.getStartActivityIntent(context, opId);

        showNotificationWithoutBitmap(title, content, intent);
    }

    private void showNotificationWithDrawable(String title,
                                              String content,
                                              Intent intent,
                                              @DrawableRes int drawableId) {

        final RequestBuilder<Bitmap> requestBuilder = Glide.with(context)
                .asBitmap()
                .load(drawableId);

        showNotificationWithBitmapRequest(title, content, intent, requestBuilder);
    }

    private void showNewOperationFromUserNotification(Operation op) {

        final String title = getNotificationContentMessage(op);
        final Intent intent = OperationDetailActivity.getStartActivityIntent(context, op.getId());

        showNotificationFromContact(op.senderProfile, title, op.description, intent);
    }

    private String getNotificationContentMessage(Operation operation) {

        return context.getResources().getString(
                R.string.notifications_amount_received_from,
                operation.senderProfile.firstName,
                MoneyHelper.formatShortMonetaryAmount(
                        operation.amount.inInputCurrency,
                        currencyDisplayModeSel.get()
                )
        );

    }

    /**
     * Shows a notification related to a contact.
     *
     * <p>This must be run in the UI thread.
     */
    private void showNotificationFromContact(PublicProfile contact, String title, String content,
                                             Intent intent) {

        if (contact.profilePictureUrl == null) {
            showNotificationWithDrawable(title, content, intent, R.drawable.avatar_badge_grey);
            return;
        }

        final RequestBuilder<Bitmap> requestBuilder = Glide.with(context)
                .asBitmap()
                .load(contact.profilePictureUrl);

        showNotificationWithBitmapRequest(title, content, intent, requestBuilder);
    }

    private void showNotificationWithBitmapRequest(
            String title,
            String content,
            Intent intent,
            RequestBuilder<Bitmap> requestBuilder) {


        final RequestBuilder<Bitmap> updatedBuilder = requestBuilder
                .apply(RequestOptions.overrideOf(PROFILE_PICTURE_WIDTH, PROFILE_PICTURE_HEIGHT))
                .apply(RequestOptions.circleCropTransform());

        final SimpleTarget<Bitmap> target = new SimpleTarget<Bitmap>(
                NOTIFICATION_IMAGE_WIDTH, NOTIFICATION_IMAGE_HEIGHT
        ) {

            @Override
            public void onResourceReady(
                    @NonNull final Bitmap resource,
                    @Nullable final Transition<? super Bitmap> transition) {

                showNotificationWithBitmap(
                        title,
                        content,
                        resource,
                        intent
                );
            }
        };
        Observable.just(updatedBuilder)
                .compose(executionTransformerFactory.getAsyncExecutor())
                .subscribe(request -> request.into(target));
    }

    private void showNotificationWithoutBitmap(String title, String content, Intent intent) {
        showNotificationWithBitmap(title, content, null, intent);
    }

    private void showNotificationWithBitmap(String title,
                                            String content,
                                            @Nullable Bitmap bitmap,
                                            Intent intent) {

        createMuunNotificationChannel();

        final androidx.core.app.NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, getMuunChannelName())
                        .setSmallIcon(R.drawable.notification_icon)
                        .setContentTitle(title)
                        .setContentText(content)
                        .setAutoCancel(true)
                        .setDefaults(Notification.DEFAULT_SOUND)
                        .setLights(Color.WHITE, 400, 1500)
                        .setVibrate(new long[]{0, 800});

        if (bitmap != null) {
            builder.setLargeIcon(bitmap);
        }

        final int notificationId = computeId(title, content);

        if (intent != null) {

            final PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT
            );

            builder.setContentIntent(pendingIntent);
        }

        final Notification notification = builder.build();

        NotificationManagerCompat
                .from(context)
                .notify(notificationId, notification);
    }

    /**
     * This is an idempotent operation. Nothing will happen if we try to create
     * a notification channel when an identical one already exists.
     */
    private void createMuunNotificationChannel() {

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int importance = NotificationManager.IMPORTANCE_DEFAULT;
            final String channelName = getMuunChannelName();

            // For now we will be only using one channel, so the channelId and channelName will be
            // the same, and the channel name will be the app's name.
            final NotificationChannel channel = new NotificationChannel(
                    channelName, channelName, importance
            );

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            final NotificationManager notificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @NonNull
    private String getMuunChannelName() {
        return context.getString(R.string.notification_channel_name);
    }

    /**
     * Computes a notification id from it's title and content, so every notification is unique.
     */
    private int computeId(String title, String content) {
        final String str = title + content;
        return str.hashCode();
    }

    /**
     * Cancel all previously shown notifications.
     */
    public void cancelAllNotifications() {
        NotificationManagerCompat
                .from(context)
                .cancelAll();
    }
}
