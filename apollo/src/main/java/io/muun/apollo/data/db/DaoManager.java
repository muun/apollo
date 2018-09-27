package io.muun.apollo.data.db;

import io.muun.apollo.data.db.base.BaseDao;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqlbrite.SqlBrite;
import rx.Observable;
import rx.Scheduler;

import java.io.IOException;

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
        final SQLiteOpenHelper databaseHelper = new SQLiteOpenHelper(context, name, null, version) {

            @Override
            public void onCreate(SQLiteDatabase db) {
                createTables(db);
            }

            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                runMigrations(db, oldVersion, newVersion);
            }

            @Override
            public void onOpen(SQLiteDatabase db) {
                super.onOpen(db);
                db.execSQL("PRAGMA foreign_keys=ON");
            }
        };

        this.version = version;
        this.daos = daos;
        this.database = sqlBrite.wrapDatabaseHelper(databaseHelper, scheduler);
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
    public void close() throws IOException {

        database.close();
    }

    private void initializeDaos() {

        for (BaseDao dao: daos) {
            dao.setBriteDb(database);
        }
    }

    private void createTables(SQLiteDatabase db) {

        for (BaseDao dao: daos) {
            dao.createTable(db);
        }
    }

    private void runMigrations(SQLiteDatabase db, int oldVersion, int newVersion) {

        dbMigrationManager.run(db, oldVersion, newVersion);
    }
}
