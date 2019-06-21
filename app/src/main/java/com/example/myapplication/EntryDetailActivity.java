package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;

import java.io.File;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import data.models.Author;
import data.models.Classification;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import data.arxiv.ArxivAPI;
import data.arxiv.ArxivClient;
import data.arxiv.model.SearchResult;
import data.models.Entry;
import list.items.EntryAuthorItem;
import list.items.EntryClassificationItem;
import util.ArxivUtil;

/**
 * Three entry vectors:
 *  - New internal load
 *  - Recycled load from savedInstanceState
 *  - External load from user's web browser
 */
public class EntryDetailActivity extends AppCompatActivity {

    private static final String TAG = EntryDetailActivity.class.getSimpleName();
    private static final String ARG_ENTRY = "entry";

    @BindView(R.id.entry_detail_coordinator) CoordinatorLayout viewRoot;
    @BindView(R.id.entry_detail_nestedscrollview) NestedScrollView scrollView;
    @BindView(R.id.entry_detail_title) TextView title;
    @BindView(R.id.entry_detail_bookmark_fab) FloatingActionButton bookmarkButton;
    @BindView(R.id.entry_detail_summary) TextView summary;
    @BindView(R.id.entry_detail_extrasSpace) Space extraSpace;
    @BindView(R.id.entry_detail_journalRef) TextView journalRef;
    @BindView(R.id.entry_detail_comment) TextView comment;

    @BindView(R.id.entry_detail_publishedDate) TextView publishedDate;
    @BindView(R.id.entry_detail_updatedFlv) TextView updatedFlv;
    @BindView(R.id.entry_detail_updatedDate) TextView updatedDate;
    @BindView(R.id.entry_detail_authors_card)   CardView authorsCard;
    @BindView(R.id.entry_detail_authors_list)    RecyclerView authorsRecycler;
    @BindView(R.id.entry_detail_classifications_card) CardView classificationsCard;
    @BindView(R.id.entry_detail_classifications_list) RecyclerView classificationsRecycler;
    @BindView(R.id.read_button) FloatingActionButton readButton;
    @BindView(R.id.entry_detail_toolbar) Toolbar detailToolBar;


    // Composite Disposable for handling URL to app entry loading
    private CompositeDisposable disposables = new CompositeDisposable();

    private Realm realm = null;
    private Entry entry = null;
    private long  mReference = 0 ;

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction() ;
            if(action != null && !action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) return;
            long  reference = intent.getLongExtra( DownloadManager.EXTRA_DOWNLOAD_ID , -1 );
            if (reference == mReference) {
                checkDownload();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = Realm.getDefaultInstance();

//        todo: set loading ui; load content ui after data are ready
        setContentView(R.layout.activity_entry_detail);
        ButterKnife.bind(this);
        detailToolBar.setTitle("Paper ID");

        setSupportActionBar(findViewById(R.id.entry_detail_toolbar));

        Intent intent = getIntent();
        if (intent != null) {
            // Incoming from web browser
            Uri incomingUrl = intent.getData();
            if (incomingUrl != null) {
                Log.d(TAG, "Loading entry from URI intent: [ " + incomingUrl.toString() + " ]");
                List<String> params = incomingUrl.getPathSegments();
                String entryId = params.get(1);

                // Retrieve entry object from API
                ArxivAPI arxivApi = ArxivClient.createService(ArxivAPI.class);
                disposables.add(
                    arxivApi.getEntry(entryId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeWith(new DisposableObserver<SearchResult>() {
                       @Override
                       public void onNext(SearchResult searchResult) {
                           entry = ArxivUtil.parseRawEntry( searchResult.entries.get(0) );
                       }

                       @Override
                       public void onError(Throwable e) {
                           Log.e(TAG, e.getMessage(), e.fillInStackTrace());
                       }

                       @Override
                       public void onComplete() {
                                setEntryUI();
                           }
                   })
                );
            } else if (intent.getParcelableExtra(ARG_ENTRY) != null) {
                entry = intent.getParcelableExtra(ARG_ENTRY);
                setEntryUI();
            }
            // Reconstruct entry from saved activity state
        } else if (savedInstanceState != null && savedInstanceState.containsKey(ARG_ENTRY)) {
            entry = savedInstanceState.getParcelable(ARG_ENTRY);
            setEntryUI();
        }

        readButton.setOnClickListener((View view)->{
            if (entry == null) return;
            if (!entry.getIsDownloaded()) {

//                 todo: do it after download completes
                entry.setIsDownloaded(true);
                Entry result = realm.where(Entry.class).equalTo("idUrl", entry.getIdUrl()).findFirst();
                if (result == null)
                    realm.executeTransaction(realm -> realm.copyToRealm(entry));
                else
                    realm.executeTransaction(realm -> result.setIsDownloaded(true));

                // start download via DownloadManager
                Toast.makeText(getApplicationContext(), "Start downloading", Toast.LENGTH_SHORT).show();

                DownloadManager downloadmanager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(entry.getPdfUrl()));

                request.setTitle(entry.getID());
                request.setDescription("Downloading");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, entry.getID() + ".pdf");
                mReference = downloadmanager.enqueue(request);

                IntentFilter filter = new IntentFilter( DownloadManager.ACTION_DOWNLOAD_COMPLETE ) ;
                registerReceiver(receiver, filter) ;
            }
            else {
//                downloaded; build intent to browse pdf file
                String dir =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                File pdfFile = new File(dir+ "/" + entry.getID() + ".pdf");

                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                browserIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri pdfURI = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", pdfFile);
                browserIntent.setDataAndType(pdfURI, "application/pdf");
                Intent chooser = Intent.createChooser(browserIntent, "Open PDF");
                chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
            }
        });
    }

    @Override
    protected void onDestroy() {
        realm.close();
        disposables.clear();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ARG_ENTRY, entry);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            entry = savedInstanceState.getParcelable(ARG_ENTRY);
        }
    }

    /**
     * Abstracted method to load the UI.
     * This method was previously onStart, but in order to load external URLs an API call must be made
     * and the result loaded asynchronously. Abstracting the previous code allows the ui to load
     * upon data load
     */

    @SuppressLint("RestrictedApi")
    private void setEntryUI() {

        detailToolBar.setTitle(entry.getID());

        authorsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        classificationsRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        title.setText(entry.getTitle());
        summary.setText(entry.getSummary());

        if (entry.getComment() == null && entry.getJournalRef() == null) {
            extraSpace.setVisibility(View.GONE);
        }

        if (entry.getJournalRef() != null) {
            journalRef.setText(entry.getJournalRef());
        } else {
            journalRef.setVisibility(View.GONE);
        }

        if (entry.getComment() != null) {
            comment.setText(entry.getComment());
        } else {
            comment.setVisibility(View.GONE);
        }


        if (entry.getUpdatedDate() != null) {
            updatedDate.setText(entry.getUpdatedDate());
        } else {
            updatedFlv.setVisibility(View.GONE);
            updatedDate.setVisibility(View.GONE);
        }

        if (entry.getPublishedDate() != null) {
            publishedDate.setText(entry.getPublishedDate());
        } else {
            publishedDate.setText("----");
        }

        if (entry.getAuthors() != null) {
            FastItemAdapter<EntryAuthorItem> authorAdapter = new FastItemAdapter<>();
            authorsRecycler.setAdapter(authorAdapter);
            for (Author author : entry.getAuthors()) {
                authorAdapter.add(new EntryAuthorItem(author));
            }
        } else {
            authorsCard.setVisibility(View.GONE);
        }

        if (entry.getPdfUrl() == null) {
            readButton.setVisibility(View.GONE);
        }

        if (entry.getClassifications() != null) {
            FastItemAdapter<EntryClassificationItem> classificationAdapter = new FastItemAdapter<>();
            classificationsRecycler.setAdapter(classificationAdapter);
            for (Classification classification : entry.getClassifications()) {
                classificationAdapter.add(new EntryClassificationItem(classification));
            }
        } else {
            classificationsCard.setVisibility(View.GONE);
        }

        checkBookmark();
        checkDownload();

        // auto hide readButton
        scrollView.setOnScrollChangeListener(
            (NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY)->{
            if (scrollY > oldScrollY)
                readButton.hide();
            else
                readButton.show();
            }
        );
    }

    public static Intent createIntent(Context context, @NonNull Entry entry) {
        Intent intent = new Intent(context, EntryDetailActivity.class);
        intent.putExtra(ARG_ENTRY, entry);
        return intent;
    }

    @OnClick(R.id.entry_detail_bookmark_fab)
    void bookmarkClicked() {
        entry.setIsInReadList(!entry.getIsInReadList());
        Entry result = realm.where(Entry.class).equalTo("idUrl", entry.getIdUrl()).findFirst();
        if (result != null && result.getIsInReadList()) {
            realm.executeTransaction(realm -> result.setIsInReadList(false));
            Toast.makeText(getApplicationContext(), "Bookmark deleted", Toast.LENGTH_SHORT).show();
            bookmarkButton.setImageResource(R.drawable.bookmark_border);
        } else {
            if (result == null)
                realm.executeTransaction(realm -> realm.copyToRealm(entry));
            else
                realm.executeTransaction(realm -> result.setIsInReadList(true));
            Toast.makeText(getApplicationContext(), "Bookmark added", Toast.LENGTH_SHORT).show();
            bookmarkButton.setImageResource(R.drawable.bookmark);
        }
    }

    void checkBookmark() {
        Entry result = realm.where(Entry.class)
                .equalTo("idUrl", entry.getIdUrl())
                .equalTo("isInReadList", true).findFirst();
        entry.setIsInReadList(result != null);
        if (entry.getIsInReadList())
            bookmarkButton.setImageResource(R.drawable.bookmark);
        else
            bookmarkButton.setImageResource(R.drawable.bookmark_border);
    }

    void checkDownload() {
        String dir =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        File pdfFile = new File(dir+ "/" + entry.getID() + ".pdf");
        entry.setIsDownloaded(pdfFile.exists());
        if (entry.getIsDownloaded())
            readButton.setImageResource(R.drawable.ic_visibility_black_24dp);
        else
            readButton.setImageResource(R.drawable.ic_file_download_black_24dp);
    }
}
