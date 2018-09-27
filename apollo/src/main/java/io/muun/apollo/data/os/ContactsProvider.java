package io.muun.apollo.data.os;

import io.muun.apollo.domain.model.PhoneContact;
import io.muun.common.Optional;
import io.muun.common.model.PhoneNumber;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.content.ContextCompat;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class ContactsProvider {

    private final Context context;

    private final ContentResolver contentResolver;

    private final Optional<String> regionCode;

    /**
     * Constructor.
     */
    @Inject
    public ContactsProvider(Context context, TelephonyInfoProvider telephonyInfoProvider) {
        this.context = context;
        this.contentResolver = context.getContentResolver();
        this.regionCode = telephonyInfoProvider.getRegion();
    }

    /**
     * Read system phone contacts.
     */
    public List<PhoneContact> readPhoneContacts(String defaultAreaCode,
                                                String defaultRegionCode,
                                                long currentTs) {
        if (! canReadContacts()) {
            return new ArrayList<>();
        }

        final Cursor cursor = queryPhoneNumbers();
        final List<PhoneContact> phoneContacts = new ArrayList<>(cursor.getCount());

        while (cursor.moveToNext()) {
            maybeGetContact(cursor, defaultAreaCode, defaultRegionCode, currentTs)
                    .ifPresent(phoneContacts::add);
        }

        return phoneContacts;
    }

    private Optional<PhoneContact> maybeGetContact(Cursor cursor,
                                                   String defaultAreaCode,
                                                   String defaultRegionCode,
                                                   long currentTs) {

        final String contactId = cursor.getString(
                cursor.getColumnIndex(Phone.CONTACT_ID)
        );

        final String name = cursor.getString(
                cursor.getColumnIndex(Phone.DISPLAY_NAME)
        );

        final long lastUpdated = cursor.getLong(
                cursor.getColumnIndex(Phone.CONTACT_LAST_UPDATED_TIMESTAMP)
        );

        String number = cursor.getString(
                cursor.getColumnIndex(Phone.NUMBER)
        );

        try {
            number = normalizePhoneNumber(number, defaultAreaCode, defaultRegionCode);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        final PhoneContact contact = new PhoneContact(
                contactId,
                name,
                number,
                currentTs,
                currentTs,
                lastUpdated
        );

        return Optional.of(contact);
    }

    private Cursor queryPhoneNumbers() {
        final Uri uri = Phone.CONTENT_URI;

        final String[] projection = new String[] {
                Phone._ID,
                Phone.CONTACT_ID,
                Phone.DISPLAY_NAME,
                Phone.NUMBER,
                Phone.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        final String order = Phone.CONTACT_LAST_UPDATED_TIMESTAMP + " DESC";

        return contentResolver.query(uri, projection, null, null, order);
    }

    /**
     * Observe the ContactsContract for changed contact URIs (emits an initial notification).
     */
    public Observable<String> watchContactChanges() {
        final Uri rootUri = Phone.CONTENT_URI;

        return Observable.using(
                () -> new SubscriberContentObserver(),

                contentObserver -> Observable.create((Observable.OnSubscribe<String>)
                    (subscriber) -> {
                        contentObserver.setSubscriber(subscriber);
                        subscriber.onNext(rootUri.toString());

                        contentResolver.registerContentObserver(rootUri, true, contentObserver);
                    }
                ),

                contentObserver -> contentResolver.unregisterContentObserver(contentObserver)
        );
    }

    private String normalizePhoneNumber(String number,
                                        String defaultAreaCode,
                                        String defaultRegionCode)
            throws IllegalArgumentException {

        PhoneNumber phoneNumber;

        try {
            phoneNumber = new PhoneNumber(number, regionCode.orElse(defaultRegionCode));
        } catch (IllegalArgumentException e) {
            phoneNumber = new PhoneNumber(
                    defaultAreaCode + number,
                    regionCode.orElse(defaultRegionCode)
            );
        }

        return phoneNumber.toE164String();
    }

    private boolean canReadContacts() {
        final int grantResult = ContextCompat
                .checkSelfPermission(context, Manifest.permission.READ_CONTACTS);

        return (grantResult == PackageManager.PERMISSION_GRANTED);
    }
}
