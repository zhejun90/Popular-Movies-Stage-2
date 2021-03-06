package com.example.android.popularmovies;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.example.android.popularmovies.data.MovieContract;
import com.example.android.popularmovies.utils.MovieDataBaseUtils;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements
        ImageDisplayAdapter.ImageDisplayAdapterOnClickHandler,
        CustomCursorAdapter.CustomCursorAdapterOnClickHandler,
        LoaderManager.LoaderCallbacks<Cursor>{

    private static final String TAG = "MainActivity";

    private TextView mErrorDisplay;
    private ImageDisplayAdapter mImageDisplayAdapter;
    private CustomCursorAdapter mCustomCursorAdapter;
    private RecyclerView mRecyclerView;

    private static int DISPLAY_STATE = 0;
    private int scrollState = -1;
    private int scrollOffset;
    private int scrollRange;

    // Make your own api_key.properties file in /app folder. Variable name myAPI_Key
    private static final String api_key = BuildConfig.MOVIES_DB_API_KEY;

    private final static String BUNDLE_RECYCLER_LAYOUT = "Recycler_Layout";
    private final static String SCROLL_OFFSET = "scrollOffset";
    private final static String SCROLL_RANGE = "scrollRange";
    private final static String DISPLAY_STATUS = "displayStatus";

    private Parcelable savedRecyclerLayoutState;

    // Data columns for display of movie data

    private static final String[] MAIN_MOVIE_PROJECTION = {
            MovieContract.MovieEntry.COLUMN_MOVIE_ID,
            MovieContract.MovieEntry.COLUMN_MOVIE,
            MovieContract.MovieEntry.COLUMN_MOVIE_IMAGE_URL,
            MovieContract.MovieEntry.COLUMN_MOVIE_SYNOPSIS,
            MovieContract.MovieEntry.COLUMN_MOVIE_RELEASE_DATE,
            MovieContract.MovieEntry.COLUMN_MOVIE_RATING
    };

    // Index values of array of strings to be accessed. Index values must
    // match above order for column data

    public static final int INDEX_MOVIE_ID = 0;
    public static final int INDEX_MOVIE_NAME = 1;
    public static final int INDEX_MOVIE_IMAGE_URL = 2;
    public static final int INDEX_MOVIE_SYNOPSIS = 3;
    public static final int INDEX_MOVIE_RELEASE_DATE = 4;
    public static final int INDEX_MOVIE_RATING = 5;

    // ID to be used for identifying loader responsible for loading movie data
    private static final int ID_MOVIE_LOADER = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mErrorDisplay = (TextView) findViewById(R.id.error_display);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView_display);

        GridLayoutManager layoutManager_display
                = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(layoutManager_display);

        mImageDisplayAdapter = new ImageDisplayAdapter(this);
        mImageDisplayAdapter.setContext(this);

        mCustomCursorAdapter = new CustomCursorAdapter(this, this);

        if (DISPLAY_STATE != 2)
            mRecyclerView.setAdapter(mImageDisplayAdapter);
        else mRecyclerView.setAdapter(mCustomCursorAdapter);

        mRecyclerView.setHasFixedSize(true);

        displayOnRequest();

        getSupportLoaderManager().initLoader(ID_MOVIE_LOADER, null, this);

        Log.d(TAG, "onCreate: ");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.movie_menu, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch(id) {
            case R.id.most_popular:
                DISPLAY_STATE = 0;
                if (mRecyclerView.getAdapter() != mImageDisplayAdapter) {
                    mRecyclerView.setAdapter(mImageDisplayAdapter);
                }
                displayOnRequest();
                return true;
            case R.id.top_rated:
                DISPLAY_STATE = 1;
                if (mRecyclerView.getAdapter() != mImageDisplayAdapter) {
                    mRecyclerView.setAdapter(mImageDisplayAdapter);
                }
                displayOnRequest();
                return true;
            case R.id.favourites:
                DISPLAY_STATE = 2;
                getSupportLoaderManager().restartLoader(ID_MOVIE_LOADER, null, MainActivity.this);
                mRecyclerView.setAdapter(mCustomCursorAdapter);
                showMovieCatalogue();
                Log.d(TAG, "onOptionsItemSelected: ");
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        savedRecyclerLayoutState = mRecyclerView.getLayoutManager().onSaveInstanceState();
        outState.putParcelable(BUNDLE_RECYCLER_LAYOUT, savedRecyclerLayoutState);
        scrollOffset = mRecyclerView.computeVerticalScrollOffset();
        scrollRange = mRecyclerView.computeVerticalScrollRange();

        outState.putInt(SCROLL_RANGE, scrollRange);
        outState.putInt(SCROLL_OFFSET, scrollOffset);
        outState.putInt(DISPLAY_STATUS, DISPLAY_STATE);

        Log.d(TAG, "onSaveInstanceState: ScrollRange " + scrollRange);
        Log.d(TAG, "onSaveInstanceState: ScrollExtent " + scrollOffset);
    }


    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState != null){
            savedRecyclerLayoutState = savedInstanceState.getParcelable(BUNDLE_RECYCLER_LAYOUT);
            mRecyclerView.getLayoutManager().onRestoreInstanceState(savedRecyclerLayoutState);
            scrollRange = savedInstanceState.getInt(SCROLL_RANGE);
            scrollOffset = savedInstanceState.getInt(SCROLL_OFFSET);
            DISPLAY_STATE = savedInstanceState.getInt(DISPLAY_STATUS);
        }

        if(DISPLAY_STATE == 2){
            showMovieCatalogue();
        }

        Log.d(TAG, "onRestoreInstanceState: ScrollRange " + scrollRange);
        Log.d(TAG, "onSaveInstanceState: ScrollExtent " + scrollOffset);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scrollState = mRecyclerView.getScrollY();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getSupportLoaderManager().restartLoader(ID_MOVIE_LOADER, null, MainActivity.this);
        Log.d(TAG, "onResume: ");
    }

    @Override
    public void onDisplayImageClicked(Movie movie) {
        Class destinationActivity = DetailActivity.class;
        Log.d(TAG, "onDisplayImageClicked: " + movie.favourite);
        Intent intent = new Intent(this, destinationActivity);
        intent.putExtra("Movie", movie);
        startActivity(intent);
    }

    @Override
    public void onCustomCursorAdapterImageClicked(Movie movie) {
        Class destinationActivity = DetailActivity.class;
        Intent intent = new Intent(this, destinationActivity);
        intent.putExtra("Movie", movie);
        startActivity(intent);
    }

    private void displayOnRequest(){
        if(!isInternetConnected()){
            showDisplayError();

        } else {
            new RetrieveFeedTask().execute(api_key);
            showMovieCatalogue();
        }
    }

    // Layout manager can only be restored after all images are loaded.
    // This should go in PostExecute of AsyncTask
    private void restoreLayoutManagerPosition(ArrayList<Movie> movies) {
        double relativeScrollPosition = (double)scrollOffset/(double)scrollRange;
        double scrollPos = relativeScrollPosition * (double)movies.size();
        mRecyclerView.scrollToPosition((int)scrollPos);
    }

    private boolean isInternetConnected(){
        Context context = MainActivity.this;
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void showDisplayError(){
        mRecyclerView.setVisibility(View.INVISIBLE);
        mErrorDisplay.setVisibility(View.VISIBLE);
    }

    private void showMovieCatalogue(){
        mRecyclerView.setVisibility(View.VISIBLE);
        mErrorDisplay.setVisibility(View.INVISIBLE);
    }

    private class RetrieveFeedTask extends AsyncTask<String, Void, String> {
        private static final String TAG = "AsyncTask";
        public static final String REQUEST_METHOD = "GET";
        public static final int READ_TIMEOUT = 15000;
        public static final int CONNECTION_TIMEOUT = 15000;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected String doInBackground(String... apiKey) {
            Log.d(TAG, "doInBackground: ");

            String urlString = "";
            HttpURLConnection urlConnection = null;
            if (DISPLAY_STATE == 0) {
                urlString = "https://api.themoviedb.org/3/movie/popular?";
            } else if (DISPLAY_STATE == 1){
                urlString = "https://api.themoviedb.org/3/movie/top_rated?";
            }
            try {
                URL url = new URL(urlString + "&api_key=" + apiKey[0]);
                urlConnection = (HttpURLConnection) url.openConnection();

                urlConnection.setRequestMethod(REQUEST_METHOD);
                urlConnection.setReadTimeout(READ_TIMEOUT);
                urlConnection.setConnectTimeout(CONNECTION_TIMEOUT);

                urlConnection.connect();

                InputStreamReader streamReader = new InputStreamReader(urlConnection.getInputStream());
                BufferedReader bufferedReader = new BufferedReader(streamReader);
                StringBuilder stringBuilder = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                bufferedReader.close();
                streamReader.close();

                return stringBuilder.toString();

            } catch(IOException e) {
                e.printStackTrace();
            }
            finally {
                if(urlConnection != null){
                    urlConnection.disconnect();
                }
            }
            return null;
        }

        protected void onPostExecute(String movieResults) {
            try {
                if (movieResults != null) {
                // Set data to be retrieved when DetailActivity is called
                    mImageDisplayAdapter.setMovies
                            (MovieDataBaseUtils.getMovieObjectsFromJSON(movieResults));
                    showMovieCatalogue();
                    Log.d(TAG, "onPostExecute: Movies Loaded");
                    restoreLayoutManagerPosition(mImageDisplayAdapter.getMovies());
                }
            } catch (JSONException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Log.d(TAG, "onCreateLoader: CursorID " + id + " loaded");
        switch (id) {
            case ID_MOVIE_LOADER:
                Uri movieQueryUri = MovieContract.MovieEntry.CONTENT_URI;

                return new CursorLoader(this,
                        movieQueryUri,
                        MAIN_MOVIE_PROJECTION,
                        null,
                        null,
                        null);
            default:
                throw new RuntimeException("Loader not implemented: " + id);
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCustomCursorAdapter.setCursor(data);
        Log.d(TAG, "onLoadFinished: Cursor set CustomCursorAdapter");
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCustomCursorAdapter.setCursor(null);
    }
}
