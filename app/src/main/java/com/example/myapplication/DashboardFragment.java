package com.example.myapplication;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter_extensions.items.ProgressItem;
import com.mikepenz.fastadapter_extensions.scroll.EndlessRecyclerOnScrollListener;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import data.arxiv.ArxivAPI;
import data.arxiv.ArxivClient;
import data.arxiv.model.ArxivEntry;
import data.arxiv.model.SearchResult;
import data.models.Classification;
import data.models.Entry;
import data.models.Query;
import data.models.Subject;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.schedulers.Schedulers;
import io.realm.Realm;
import io.realm.RealmResults;
import list.items.EntryListItem;
import util.ArxivUtil;

import static com.mikepenz.fastadapter.adapters.ItemAdapter.items;

public class DashboardFragment extends Fragment {
//  todo: select different categories
    private static final int MAX_SEARCH_RESULTS = 20;


    private Realm realm = null;

    @BindView(R.id.home_recycler) RecyclerView recycler;
    @BindView(R.id.home_swipeRefresher) SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.list_toolbar) Toolbar toolbar;

    // Preferred classification parameters
    private RealmResults<Subject> subjects = null;

    // Current search state
    private Query searchQuery = null;
    private boolean isLoading = false;

    // Simple reactive handling of search queries, cleaned up along side life cycle
    private CompositeDisposable disposables = new CompositeDisposable();

    // RecyclerView adapters
    private FastItemAdapter<EntryListItem> fastAdapter = null;
    ItemAdapter footerAdapter = null;
    EndlessRecyclerOnScrollListener scrollListener = null;

    public static DashboardFragment newInstance() {

        Bundle args = new Bundle();

        DashboardFragment fragment = new DashboardFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.app_bar_main, container, false);
        ButterKnife.bind(this, root);
        return root;
    }


    @Override
    public void onDestroy() {
        disposables.dispose();
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar.setTitle("Dashboard");

        realm = Realm.getDefaultInstance();
        subjects = realm.where(Subject.class).findAll();

        DrawerLayout drawer = (DrawerLayout) getActivity().findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                getActivity(), drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Adapter initialization
        fastAdapter = new FastItemAdapter<>();
        fastAdapter.withSelectable(true);

        footerAdapter = items();

        fastAdapter.withOnClickListener((v, adapter, item, position) -> {
            startActivity(EntryDetailActivity.createIntent(getActivity(), item.getEntry()));
            return true;
        });


        searchQuery = new Query();

        // Recycler initialization
        recycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recycler.setItemAnimator(new DefaultItemAnimator());
        recycler.setAdapter(fastAdapter);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            searchQuery.setCurrentStartIndex(0);
            searchQuery.setTotalResults(-1);
            loadResults();
        });

        fastAdapter.addAdapter(1, footerAdapter);

        scrollListener = new EndlessRecyclerOnScrollListener(footerAdapter) {
            @Override public void onLoadMore(int currentPage) {
                footerAdapter.clear();
                footerAdapter.add(new ProgressItem().withEnabled(false));
                loadResults();
            }
        };
        recycler.addOnScrollListener(scrollListener);

        fastAdapter.withSavedInstanceState(savedInstanceState);
        initSearchQuery(savedInstanceState);
        swipeRefreshLayout.setRefreshing(true);
        loadResults();

    }

    private void initSearchQuery(Bundle savedInstanceState) {
        Classification classification = null;
//        if (savedInstanceState != null) {
//            searchQuery = savedInstanceState.getParcelable(ARG_QUERY);
//        } else {
        searchQuery = new Query();
//        }

        Subject subject = subjects.get(0);
        classification = new Classification(subject.getName(), subject.getKey(), null);
        if (subject.getCategories() != null && !subject.getCategories().isEmpty()) {
            classification.setCategory(subject.getCategories().get(0));
        }

        searchQuery.setClassification(classification);
    }


    private void loadResults() {
        if (fastAdapter == null || isLoading)
            return;

        isLoading = true;
        List<EntryListItem> newEntryList = new ArrayList<>();
        ArxivAPI api = ArxivClient.createService(ArxivAPI.class);
        disposables.add(api.query(searchQuery.getQuery(),
            searchQuery.getCurrentStartIndex(),
            MAX_SEARCH_RESULTS,
            searchQuery.getSortBy(),
            searchQuery.getSortOrder())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<SearchResult>() {
                @Override public void onNext(SearchResult searchResult) {
                    if (searchResult.totalResults == 0) {
                        swipeRefreshLayout.setRefreshing(false);
                        recycler.setVisibility(View.GONE);
                        return;
                    }

                    recycler.setVisibility(View.VISIBLE);
                    if (searchQuery.getTotalResults() == -1) {
                        searchQuery.setTotalResults(searchResult.totalResults);
                    }

                    for (ArxivEntry arxivEntry : searchResult.entries) {
                        Entry entry = ArxivUtil.parseRawEntry(arxivEntry);
                        newEntryList.add(new EntryListItem(entry));
                    }
                }

                @Override public void onError(Throwable e) {
                    swipeRefreshLayout.setRefreshing(false);
//                    recycler.setVisibility(View.GONE);
                    isLoading = false;
                }

                @Override public void onComplete() {
                    if (!newEntryList.isEmpty()) {
                        if (!swipeRefreshLayout.isRefreshing())
                            fastAdapter.add(newEntryList);
                        else{
                            fastAdapter.setNewList(newEntryList);
                            scrollListener.resetPageCount();
                        }
                    }
                    isLoading = false;
                    searchQuery.setCurrentStartIndex(searchQuery.getCurrentStartIndex() + MAX_SEARCH_RESULTS);
                    if (searchQuery.getCurrentStartIndex() > searchQuery.getTotalResults()) {
                        searchQuery.setTotalResults(searchQuery.getTotalResults() - 1);
                    }
                    swipeRefreshLayout.setRefreshing(false);
                }
            })
        );
    }
}
