package io.muun.apollo.data.db;

import io.muun.apollo.Database;

import androidx.sqlite.db.SupportSQLiteDatabase;
import com.squareup.sqldelight.android.AndroidSqliteDriver;
import timber.log.Timber;

public class DbMigrationManager {

    /**
     * This method will be called from the DaoManager to execute the migrations. Do not call this
     * method directly.
     */
    public void run(SupportSQLiteDatabase database, int oldVersion, int newVersion) {
        // We keep this in it's own file so that if we ever do need to add code based migrations
        // the DaoManager file won't get littered by them

        try {
            Database.Companion.getSchema().migrate(
                    new AndroidSqliteDriver(database),
                    oldVersion,
                    newVersion
            );
        } catch (final Exception e) {
            Timber.e(e);
            throw e;
        }
    }
}
