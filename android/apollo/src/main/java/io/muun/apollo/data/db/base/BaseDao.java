package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.DatabaseError;
import io.muun.apollo.domain.model.base.PersistentModel;
import io.muun.apollo.lib.Database;
import io.muun.common.Optional;
import io.muun.common.utils.Preconditions;

import android.database.Cursor;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.squareup.sqlbrite3.BriteDatabase;
import com.squareup.sqldelight.Query;
import com.squareup.sqldelight.runtime.rx.RxQuery;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Scheduler;
import rx.Completable;
import rx.Observable;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Action0;

import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;

public abstract class BaseDao<ModelT extends PersistentModel> {

    protected final String tableName;

    protected BriteDatabase briteDb;

    protected SupportSQLiteDatabase db;

    protected Database delightDb;

    protected Scheduler scheduler;

    /**
     * Constructor.
     */
    protected BaseDao(final String tableName) {
        this.tableName = tableName;
    }

    /**
     * This method will be called from the DaoManager to set the database. You should not call this
     * method directly.
     */
    public void setDb(BriteDatabase briteDatabase, Database delightDb, final Scheduler scheduler) {
        this.briteDb = briteDatabase;
        this.db = briteDatabase.getWritableDatabase();
        this.delightDb = delightDb;
        this.scheduler = scheduler;


        try (Cursor cursor = briteDatabase.query("SELECT COUNT(*) FROM " + tableName)) {
            Preconditions.checkState(
                    cursor.getCount() == 1,
                    "expected table " + tableName + " to exist"
            );
        }
    }

    /**
     * Create a new Transaction. Don't forget to commit your changes by marking the transaction as
     * successful or rollback your changes.
     *
     * @return New transaction.
     */
    protected BriteDatabase.Transaction newTransaction() {
        return briteDb.newTransaction();
    }

    /**
     * Deletes all rows from a table.
     *
     * @return A deferred completable.
     */
    public abstract Completable deleteAll();

    /**
     * Runs the statement returning an observable with a list of results, and re-emitting if the
     * result set changes.
     */
    protected Observable<List<ModelT>> fetchList(@NotNull Query<ModelT> query) {

        final io.reactivex.Observable<List<ModelT>> v2Observable =
                RxQuery.toObservable(query, scheduler)
                        .map(Query::executeAsList)
                        .onErrorResumeNext(error -> {
                            return RxJavaInterop.toV2Observable(wrapError(error));
                        });

        return RxJavaInterop.toV1Observable(v2Observable, BackpressureStrategy.ERROR);
    }

    /**
     * Runs the statement which will emit the only expected result, re-emitting when it changes. The
     * observable will emit an error if no element is found.
     */
    protected Observable<ModelT> fetchOneOrFail(@NotNull Query<ModelT> query) {

        final ElementNotFoundException elementNotFoundException = new ElementNotFoundException(
                query
        );

        final io.reactivex.Observable<ModelT> v2Observable =
                RxQuery.toObservable(query, scheduler)
                        .map(this::mapQueryToOptional)
                        .flatMap(maybeElement -> {

                            if (maybeElement.isPresent()) {
                                return io.reactivex.Observable.just(maybeElement.get());

                            } else {

                                return io.reactivex.Observable.error(
                                        OnErrorThrowable.from(elementNotFoundException)
                                );
                            }

                        });

        return RxJavaInterop.toV1Observable(v2Observable, BackpressureStrategy.ERROR);
    }

    /**
     * Runs the statement which will emit the only expected result, re-emitting when it changes. If
     * no element is found, an empty Optional is returned.
     */
    protected Observable<Optional<ModelT>> fetchMaybeOne(@NotNull Query<ModelT> query) {

        final io.reactivex.Observable<Optional<ModelT>> v2Observable =
                RxQuery.toObservable(query, scheduler)
                        .map(this::mapQueryToOptional);

        return RxJavaInterop.toV1Observable(v2Observable, BackpressureStrategy.ERROR);
    }

    private Optional<ModelT> mapQueryToOptional(final Query<ModelT> query) {
        return Optional.ofNullable(query.executeAsOneOrNull());
    }

    /**
     * Directly store an element to the backing database. Not intended to be called directly since
     * this does not check for any of the currently held invariants by {@link HoustonIdDao} or
     * {@link HuostonUuidDao}. Incorrect calls might result in duplicated entries.
     */
    protected abstract void storeUnsafe(@Nonnull ModelT element);

    /**
     * Stores an entity in the database, returning a single-element observable of the saved entity.
     *
     * @return A deferred observable with an element that has an id.
     */
    public Observable<ModelT> store(@Nonnull ModelT element) {
        final Long id = element.getId();

        if (id != null) {
            final Action0 update = () -> {
                try (final BriteDatabase.Transaction tx = briteDb.newNonExclusiveTransaction()) {
                    storeUnsafe(element);

                    final Cursor cursor = briteDb.query(
                            "SELECT COUNT(*) FROM " + tableName + " WHERE id = " + id
                    );
                    if (cursor.getCount() == 0) {
                        throw new NoSuchElementException("Entity with id " + id + " not found");
                    }

                    Preconditions.checkState(cursor.moveToFirst());
                    if (cursor.getLong(0) > 1) {
                        throw new IllegalStateException(
                                "More than one entity updated with id " + id);
                    }

                    tx.markSuccessful();
                }
            };

            return Completable.fromAction(update)
                    .andThen(Observable.just(element))
                    .onErrorResumeNext(this::wrapError);
        }

        return Observable.defer(() -> {
            storeUnsafe(element);

            // FIXME: This is a racy operation. If another thread inserts any row, the id
            // won't match.
            final long insertedId;
            try (Cursor query = briteDb.query("SELECT last_insert_rowid();")) {
                Preconditions.checkState(query.moveToFirst());
                insertedId = query.getLong(0);
            }
            if (insertedId < 0) {
                throw new RuntimeException("Error while inserting new value in the db");
            }

            element.setId(insertedId);

            return Observable.just(element);
        })
        .onErrorResumeNext(this::wrapError);
    }

    protected <T> Observable<T> wrapError(Throwable error) {
        return Observable.error(new DatabaseError("Error on " + getClass().getSimpleName(), error));
    }

    protected void enhanceError(Throwable error, String... args) {
        if (error instanceof ElementNotFoundException) {
            ((ElementNotFoundException) error).setArgs(args);
        }
    }

    protected Observable<Long> executeCount(final Query<Long> query) {
        final io.reactivex.Observable<Long> observable =
                RxQuery.toObservable(query, scheduler)
                        .map(Query::executeAsOneOrNull)
                        .map(l -> l == null ? 0 : l)
                        .onErrorResumeNext(error -> {
                            return RxJavaInterop.toV2Observable(wrapError(error));
                        });

        return RxJavaInterop.toV1Observable(observable, BackpressureStrategy.ERROR);
    }
}