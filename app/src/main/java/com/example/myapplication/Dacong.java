package com.example.myapplication;
import android.app.Application;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import util.RealmUtil;


public class Dacong extends Application {

    @Override public void onCreate() {
        super.onCreate();

        // Realm
        Realm.init(this);
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder()
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(realmConfiguration);
        RealmUtil.firstRunPopulateDatabase();
    }
}