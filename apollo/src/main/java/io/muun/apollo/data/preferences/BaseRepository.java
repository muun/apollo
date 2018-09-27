package io.muun.apollo.data.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import com.f2prateek.rx.preferences.RxSharedPreferences;

public abstract class BaseRepository {

    protected final RxSharedPreferences rxSharedPreferences;

    private final SharedPreferences sharedPreferences;

    /**
     * Creates a base preferences repository.
     */
    public BaseRepository(Context context) {
        sharedPreferences = context.getSharedPreferences(
                getFileName(),
                Context.MODE_PRIVATE
        );

        rxSharedPreferences = RxSharedPreferences.create(sharedPreferences);
    }

    protected abstract String getFileName();

    /**
     * Clears the repository.
     */
    public void clear() {
        sharedPreferences.edit()
                .clear()
                .commit();
    }

}
