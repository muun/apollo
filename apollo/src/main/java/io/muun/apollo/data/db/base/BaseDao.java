package io.muun.apollo.data.db.base;

import io.muun.apollo.domain.errors.DatabaseError;
import io.muun.apollo.domain.model.base.PersistentModel;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;
import com.squareup.sqlbrite.BriteDatabase;
import com.squareup.sqldelight.SqlDelightCompiledStatement;
import com.squareup.sqldelight.SqlDelightStatement;
import rx.Observable;
import rx.exceptions.OnErrorThrowable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import javax.validation.constraints.NotNull;

// TODO: make insert/delete/update/store return values non-observable
// TODO: make non-observable versions of the fetch methods
// TODO: migrate from the store method to inserting compiled statements
public class BaseDao<ModelT extends PersistentModel> {

    protected BriteDatabase briteDb;
    protected SQLiteDatabase db;

    protected final String tableName;

    private final String createTableSql;

    private final Func1<ModelT, ContentValues> inputMapper;

    private final Func1<Cursor, ModelT> outputMapper;

    /**
     * Constructor.
     */
    protected BaseDao(
            String createTableSql,
            Func1<ModelT, ContentValues> inputMapper,
            Func1<Cursor, ModelT> outputMapper,
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
    public void createTable(SQLiteDatabase database) {

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
     * Insert a row into the given table.
     *
     * @return A deferred observable with the row Id of the new inserted row.
     */
    protected Observable<Long> insert(@NotNull final ContentValues contentValues) {

        return insert(contentValues, SQLiteDatabase.CONFLICT_NONE);
    }

    /**
     * Insert a row into the given table.
     *
     * @return A deferred observable with the row Id of the new inserted row.
     */
    protected Observable<Long> insert(@NotNull final ContentValues contentValues,
                                      final int conflictAlgorithm) {

        return Observable.defer(() ->
                Observable.just(briteDb.insert(tableName, contentValues, conflictAlgorithm))
        ).onErrorResumeNext(this::wrapError);
    }

    /**
     * Update the rows that match a given query.
     *
     * @return A deferred observable containing the number of rows that have been changed by this
     *     update.
     */
    protected Observable<Integer> update(@NotNull final ContentValues values,
                                         @Nullable final String whereClause,
                                         @Nullable final String... whereArgs) {

        return update(values, SQLiteDatabase.CONFLICT_NONE, whereClause, whereArgs);
    }

    /**
     * Update the rows that match a given query.
     *
     * @return A deferred observable containing the number of rows that have been changed by this
     *     update.
     */
    protected Observable<Integer> update(@NotNull final ContentValues values,
                                         final int conflictAlgorithm,
                                         @Nullable final String whereClause,
                                         @Nullable final String... whereArgs) {

        return Observable.defer(() -> Observable.just(
                briteDb.update(tableName, values, conflictAlgorithm, whereClause, whereArgs)
        )).onErrorResumeNext(this::wrapError);
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
    protected Observable<List<ModelT>> fetchList(@NotNull SqlDelightStatement query) {

        return briteDb.createQuery(query.tables, query.statement, query.args)
                .mapToList(outputMapper)
                .onErrorResumeNext(this::wrapError);
    }

    /**
     * Runs the statement returning a list of results, without an ongoing subscription. Safe to call
     * inside transactions.
     */
    protected List<ModelT> fetchListOnce(@NotNull SqlDelightStatement query) {
        final List<ModelT> results = new ArrayList<>();

        try (Cursor cursor = briteDb.query(query.statement, query.args)) {
            while (cursor.moveToNext()) {
                results.add(outputMapper.call(cursor));
            }
        }

        return results;
    }

    /**
     * Runs the statement which will emit the only expected result, re-emitting when it changes. The
     * observable will emit an error if no element is found.
     */
    protected Observable<ModelT> fetchOneOrFail(@NotNull SqlDelightStatement query) {

        final ElementNotFoundException elementNotFoundException = new ElementNotFoundException(
                "Expected unique result for query not found. Statement: " + query.statement
                        + "; Arguments: " + Arrays.toString(query.args)
        );

        return briteDb.createQuery(query.tables, query.statement, query.args)
                .mapToOneOrDefault(outputMapper, null)
                .flatMap(element -> {

                    if (element == null) {
                        return Observable.error(OnErrorThrowable.from(elementNotFoundException));
                    }

                    return Observable.just(element);
                });
    }

    /**
     * Stores an entity in the database, returning a single-element observable of the saved entity.
     *
     * @return A deferred observable with an element that has an id.
     */
    public Observable<ModelT> store(@NotNull ModelT element) {

        final ContentValues values = inputMapper.call(element);

        final Long id = element.getId();

        if (id != null) {

            return update(values, "id = ?", String.valueOf(id))
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

        values.putNull("id");

        return insert(values, SQLiteDatabase.CONFLICT_REPLACE)
                .map(rowId -> {

                    if (rowId == -1) {
                        throw new RuntimeException("Error while inserting new value in the db");
                    }

                    element.setId(rowId);
                    return element;
                })
                .onErrorResumeNext(this::wrapError);
    }

    /**
     * Stores a list of entities in the database, returning a single-element observable of list with
     * the saved entities.
     *
     * @return A deferred observable with a list of elements that have id.
     */
    public Observable<List<ModelT>> storeList(@NotNull List<ModelT> elements) {

        return Observable.defer(() -> {

            final BriteDatabase.Transaction transaction = newTransaction();

            return Observable.from(elements)
                    .concatMap(this::store)
                    .toList()
                    .doOnNext(ignored -> {
                        transaction.markSuccessful();
                        transaction.close();
                    })
                    .doOnError(error -> transaction.close());
        }).onErrorResumeNext(this::wrapError);
    }

    /**
     * Execute a compiled statement and trigger updates to the relevant tables.
     */
    protected void executeStatement(SqlDelightCompiledStatement.Update compiledStatement) {

        briteDb.executeUpdateDelete(compiledStatement.table, compiledStatement.program);
    }

    /**
     * Execute a compiled statement and trigger updates to the relevant tables.
     */
    protected void executeStatement(SqlDelightCompiledStatement.Delete compiledStatement) {

        briteDb.executeUpdateDelete(compiledStatement.table, compiledStatement.program);
    }

    /**
     * Execute a compiled statement and trigger updates to the relevant tables.
     */
    protected void executeStatement(SqlDelightCompiledStatement.Insert compiledStatement) {

        briteDb.executeInsert(compiledStatement.table, compiledStatement.program);
    }

    protected <T> Observable<T> wrapError(Throwable error) {
        return Observable.error(new DatabaseError("Error on " + getClass().getSimpleName(), error));
    }
}