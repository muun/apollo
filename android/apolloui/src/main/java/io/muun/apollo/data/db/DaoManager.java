package io.muun.apollo.data.db;

import io.muun.apollo.Database;
import io.muun.apollo.data.db.base.Adapters;
import io.muun.apollo.data.db.base.BaseDao;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import com.squareup.sqlbrite3.BriteDatabase;
import com.squareup.sqlbrite3.SqlBrite;
import com.squareup.sqldelight.android.AndroidSqliteDriver;
import io.reactivex.Scheduler;
import rx.Observable;

public class DaoManager {

    private final BaseDao[] daos;

    private final int version;

    private final BriteDatabase database;

    private final DbMigrationManager dbMigrationManager = new DbMigrationManager();

    private final Database delightDb;

    private final Scheduler scheduler;

    /**
     * Constructor.
     */
    public DaoManager(
            Context context,
            String name,
            int version,
            Scheduler scheduler,
            BaseDao... daos
    ) {

        final SqlBrite sqlBrite = new SqlBrite.Builder().build();

        final SupportSQLiteOpenHelper.Callback callback = new SupportSQLiteOpenHelper
                .Callback(version) {

            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                // This executes all create statements
                Database.Companion.getSchema().create(new AndroidSqliteDriver(db));
            }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                runMigrations(
                        db,
                        oldVersion,
                        newVersion
                );
            }

            @Override
            public void onOpen(SupportSQLiteDatabase db) {
                super.onOpen(db);
                db.execSQL("PRAGMA foreign_keys=ON");
            }
        };

        final SupportSQLiteOpenHelper.Configuration conf = SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(name)
                .callback(callback)
                .build();

        final SupportSQLiteOpenHelper helper = new FrameworkSQLiteOpenHelperFactory().create(conf);

        this.version = version;
        this.daos = daos;
        this.database = sqlBrite.wrapDatabaseHelper(helper, scheduler);
        this.database.setLoggingEnabled(true);
        this.delightDb = Database.Companion.invoke(
                new AndroidSqliteDriver(helper),
                Adapters.OPERATIONS,
                Adapters.SUBMARINE_SWAPS
        );
        this.scheduler = scheduler;

        initializeDaos();
    }

    /**
     * Get the database version.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Delete the content from all daos.
     */
    public void delete() {
        Observable.from(daos)
                .flatMapCompletable(BaseDao::deleteAll)
                .toBlocking()
                .subscribe();
    }

    /**
     * Close the database.
     */
    public void close() {
        database.close();
    }

    private void initializeDaos() {
        for (BaseDao dao : daos) {
            dao.setDb(database, delightDb, scheduler);
        }
    }

    private void runMigrations(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        dbMigrationManager.run(db, oldVersion, newVersion);
    }
}
