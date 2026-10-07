package com.photobridge.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.*;

public class MediaRepo {
    public static class Photo {
        public long id;
        public String name;
        public String mime;
        public String bucketId;
        public String bucketName;
        public long dateAddedMs;
        public long size;
        public Uri uri;
    }

    private final Context ctx;
    public MediaRepo(Context c) { ctx = c; }

    private Uri collection() {
        return MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    }

    public JSONArray albums(Set<String> selected) throws Exception {
        String[] p = {
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID
        };
        Cursor c = ctx.getContentResolver().query(collection(), p, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC");
        Map<String, JSONObject> map = new LinkedHashMap<>();
        if (c != null) {
            int iBid = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int iBn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            try {
                while (c.moveToNext()) {
                    String id = c.getString(iBid);
                    if (map.containsKey(id)) continue;
                    String name = c.getString(iBn);
                    JSONObject o = new JSONObject();
                    o.put("id", id);
                    o.put("name", name == null || name.isEmpty() ? "앨범 " + id : name);
                    o.put("selected", selected.contains(id));
                    map.put(id, o);
                }
            } finally { c.close(); }
        }
        JSONArray a = new JSONArray();
        for (JSONObject o : map.values()) a.put(o);
        return a;
    }

    public long maxIdForAlbum(String bucketId) {
        String[] p = { MediaStore.Images.Media._ID };
        String sel = MediaStore.Images.Media.BUCKET_ID + "=?";
        Cursor c = ctx.getContentResolver().query(collection(), p, sel, new String[]{bucketId},
                MediaStore.Images.Media._ID + " DESC");
        if (c == null) return 0L;
        try { return c.moveToFirst() ? c.getLong(0) : 0L; }
        finally { c.close(); }
    }

    public List<Photo> pending(AppPrefs prefs) {
        Set<String> albums = prefs.selectedAlbums();
        Set<String> sent = prefs.sentIds();
        List<Photo> out = new ArrayList<>();
        if (albums.isEmpty() || prefs.pairedAt() == 0L) return out;

        String[] p = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
        };
        Cursor c = ctx.getContentResolver().query(collection(), p, null, null,
                MediaStore.Images.Media.DATE_ADDED + " ASC");
        if (c == null) return out;
        try {
            int iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
            int iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
            int iBid = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID);
            int iBn = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
            int iDate = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED);
            int iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE);
            while (c.moveToNext()) {
                long id = c.getLong(iId);
                String sid = String.valueOf(id);
                String bid = c.getString(iBid);
                if (!albums.contains(bid) || sent.contains(sid)) continue;
                if (id <= prefs.albumBaselineId(bid)) continue;
                Photo ph = new Photo();
                ph.id = id;
                ph.name = c.getString(iName);
                ph.mime = c.getString(iMime);
                ph.bucketId = bid;
                ph.bucketName = c.getString(iBn);
                ph.dateAddedMs = c.getLong(iDate) * 1000L;
                ph.size = c.getLong(iSize);
                ph.uri = Uri.withAppendedPath(collection(), sid);
                out.add(ph);
            }
        } finally { c.close(); }
        return out;
    }

    public Photo get(long id) {
        String[] p = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE
        };
        String sel = MediaStore.Images.Media._ID + "=?";
        Cursor c = ctx.getContentResolver().query(collection(), p, sel, new String[]{String.valueOf(id)}, null);
        if (c == null) return null;
        try {
            if (!c.moveToFirst()) return null;
            Photo ph = new Photo();
            ph.id = id;
            ph.name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
            ph.mime = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE));
            ph.bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID));
            ph.bucketName = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
            ph.dateAddedMs = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000L;
            ph.size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE));
            ph.uri = Uri.withAppendedPath(collection(), String.valueOf(id));
            return ph;
        } finally { c.close(); }
    }

    public InputStream open(Photo p) throws Exception {
        return ctx.getContentResolver().openInputStream(p.uri);
    }
}
