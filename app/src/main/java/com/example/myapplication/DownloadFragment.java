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

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import data.models.Entry;
import io.realm.Realm;
import io.realm.RealmResults;
import list.items.EntryListItem;

import static com.mikepenz.fastadapter.adapters.ItemAdapter.items;

public class DownloadFragment extends Fragment {
//  todo: manage downloaded/downloading entries

    private Realm realm = null;

    @BindView(R.id.home_recycler) RecyclerView recycler;
    @BindView(R.id.home_swipeRefresher) SwipeRefreshLayout swipeRefreshLayout;
    @BindView(R.id.list_toolbar) Toolbar toolbar;

    // RecyclerView adapters
    private FastItemAdapter<EntryListItem> fastAdapter = null;
    ItemAdapter footerAdapter = null;

    public static DownloadFragment newInstance() {

        Bundle args = new Bundle();

        DownloadFragment fragment = new DownloadFragment();
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
        realm.close();
        super.onDestroy();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        toolbar.setTitle("Downloaded");

        realm = Realm.getDefaultInstance();

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

        // Recycler initialization
        recycler.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        recycler.setItemAnimator(new DefaultItemAnimator());
        recycler.setAdapter(fastAdapter);

        swipeRefreshLayout.setEnabled(false);

        fastAdapter.withSavedInstanceState(savedInstanceState);
        RealmResults<Entry> bookmarkResults = realm.where(Entry.class).equalTo("isDownloaded", true).findAll();
        List<EntryListItem> newEntryList = new ArrayList<>();
        for (Entry entry : bookmarkResults) {
            newEntryList.add(new EntryListItem(entry));
        }
        fastAdapter.setNewList(newEntryList);
    }
}
