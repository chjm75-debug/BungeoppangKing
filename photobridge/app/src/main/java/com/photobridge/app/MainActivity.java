package com.photobridge.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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

        try {
            startForegroundService(new Intent(this, PhotoSyncService.class));
        } catch (Exception e) {
            Toast.makeText(this, "동기화 서비스 시작 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (hasPhotoPermission()) loadAlbums();
        else requestMediaPermission();
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
        status.setPadding(0,8,0,20);
        root.addView(status);

        selectedAlbumsText = new TextView(this);
        selectedAlbumsText.setText("선택된 앨범 없음");
        selectedAlbumsText.setTextSize(16);
        selectedAlbumsText.setPadding(0,6,0,10);
        root.addView(selectedAlbumsText);

        pendingText = new TextView(this);
        pendingText.setText("전송 대기 사진: 확인 중");
        pendingText.setTextSize(16);
        pendingText.setPadding(0,0,0,14);
        root.addView(pendingText);

        Button choose = new Button(this);
        choose.setText("앨범 선택");
        choose.setOnClickListener(v -> {
            if (!hasPhotoPermission()) {
                Toast.makeText(this,"사진 권한을 먼저 허용해주세요.",Toast.LENGTH_LONG).show();
                requestMediaPermission();
            } else {
                openAlbumPicker();
            }
        });
        root.addView(choose);

        Button settingsBtn = new Button(this);
        settingsBtn.setText("사진 권한 설정 열기");
        settingsBtn.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(settingsBtn);

        Button refresh = new Button(this);
        refresh.setText("앨범 목록 새로고침");
        refresh.setOnClickListener(v -> loadAlbums());
        root.addView(refresh);

        TextView note = new TextView(this);
        note.setText("※ PC 최초 연결 이전 사진은 전송하지 않습니다.");
        note.setPadding(0,18,0,0);
        root.addView(note);

        setContentView(sv);
    }

    private boolean hasPhotoPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestMediaPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES},100);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (hasPhotoPermission()) loadAlbums();
            else {
                selectedAlbumsText.setText("사진 권한이 필요합니다.");
                pendingText.setText("전송 대기 사진: 권한 필요");
            }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (selectedAlbumsText != null && hasPhotoPermission()) loadAlbums();
    }

    private String pairText() {
        long t = prefs.pairedAt();
        if (t == 0) return "아직 PC와 연결되지 않음";
        return "동기화 시작 기준: " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                        .format(new Date(t));
    }

    private void loadAlbums() {
        if (!hasPhotoPermission()) {
            cachedAlbums = new JSONArray();
            selectedAlbumsText.setText("사진 권한이 필요합니다.");
            pendingText.setText("전송 대기 사진: 권한 필요");
            return;
        }

        try {
            cachedAlbums = new MediaRepo(this).albums(prefs.selectedAlbums());
            updateSelectedAlbumsText();
            int count = new MediaRepo(this).pending(prefs).size();
            pendingText.setText("전송 대기 사진: " + count + "장");
        } catch(Exception e) {
            cachedAlbums = new JSONArray();
            selectedAlbumsText.setText("앨범 읽기 실패: " + e.getClass().getSimpleName());
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
            for(int i=0;i<cachedAlbums.length();i++) {
                JSONObject o = cachedAlbums.getJSONObject(i);
                if (selected.contains(o.getString("id"))) names.add(o.getString("name"));
            }
        } catch(Exception ignored) {}

        selectedAlbumsText.setText(names.isEmpty()
                ? "선택된 앨범 " + selected.size() + "개"
                : "선택됨: " + android.text.TextUtils.join(", ", names));
    }

    private void openAlbumPicker() {
        loadAlbums();
        if (cachedAlbums.length() == 0) {
            Toast.makeText(this,
                    "앨범을 읽지 못했습니다. '사진 권한 설정 열기'에서 사진 권한을 허용해주세요.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            int n = cachedAlbums.length();
            String[] names = new String[n];
            String[] ids = new String[n];
            boolean[] checked = new boolean[n];
            Set<String> selected = prefs.selectedAlbums();

            for(int i=0;i<n;i++) {
                JSONObject o = cachedAlbums.getJSONObject(i);
                names[i] = o.getString("name");
                ids[i] = o.getString("id");
                checked[i] = selected.contains(ids[i]);
            }

            new AlertDialog.Builder(this)
                    .setTitle("동기화할 앨범 선택")
                    .setMultiChoiceItems(names, checked,
                            (d, which, isChecked) -> checked[which] = isChecked)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("확인", (d,w) -> {
                        Set<String> save = new HashSet<>();
                        for(int i=0;i<n;i++) if(checked[i]) save.add(ids[i]);
                        prefs.setSelectedAlbums(save);
                        loadAlbums();
                        Toast.makeText(this,
                                save.size() + "개 앨범을 선택했습니다",
                                Toast.LENGTH_SHORT).show();
                    })
                    .show();
        } catch(Exception e) {
            Toast.makeText(this,
                    "앨범 선택 오류: " + e.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
