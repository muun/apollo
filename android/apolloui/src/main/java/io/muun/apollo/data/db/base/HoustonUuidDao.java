package io.muun.apollo.data.db.base;

import io.muun.apollo.data.external.Globals;
import io.muun.apollo.domain.model.base.HoustonUuidModel;
import io.muun.common.utils.Preconditions;

import android.database.Cursor;
import androidx.annotation.NonNull;
import rx.Observable;

public abstract class HoustonUuidDao<ModelT extends HoustonUuidModel> extends BaseDao<ModelT> {

    protected HoustonUuidDao(final String tableName) {
        super(tableName);
    }

    @Override
    public Observable<ModelT> store(@NonNull ModelT element) {

        return Observable.defer(() -> {

            if (Globals.INSTANCE.isDebug()) {
                Preconditions.checkNotNull(
                        element.houstonUuid,
                        "Trying to store model with no UUID"
                );
            }

            if (element.getId() != null) {
                return super.store(element);
            }

            final Cursor cursor = briteDb.query(
                    "select id from " + tableName + " where houston_uuid = ?",
                    String.valueOf(element.houstonUuid)
            );

            if (cursor.getCount() == 0) {
                return super.store(element);
            }

            if (cursor.getCount() == 1) {
                cursor.moveToFirst();
                element.setId(cursor.getLong(cursor.getColumnIndex("id")));
                return super.store(element);
            }

            return Observable.error(
                    new IllegalStateException(
                            "More than one entity with a single houston uuid " + element.houstonUuid
                                    + " found in table " + tableName
                    )
            );
        });
    }
}
