package com.photobridge.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private AppPrefs prefs;
    private LinearLayout albumBox;
    private TextView status;
    private final Map<String,CheckBox> checks = new LinkedHashMap<>();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        requestMediaPermission();
        startForegroundService(new Intent(this, PhotoSyncService.class));
        buildUi();
    }

    private void buildUi() {
        ScrollView sv=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,36,36,48);
        sv.addView(root);

        TextView title=new TextView(this); title.setText("PhotoBridge"); title.setTextSize(28); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView sub=new TextView(this); sub.setText("PC 연결 이후 새 사진만 자동 전송"); sub.setTextSize(15); sub.setPadding(0,6,0,24); root.addView(sub);

        TextView code=new TextView(this); code.setText("연결 코드  " + prefs.token()); code.setTextSize(22); code.setTypeface(null,Typeface.BOLD); root.addView(code);
        TextView ip=new TextView(this); ip.setText("휴대폰 IP  " + NetUtil.wifiIp() + ":8765"); ip.setTextSize(17); ip.setPadding(0,8,0,8); root.addView(ip);

        status=new TextView(this); status.setText(pairText()); status.setPadding(0,8,0,24); root.addView(status);

        Button refresh=new Button(this); refresh.setText("앨범 목록 새로고침"); refresh.setOnClickListener(v->loadAlbums()); root.addView(refresh);
        TextView h=new TextView(this); h.setText("동기화할 앨범 선택"); h.setTextSize(18); h.setTypeface(null,Typeface.BOLD); h.setPadding(0,24,0,8); root.addView(h);

        albumBox=new LinearLayout(this); albumBox.setOrientation(LinearLayout.VERTICAL); root.addView(albumBox);
        Button save=new Button(this); save.setText("선택 저장"); save.setOnClickListener(v->saveAlbums()); root.addView(save);
        TextView note=new TextView(this); note.setText("※ 처음 PC와 연결되기 전 사진은 전송하지 않습니다. 나중에 새 앨범을 추가하면 그 앨범도 추가한 시점 이후 사진부터 전송합니다."); note.setPadding(0,18,0,0); root.addView(note);

        setContentView(sv); loadAlbums();
    }

    private String pairText() {
        long t=prefs.pairedAt();
        if(t==0) return "아직 PC와 연결되지 않음 · 기존 사진은 전송 대상 아님";
        return "동기화 시작 기준: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date(t));
    }

    private void requestMediaPermission() {
        if(android.os.Build.VERSION.SDK_INT>=33) {
            if(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS},100);
        } else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},100);
    }

    private void loadAlbums() {
        albumBox.removeAllViews(); checks.clear();
        try {
            JSONArray a=new MediaRepo(this).albums(prefs.selectedAlbums());
            for(int i=0;i<a.length();i++) {
                JSONObject o=a.getJSONObject(i);
                CheckBox c=new CheckBox(this); c.setText(o.getString("name")); c.setChecked(o.getBoolean("selected"));
                String id=o.getString("id"); checks.put(id,c); albumBox.addView(c);
            }
            if(a.length()==0) {
                TextView t=new TextView(this); t.setText("사진 권한을 허용한 뒤 새로고침하세요."); albumBox.addView(t);
            }
        } catch(Exception e) {
            TextView t=new TextView(this); t.setText("앨범을 읽지 못했습니다: "+e.getMessage()); albumBox.addView(t);
        }
    }

    private void saveAlbums() {
        Set<String> ids=new HashSet<>();
        for(Map.Entry<String,CheckBox> e:checks.entrySet()) if(e.getValue().isChecked()) ids.add(e.getKey());
        Set<String> old = prefs.selectedAlbums();
        if (prefs.pairedAt() > 0L) {
            MediaRepo repo = new MediaRepo(this);
            for (String id : ids) if (!old.contains(id)) prefs.setAlbumBaselineId(id, repo.maxIdForAlbum(id));
        }
        prefs.setSelectedAlbums(ids);
        Toast.makeText(this,"선택한 앨범을 저장했습니다",Toast.LENGTH_SHORT).show();
        status.setText(pairText());
    }
}
