package com.dekut.register;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Turns on Firebase's offline disk cache. Writes made while offline are stored
 * on the device and pushed automatically when the connection returns.
 * Must run before any other Firebase Database call.
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase db = FirebaseDatabase.getInstance();
        db.setPersistenceEnabled(true);
        // keep a local copy so summaries can still be shown offline
        db.getReference().keepSynced(true);
    }
}
