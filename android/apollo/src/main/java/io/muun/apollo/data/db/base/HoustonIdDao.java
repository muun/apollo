package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.model.base.HoustonIdModel;
import io.muun.apollo.lib.BuildConfig;
import io.muun.common.utils.Preconditions;

import android.database.Cursor;
import rx.Observable;

import javax.validation.constraints.NotNull;

public abstract class HoustonIdDao<ModelT extends HoustonIdModel> extends BaseDao<ModelT> {

    protected HoustonIdDao(final String tableName) {
        super(tableName);
    }

    @Override
    public Observable<ModelT> store(@NotNull ModelT element) {

        return Observable.defer(() -> {

            if (BuildConfig.DEBUG) {
                Preconditions.checkNotNull(
                        element.getHid(),
                        "Trying to store model with no UUID"
                );
            }

            if (element.getId() != null) {
                return super.store(element);
            }

            final Cursor cursor = briteDb.query(
                    "select id from " + tableName + " where hid = ?",
                    String.valueOf(element.getHid())
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
                            "More than one entity with a single hid " + element.getHid()
                                    + " found in table " + tableName
                    )
            );
        });
    }
}
