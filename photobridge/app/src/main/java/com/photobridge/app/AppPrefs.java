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
        Set<String> old = selectedAlbums();
        SharedPreferences.Editor e = sp.edit().putStringSet("albums", new HashSet<>(ids));
        long now = System.currentTimeMillis();
        long pair = pairedAt();
        for (String id : ids) {
            if (!old.contains(id)) {
                long start = Math.max(pair, now);
                e.putLong("albumStart_" + id, start);
            }
        }
        e.apply();
    }

    public void setAlbumBaselineId(String id, long maxId) {
        sp.edit().putLong("albumBaseId_" + id, maxId).apply();
    }

    public long albumBaselineId(String id) {
        return sp.getLong("albumBaseId_" + id, 0L);
    }

    public long albumStart(String id) {
        long p = pairedAt();
        long v = sp.getLong("albumStart_" + id, 0L);
        return v == 0L ? p : Math.max(v, p);
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
