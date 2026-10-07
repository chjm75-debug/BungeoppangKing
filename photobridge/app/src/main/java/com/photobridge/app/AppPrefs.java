package com.photobridge.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

public class AppPrefs {
    private final SharedPreferences sp;
    public AppPrefs(Context c) { sp = c.getSharedPreferences("photobridge", Context.MODE_PRIVATE); }

    public String token() {
        String v = sp.getString("token", null);
        if (v == null) {
            int n = 100000 + new SecureRandom().nextInt(900000);
            v = String.valueOf(n);
            sp.edit().putString("token", v).apply();
        }
        return v;
    }

    public long pairedAt() { return sp.getLong("pairedAt", 0L); }
    public long ensurePairedNow() {
        long t = pairedAt();
        if (t == 0L) {
            t = System.currentTimeMillis();
            sp.edit().putLong("pairedAt", t).apply();
        }
        return t;
    }

    public Set<String> selectedAlbums() {
        return new HashSet<>(sp.getStringSet("albums", new HashSet<>()));
    }

    public void setSelectedAlbums(Set<String> ids) {
        sp.edit().putStringSet("albums", new HashSet<>(ids)).apply();
    }

    public Set<String> sentIds() {
        return new HashSet<>(sp.getStringSet("sent", new HashSet<>()));
    }

    public void ack(String id) {
        Set<String> s = sentIds();
        s.add(id);
        if (s.size() > 10000) {
            Set<String> trimmed = new HashSet<>();
            int i = 0;
            for (String x : s) { if (i++ >= s.size() - 8000) trimmed.add(x); }
            s = trimmed;
        }
        sp.edit().putStringSet("sent", s).apply();
    }
}
