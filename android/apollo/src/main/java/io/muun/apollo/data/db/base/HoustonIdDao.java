package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.model.base.HoustonIdModel;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;
import io.reactivex.functions.Function;
import rx.Observable;
import rx.functions.Func2;

import javax.validation.constraints.NotNull;

public abstract class HoustonIdDao<ModelT extends HoustonIdModel> extends BaseDao<ModelT> {

    protected HoustonIdDao(
            String createTableSql,
            Func2<SupportSQLiteDatabase, ModelT, SqlDelightStatement> inputMapper,
            Function<Cursor, ModelT> outputMapper,
            String tableName) {

        super(createTableSql, inputMapper, outputMapper, tableName);
    }

    @Override
    public Observable<ModelT> store(@NotNull ModelT element) {

        return Observable.defer(() -> {

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
