package com.photobridge.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
    private TextView status;
    private TextView selectedAlbumsText;
    private TextView pendingText;
    private JSONArray cachedAlbums = new JSONArray();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        buildUi();
        requestMediaPermission();
        startForegroundService(new Intent(this, PhotoSyncService.class));
        loadAlbums();
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36,36,36,48);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("PhotoBridge");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("PC 연결 이후 새 사진만 자동 전송");
        sub.setTextSize(15);
        sub.setPadding(0,6,0,24);
        root.addView(sub);

        TextView code = new TextView(this);
        code.setText("연결 코드  " + prefs.token());
        code.setTextSize(22);
        code.setTypeface(null,Typeface.BOLD);
        root.addView(code);

        TextView ip = new TextView(this);
        ip.setText("휴대폰 IP  " + NetUtil.wifiIp() + ":8765");
        ip.setTextSize(17);
        ip.setPadding(0,8,0,8);
        root.addView(ip);

        status = new TextView(this);
        status.setText(pairText());
        status.setPadding(0,8,0,24);
        root.addView(status);

        TextView h = new TextView(this);
        h.setText("동기화할 앨범");
        h.setTextSize(18);
        h.setTypeface(null,Typeface.BOLD);
        root.addView(h);

        selectedAlbumsText = new TextView(this);
        selectedAlbumsText.setText("선택된 앨범 없음");
        selectedAlbumsText.setTextSize(16);
        selectedAlbumsText.setPadding(0,10,0,14);
        root.addView(selectedAlbumsText);

        Button choose = new Button(this);
        choose.setText("앨범 선택");
        choose.setOnClickListener(v -> openAlbumPicker());
        root.addView(choose);

        Button refresh = new Button(this);
        refresh.setText("앨범 목록 새로고침");
        refresh.setOnClickListener(v -> {
            loadAlbums();
            Toast.makeText(this,"앨범 목록을 새로 불러왔습니다",Toast.LENGTH_SHORT).show();
        });
        root.addView(refresh);

        TextView note = new TextView(this);
        note.setText("※ 처음 PC와 연결되기 전 사진은 전송하지 않습니다. 나중에 새 앨범을 추가하면 그 앨범도 추가한 시점 이후 사진부터 전송합니다.");
        note.setPadding(0,18,0,0);
        root.addView(note);

        setContentView(sv);
    }

    private String pairText() {
        long t = prefs.pairedAt();
        if (t == 0) return "아직 PC와 연결되지 않음 · 기존 사진은 전송 대상 아님";
        return "동기화 시작 기준: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(new Date(t));
    }

    private void requestMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            List<String> req = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
                req.add(Manifest.permission.READ_MEDIA_IMAGES);
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                req.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!req.isEmpty()) requestPermissions(req.toArray(new String[0]),100);
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) loadAlbums();
    }

    private void loadAlbums() {
        try {
            cachedAlbums = new MediaRepo(this).albums(prefs.selectedAlbums());
            updateSelectedAlbumsText();
            updatePendingText();
        } catch (Exception e) {
            cachedAlbums = new JSONArray();
            selectedAlbumsText.setText("앨범을 읽지 못했습니다. 사진 권한을 확인하세요.");
        }
    }

    private void updatePendingText() {
        try {
            int count = new MediaRepo(this).pending(prefs).size();
            pendingText.setText("전송 대기 사진: " + count + "장");
        } catch (Exception e) {
            pendingText.setText("전송 대기 사진: 확인 실패");
        }
    }

    private void updateSelectedAlbumsText() {
        Set<String> selected = prefs.selectedAlbums();
        if (selected.isEmpty()) {
            selectedAlbumsText.setText("선택된 앨범 없음");
            return;
        }

        List<String> names = new ArrayList<>();
        try {
            for (int i=0; i<cachedAlbums.length(); i++) {
                JSONObject o = cachedAlbums.getJSONObject(i);
                if (selected.contains(o.getString("id"))) names.add(o.getString("name"));
            }
        } catch (Exception ignored) {}

        if (names.isEmpty()) selectedAlbumsText.setText("선택된 앨범 " + selected.size() + "개");
        else selectedAlbumsText.setText("선택됨: " + android.text.TextUtils.join(", ", names));
    }

    private void openAlbumPicker() {
        loadAlbums();

        if (cachedAlbums.length() == 0) {
            Toast.makeText(this,"앨범을 찾지 못했습니다. 사진 접근 권한을 허용한 뒤 다시 눌러주세요.",Toast.LENGTH_LONG).show();
            requestMediaPermission();
            return;
        }

        try {
            int n = cachedAlbums.length();
            String[] names = new String[n];
            String[] ids = new String[n];
            boolean[] checked = new boolean[n];
            Set<String> selected = prefs.selectedAlbums();

            for (int i=0; i<n; i++) {
                JSONObject o = cachedAlbums.getJSONObject(i);
                names[i] = o.getString("name");
                ids[i] = o.getString("id");
                checked[i] = selected.contains(ids[i]);
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("동기화할 앨범 선택")
                    .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("확인", null)
                    .create();

            dialog.setOnShowListener(x -> {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Set<String> idsToSave = new HashSet<>();
                    for (int i=0; i<n; i++) if (checked[i]) idsToSave.add(ids[i]);

                    prefs.setSelectedAlbums(idsToSave);
                    loadAlbums();
                    Toast.makeText(this, idsToSave.size() + "개 앨범을 선택했습니다", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
            });

            dialog.show();
        } catch (Exception e) {
            Toast.makeText(this,"앨범 선택 화면을 열지 못했습니다: " + e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
}
