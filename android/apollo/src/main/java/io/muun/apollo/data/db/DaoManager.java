package io.muun.apollo.data.db;

import io.muun.apollo.data.db.base.BaseDao;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import com.squareup.sqlbrite3.BriteDatabase;
import com.squareup.sqlbrite3.SqlBrite;
import io.reactivex.Scheduler;
import rx.Observable;

public class DaoManager {

    private final BaseDao[] daos;

    private final int version;

    private final BriteDatabase database;

    private final DbMigrationManager dbMigrationManager = new DbMigrationManager();

    /**
     * Constructor.
     */
    public DaoManager(
            Context context,
            String name,
            int version,
            Scheduler scheduler,
            BaseDao... daos) {

        final SqlBrite sqlBrite = new SqlBrite.Builder().build();

        final SupportSQLiteOpenHelper.Callback callback = new SupportSQLiteOpenHelper
                .Callback(version) {

            @Override
            public void onCreate(SupportSQLiteDatabase db) {
                createTables(db);
            }

            @Override
            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                runMigrations(db, oldVersion, newVersion);
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
                .flatMap(BaseDao::deleteAll)
                .toBlocking()
                .last();
    }

    /**
     * Close the database.
     */
    public void close() {
        database.close();
    }

    private void initializeDaos() {
        for (BaseDao dao : daos) {
            dao.setBriteDb(database);
        }
    }

    private void createTables(SupportSQLiteDatabase db) {
        for (BaseDao dao : daos) {
            dao.createTable(db);
        }
    }

    private void runMigrations(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
        dbMigrationManager.run(db, oldVersion, newVersion);
    }
}
