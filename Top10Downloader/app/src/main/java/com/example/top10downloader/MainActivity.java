package com.example.top10downloader;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private ListView listapps;
    FeedAdapter feedAdapter;
    private ProgressBar loadingIndicator;
    private ArrayList<FeedEntry> applications;
    private String feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml";
    private int feedLimit = 10;
    private boolean checked = true;
    private String feedCacheUrl = "INVALIDATED";
    public static final String STATE_URL = "feedUrl";
    public static final String STATE_LIMIT = "feedLimit";
    public static final String DOWNLOADED_ITEM = "downloaded_item";
    public static final String APPLICATIONS_LIST = "applications";
    private String downloaded_item = "Free Applications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listapps = (ListView) findViewById(R.id.xmlListView);
        loadingIndicator = (ProgressBar) findViewById(R.id.loading_indicator);
        feedAdapter = new FeedAdapter(MainActivity.this, R.layout.list_record, new ArrayList<FeedEntry>());
        listapps.setAdapter(feedAdapter);
        if (savedInstanceState != null) {
            feedUrl = savedInstanceState.getString(STATE_URL);
            feedLimit = savedInstanceState.getInt(STATE_LIMIT);
            downloaded_item = savedInstanceState.getString(DOWNLOADED_ITEM);
            applications = (ArrayList<FeedEntry>) savedInstanceState.getSerializable(APPLICATIONS_LIST);
            feedAdapter.loadNewData(applications);
        }
        getSupportActionBar().setTitle("Top "+feedLimit+" "+downloaded_item);
        downloadUrl(String.format(feedUrl, feedLimit));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.feeds_menu, menu);
        if (feedLimit == 10) {
            menu.findItem(R.id.mnu10).setChecked(true);
        } else {
            menu.findItem(R.id.mnu25).setChecked(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.mnuFree:
                feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml";
                checked = true;
                downloaded_item = "Free Applications";
                getSupportActionBar().setTitle("Free Applications");
                break;
            case R.id.mnuPaid:
                feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/toppaidapplications/limit=%d/xml";
                checked = true;
                downloaded_item = "Paid Applications";
                break;
            case R.id.mnuSongs:
                feedUrl = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topsongs/limit=%d/xml";
                checked = true;
                downloaded_item = "Songs";
                break;
            case R.id.mnu10:
            case R.id.mnu25:
                if (item.isChecked()) {
                    Log.d(TAG, "onOptionsItemSelected: " + item.getTitle() + " feedlimit unchanged");
                    checked = false;
                } else {
                    item.setChecked(true);
                    feedLimit = 35 - feedLimit;
                    Log.d(TAG, "onOptionsItemSelected: " + item.getTitle() + " setting feedlimit to " + feedLimit);
                    checked = true;
                }
                break;
            case R.id.mnuRefresh:
                feedCacheUrl="INVALIDATED";
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        if (checked) {
            getSupportActionBar().setTitle("Top "+feedLimit+" "+downloaded_item);
            downloadUrl(String.format(feedUrl, feedLimit));
        }
        return true;

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_URL, feedUrl);
        outState.putInt(STATE_LIMIT, feedLimit);
        outState.putString(DOWNLOADED_ITEM,downloaded_item);
        outState.putSerializable(APPLICATIONS_LIST,applications);
        super.onSaveInstanceState(outState);
    }

    private void downloadUrl(String feedUrl) {
        if(!feedUrl.equalsIgnoreCase(feedCacheUrl)){
            Log.d(TAG, "downloadUrl: starting Asynctask");
            DownloadData downloadData = new DownloadData();
            downloadData.execute(feedUrl);
            feedCacheUrl=feedUrl;
            Log.d(TAG, "downloadUrl: done");
        }else {
            Log.d(TAG, "downloadUrl: URL not changed");
        }
    }


    private class DownloadData extends AsyncTask<String, Void, String> {
        private static final String TAG = "DownloadData";

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingIndicator.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            loadingIndicator.setVisibility(View.INVISIBLE);
            ParseApplications parseApplications = new ParseApplications();
            parseApplications.parse(s);
            Log.d(TAG, "onPostExecute: starts");
            applications = parseApplications.getApplications();
            feedAdapter.loadNewData(applications);
            Log.d(TAG, "onPostExecute: ends");
        }

        @Override
        protected String doInBackground(String... strings) {
            Log.d(TAG, "doInBackground: starts with " + strings[0]);
            String rssFeed = downloadXML(strings[0]);
            if (rssFeed == null) {
                Log.e(TAG, "doInBackground: Error downloading");
            }
            return rssFeed;
        }

        private String downloadXML(String urlPath) {
            StringBuilder xmlResult = new StringBuilder();
            try {
                URL url = new URL(urlPath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                int response = connection.getResponseCode();
                Log.d(TAG, "downloadXML: The response code was " + response);
//                InputStream inputStream = connection.getInputStream();
//                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
//                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                int charsRead;
                char[] inputBuffer = new char[500];
                while (true) {
                    charsRead = bufferedReader.read(inputBuffer);
                    if (charsRead < 0) {
                        break;
                    }
                    if (charsRead > 0) {
                        xmlResult.append(String.copyValueOf(inputBuffer, 0, charsRead));
                    }
                }
                bufferedReader.close();

                return xmlResult.toString();
            } catch (MalformedURLException e) {
                Log.e(TAG, "downloadXML: Invalid URL " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "downloadXML: IO Exception reading data " + e.getMessage());
            } catch (SecurityException e) {
                Log.e(TAG, "downloadXML: Security Exception. Needs permission? " + e.getMessage());
            }
            return null;
        }

    }


}
