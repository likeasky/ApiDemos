package com.example.android.apis.app;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import java.util.HashMap;

/**
 * Created by nobody on 2015-01-21.
 */
public class LoaderThrottle extends Activity {

    static final String TAG = "LoaderThrottle";

    public static final String AUTHORITY = "com.example.android.apis.app.LoaderThrottle";

    public static final class MainTable implements BaseColumns {

        private MainTable() {
            Log.d(TAG, "MainTable()");

        }

        public static final String TABLE_NAME = "main";

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/main");

        public static final Uri CONTENT_ID_URI_BASE
                = Uri.parse("content://" + AUTHORITY + "/main/");

        public static final String CONTENT_TYPE
                = "vnd.android.cursor.dir/vnd.example.api-demos-throttle";

        public static final String CONTENT_ITEM_TYPE
                = "vnd.android.cursor.item/vnd.example.api-demos-throttle";

        public static final String DEFAULT_SORT_ORDER = "data COLLATE LOCALIZED ASC";

        public static final String COLUMN_NAME_DATA = "data";
    }

    static class DatabaseHelper extends SQLiteOpenHelper {

        private static final String DATABASE_NAME = "loader_throttle.db";
        private static final int DATABASE_VERSION = 2;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            Log.d(TAG, "DatabaseHelper()");
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            Log.d(TAG, "DatabaseHelper.onCreate()");
            db.execSQL("CREATE TABLE " + MainTable.TABLE_NAME + " ("
                    + MainTable._ID + " INTEGER PRIMARY KEY,"
                    + MainTable.COLUMN_NAME_DATA + " TEXT"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

            Log.w(TAG, "Upgradeing database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");

            db.execSQL("DROP TABLE IF EXISTS notes");

            onCreate(db);
        }
    }

    public static class SimpleProvider extends ContentProvider {
        private final HashMap<String, String> mNotesProjectionMap;
        private final UriMatcher mUriMatcher;

        private static final int MAIN = 1;
        private static final int MAIN_ID = 2;

        private DatabaseHelper mOpenHelper;

        public SimpleProvider() {
            Log.d(TAG, "SimpleProvider()");
            mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
            mUriMatcher.addURI(AUTHORITY, MainTable.TABLE_NAME, MAIN);
            mUriMatcher.addURI(AUTHORITY, MainTable.TABLE_NAME + "/#", MAIN_ID);

            mNotesProjectionMap = new HashMap<String, String>();
            mNotesProjectionMap.put(MainTable._ID, MainTable._ID);
            mNotesProjectionMap.put(MainTable.COLUMN_NAME_DATA, MainTable.COLUMN_NAME_DATA);
        }

        @Override
        public boolean onCreate() {
            Log.d(TAG, "SimpleProvider.onCreate()");
            mOpenHelper = new DatabaseHelper(getContext());
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                            String[] selectionArgs, String sortOrder) {
            Log.d(TAG, "SimpleProvider.query()");

            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables(MainTable.TABLE_NAME);

            switch (mUriMatcher.match(uri)) {
                case MAIN:
                    qb.setProjectionMap(mNotesProjectionMap);
                    break;

                case MAIN_ID:
                    qb.setProjectionMap(mNotesProjectionMap);
                    qb.appendWhere(MainTable._ID + "=?");
                    selectionArgs = DatabaseUtils.appendSelectionArgs(selectionArgs,
                            new String[] {uri.getLastPathSegment()});
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }

            if (TextUtils.isEmpty(sortOrder)) {
                sortOrder = MainTable.DEFAULT_SORT_ORDER;
            }

            SQLiteDatabase db = mOpenHelper.getReadableDatabase();

            Cursor c = qb.query(db, projection, selection, selectionArgs,
                    null, null, sortOrder);

            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        }

        @Override
        public String getType(Uri uri) {
            Log.d(TAG, "SimpleProvider.getType()");
            switch (mUriMatcher.match(uri)) {
                case MAIN:
                    return MainTable.CONTENT_TYPE;
                case MAIN_ID:
                    return MainTable.CONTENT_ITEM_TYPE;
                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }

        @Override
        public Uri insert(Uri uri, ContentValues initialValues) {
            Log.d(TAG, "SimpleProvider.insert()");
            if (mUriMatcher.match(uri) != MAIN) {
                throw new IllegalArgumentException("Unknown URI " + uri);
            }

            ContentValues values;

            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }

            if (values.containsKey(MainTable.COLUMN_NAME_DATA) == false) {
                values.put(MainTable.COLUMN_NAME_DATA, "");
            }

            SQLiteDatabase db = mOpenHelper.getWritableDatabase();

            long rowId = db.insert(MainTable.TABLE_NAME, null, values);

            if (rowId > 0) {
                Uri noteUri = ContentUris.withAppendedId(MainTable.CONTENT_ID_URI_BASE, rowId);
                getContext().getContentResolver().notifyChange(noteUri, null);
                return noteUri;
            }

            throw new SQLException("Failed to insert row into " + uri);
        }

        @Override
        public int delete(Uri uri, String where, String[] whereArgs) {
            Log.d(TAG, "SimpleProvider.delete()");
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            String finalWhere;

            int count;

            switch (mUriMatcher.match(uri)) {
                case MAIN:
                    count = db.delete(MainTable.TABLE_NAME, where, whereArgs);
                    break;

                case MAIN_ID:
                    finalWhere = DatabaseUtils.concatenateWhere(
                            MainTable._ID + " = " + ContentUris.parseId(uri), where);
                    count = db.delete(MainTable.TABLE_NAME, finalWhere, whereArgs);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }

            getContext().getContentResolver().notifyChange(uri, null);

            return count;
        }

        @Override
        public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
            Log.d(TAG, "SimpleProvider.update()");
            SQLiteDatabase db = mOpenHelper.getWritableDatabase();
            int count;
            String finalWhere;

            switch (mUriMatcher.match(uri)) {
                case MAIN:
                    count = db.update(MainTable.TABLE_NAME, values, where, whereArgs);
                    break;

                case MAIN_ID:
                    finalWhere = DatabaseUtils.concatenateWhere(
                            MainTable._ID + " = " + ContentUris.parseId(uri), where);
                    count = db.update(MainTable.TABLE_NAME, values, finalWhere, whereArgs);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);

            }

            getContext().getContentResolver().notifyChange(uri, null);

            return count;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate()");
        super.onCreate(savedInstanceState);

        FragmentManager fm = getFragmentManager();

        if (fm.findFragmentById(android.R.id.content) == null) {
            ThrottledLoaderListFragment list = new ThrottledLoaderListFragment();
            fm.beginTransaction().add(android.R.id.content, list).commit();
        }
    }

    public static class ThrottledLoaderListFragment extends ListFragment
            implements LoaderManager.LoaderCallbacks<Cursor> {

        static final int POPULATE_ID = Menu.FIRST;
        static final int CLEAR_ID = Menu.FIRST + 1;

        SimpleCursorAdapter mAdapter;

        String mCurFilter;

        AsyncTask<Void, Void, Void> mPopulatingTask;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            Log.d(TAG, "ThrottledLoaderListFragment.onActivityCreated()");
            super.onActivityCreated(savedInstanceState);

            setEmptyText("No Data. Select 'Populate' to fill with data from Z to A at a rate of 4 per second.");
            setHasOptionsMenu(true);

            mAdapter = new SimpleCursorAdapter(getActivity(),
                    android.R.layout.simple_list_item_1, null,
                    new String[] {MainTable.COLUMN_NAME_DATA},
                    new int[] {android.R.id.text1}, 0);
            setListAdapter(mAdapter);

            setListShown(false);

            getLoaderManager().initLoader(0, null, this);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            Log.d(TAG, "onCreateOptionsMenu()");
            menu.add(Menu.NONE, POPULATE_ID, 0, "Populate")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
            menu.add(Menu.NONE, CLEAR_ID, 0, "Clear")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            Log.d(TAG, "onOptionsItemSelected()");
            final ContentResolver cr = getActivity().getContentResolver();

            switch (item.getItemId()) {
                case POPULATE_ID:
                    if (mPopulatingTask != null) {
                        mPopulatingTask.cancel(false);
                    }
                    mPopulatingTask = new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            Log.d(TAG, "AsyncTask.doInBackground(1)");
                            for (char c = 'Z'; c >= 'A'; c--) {
                                Log.d(TAG, "char=" + c);
                                if (isCancelled()) {
                                    break;
                                }
                                StringBuilder builder = new StringBuilder("Data ");
                                builder.append(c);
                                ContentValues values = new ContentValues();
                                values.put(MainTable.COLUMN_NAME_DATA, builder.toString());
                                cr.insert(MainTable.CONTENT_URI, values);

                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {

                                }
                            }
                            return null;
                        }
                    };
                    mPopulatingTask.executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
                    return true;

                case CLEAR_ID:
                    if (mPopulatingTask != null) {
                        mPopulatingTask.cancel(false);
                        mPopulatingTask = null;
                    }
                    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            Log.d(TAG, "AsyncTask.doInBackground(2)");
                            cr.delete(MainTable.CONTENT_URI, null, null);
                            return null;
                        }
                    };
                    task.execute((Void[]) null);
                    return true;

                default:
                    return super.onOptionsItemSelected(item);

            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            Log.i(TAG, "Item clicked: " + id);
        }

        static final String[] PROJECTION = new String[] {
                MainTable._ID,
                MainTable.COLUMN_NAME_DATA
        };

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Log.d(TAG, "onCreateLoader()");
            CursorLoader cl = new CursorLoader(getActivity(), MainTable.CONTENT_URI,
                    PROJECTION, null, null, null);
            cl.setUpdateThrottle(2000);
            return cl;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            Log.d(TAG, "onLoadFinished()");
            mAdapter.swapCursor(data);

            if (isResumed()) {
                setListShown(true);
            } else {
                setListShownNoAnimation(true);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            Log.d(TAG, "onLoaderReset()");
            mAdapter.swapCursor(null);
        }
    }
}
