package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.DatabaseError;
import io.muun.apollo.domain.model.base.PersistentModel;
import io.muun.common.Optional;

import android.database.Cursor;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.squareup.sqlbrite3.BriteDatabase;
import com.squareup.sqldelight.prerelease.SqlDelightQuery;
import com.squareup.sqldelight.prerelease.SqlDelightStatement;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.BackpressureStrategy;
import io.reactivex.functions.Function;
import rx.Observable;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Func2;
import timber.log.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.validation.constraints.NotNull;

// TODO: make insert/delete/update/store return values non-observable
// TODO: make non-observable versions of the fetch methods
public class BaseDao<ModelT extends PersistentModel> {

    protected BriteDatabase briteDb;
    protected SupportSQLiteDatabase db;

    protected final String tableName;

    private final String createTableSql;

    private final Func2<SupportSQLiteDatabase, ModelT, SqlDelightStatement> inputMapper;

    private final Function<Cursor, ModelT> outputMapper;

    /**
     * Constructor.
     */
    protected BaseDao(
            String createTableSql,
            Func2<SupportSQLiteDatabase, ModelT, SqlDelightStatement> inputMapper,
            Function<Cursor, ModelT> outputMapper,
            String tableName) {

        this.tableName = tableName;
        this.createTableSql = createTableSql;
        this.inputMapper = inputMapper;
        this.outputMapper = outputMapper;
    }

    /**
     * This method will be called from the DaoManager to set the database. You should not call this
     * method directly.
     */
    public void setBriteDb(BriteDatabase briteDatabase) {
        this.briteDb = briteDatabase;
        this.db = briteDatabase.getWritableDatabase();
    }

    /**
     * This method will be called from the DaoManager to create the table for this dao. You should
     * not call this method directly.
     */
    public void createTable(SupportSQLiteDatabase database) {
        database.execSQL(createTableSql);
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
     * @return A deferred observable with the number of deleted rows.
     */
    public Observable<Integer> deleteAll() {
        return delete(null);
    }

    /**
     * Delete the rows that match a given query.
     *
     * @return A deferred observable with the number of deleted rows.
     */
    protected Observable<Integer> delete(@Nullable final String whereClause,
                                         @Nullable final String... whereArgs) {
        return Observable.defer(() ->
                Observable.just(briteDb.delete(tableName, whereClause, whereArgs))
        ).onErrorResumeNext(this::wrapError);
    }

    /**
     * Runs the statement returning an observable with a list of results, and re-emitting if the
     * result set changes.
     */
    protected Observable<List<ModelT>> fetchList(@NotNull SqlDelightQuery query) {
        final io.reactivex.Observable<List<ModelT>> v2Observable = briteDb
                .createQuery(query.getTables(), query)
                .mapToList(outputMapper)
                .onErrorResumeNext(error -> {
                    return RxJavaInterop.toV2Observable(wrapError(error));
                });

        return RxJavaInterop.toV1Observable(v2Observable, BackpressureStrategy.ERROR);
    }

    /**
     * Runs the statement returning a list of results, without an ongoing subscription. Safe to call
     * inside transactions.
     */
    protected List<ModelT> fetchListOnce(@NotNull SqlDelightQuery query) {
        final List<ModelT> results = new ArrayList<>();

        try (Cursor cursor = briteDb.query(query)) {
            while (cursor.moveToNext()) {
                results.add(outputMapper.apply(cursor));
            }
        } catch (Exception e) {
            // TODO handle?
            Timber.e(e);
        }

        return results;
    }

    /**
     * Runs the statement which will emit the only expected result, re-emitting when it changes. The
     * observable will emit an error if no element is found.
     */
    protected Observable<ModelT> fetchOneOrFail(@NotNull SqlDelightQuery query) {

        final ElementNotFoundException elementNotFoundException = new ElementNotFoundException(
                query.getSql()
        );

        final io.reactivex.Observable<ModelT> v2Observable = briteDb
                .createQuery(query.getTables(), query)
                .mapToOneOrDefault(getCursorOptionalFunction(), Optional.empty())
                .flatMap(maybeElement -> {

                    if (!maybeElement.isPresent()) {
                        return io.reactivex.Observable.error(
                                OnErrorThrowable.from(elementNotFoundException)
                        );
                    }

                    return io.reactivex.Observable.just(maybeElement.get());
                });

        return RxJavaInterop.toV1Observable(v2Observable, BackpressureStrategy.ERROR);
    }

    /**
     * Somewhat convoluted hack to avoid QueryObseravle#mapToOneOrDefault's "defaultValue can't be
     * null" restriction AND our inability to use QueryObseravle#mapToOptional version (requires
     * Android's version > 24).
     */
    private Function<Cursor, Optional<ModelT>> getCursorOptionalFunction() {
        return cursor -> Optional.ofNullable(outputMapper.apply(cursor));
    }

    /**
     * Stores an entity in the database, returning a single-element observable of the saved entity.
     *
     * @return A deferred observable with an element that has an id.
     */
    public Observable<ModelT> store(@NotNull ModelT element) {

        final SqlDelightStatement statement = inputMapper.call(db, element);

        final Long id = element.getId();

        if (id != null) {

            return Observable.defer(() -> Observable.just(
                    briteDb.executeUpdateDelete(statement.getTable(), statement)
            )).onErrorResumeNext(this::wrapError)
                    .map(alteredRowsCount -> {

                        if (alteredRowsCount == 0) {
                            throw new NoSuchElementException("Entity with id " + id + " not found");
                        }

                        if (alteredRowsCount > 1) {
                            throw new IllegalStateException(
                                    "More than one entity updated with id " + id);
                        }

                        return element;
                    });
        }

        // Make sure the auto increment id is not set to anything
        statement.bindNull(1);

        return Observable.defer(() -> Observable.just(
                briteDb.executeInsert(statement.getTable(), statement)
        )).onErrorResumeNext(this::wrapError)
                .map(rowId -> {

                    if (rowId == -1) {
                        throw new RuntimeException("Error while inserting new value in the db");
                    }

                    element.setId(rowId);
                    return element;
                })
                .onErrorResumeNext(this::wrapError);
    }

    protected <T> Observable<T> wrapError(Throwable error) {
        return Observable.error(new DatabaseError("Error on " + getClass().getSimpleName(), error));
    }

    protected void executeInsert(SqlDelightStatement compiledStatement) {
        briteDb.executeInsert(compiledStatement.getTable(), compiledStatement);
    }

    protected void executeUpdate(SqlDelightStatement compiledStatement) {
        briteDb.executeUpdateDelete(compiledStatement.getTable(), compiledStatement);
    }

    protected void executeDelete(SqlDelightStatement compiledStatement) {
        briteDb.executeUpdateDelete(compiledStatement.getTable(), compiledStatement);
    }

    protected void enhanceError(Throwable error, String... args) {
        if (error instanceof ElementNotFoundException) {
            ((ElementNotFoundException) error).setArgs(args);
        }
    }
}