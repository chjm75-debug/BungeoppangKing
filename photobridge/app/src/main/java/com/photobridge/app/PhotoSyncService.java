package com.photobridge.app;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;

public class PhotoSyncService extends Service {
    private SimpleHttpServer server;
    private static final int PORT = 8765;

    @Override public void onCreate() {
        super.onCreate();
        String ch = "photobridge_sync";
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch,"PhotoBridge 동기화",NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = new Notification.Builder(this,ch)
                .setContentTitle("PhotoBridge 실행 중")
                .setContentText("선택한 앨범의 새 사진을 PC와 동기화합니다")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true).build();
        startForeground(4201,n);
        server = new SimpleHttpServer(this, PORT);
        server.start();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) { return START_STICKY; }
    @Override public void onDestroy() { if(server!=null)server.stop(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
