package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.model.base.HoustonUuidModel;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;
import io.reactivex.functions.Function;
import rx.Observable;
import rx.functions.Func2;

public class HoustonUuidDao<ModelT extends HoustonUuidModel> extends BaseDao<ModelT> {

    protected HoustonUuidDao(
            String createTableSql,
            Func2<SupportSQLiteDatabase, ModelT, SqlDelightStatement> inputMapper,
            Function<Cursor, ModelT> outputMapper,
            String tableName) {

        super(createTableSql, inputMapper, outputMapper, tableName);
    }

    @Override
    public Observable<ModelT> store(ModelT element) {

        return Observable.defer(() -> {

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
