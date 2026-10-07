package com.photobridge.app;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class SimpleHttpServer {
    private final Context ctx;
    private final int port;
    private volatile boolean running = false;
    private ServerSocket server;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final AppPrefs prefs;
    private final MediaRepo media;

    public SimpleHttpServer(Context c, int port) {
        this.ctx = c.getApplicationContext();
        this.port = port;
        prefs = new AppPrefs(ctx);
        media = new MediaRepo(ctx);
    }

    public void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress("0.0.0.0", port));
                while (running) {
                    Socket s = server.accept();
                    s.setSoTimeout(15000);
                    pool.submit(() -> handle(s));
                }
            } catch (Exception ignored) {
            } finally { running = false; }
        }, "PhotoBridgeHttp").start();
    }

    public void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        pool.shutdownNow();
    }

    private static class Req {
        String method, path;
        Map<String,String> query = new HashMap<>();
        Map<String,String> headers = new HashMap<>();
        byte[] body = new byte[0];
    }

    private void handle(Socket s) {
        try (Socket socket = s) {
            Req r = readRequest(socket.getInputStream());
            if (r == null) return;
            route(r, socket.getOutputStream());
        } catch (Exception ignored) {}
    }

    private Req readRequest(InputStream in) throws Exception {
        BufferedInputStream bin = new BufferedInputStream(in);
        String first = readLine(bin);
        if (first == null || first.isEmpty()) return null;
        String[] p = first.split(" ");
        if (p.length < 2) return null;
        Req r = new Req(); r.method = p[0];
        String raw = p[1];
        int q = raw.indexOf('?');
        r.path = q >= 0 ? raw.substring(0,q) : raw;
        if (q >= 0) {
            String qs = raw.substring(q+1);
            for (String kv : qs.split("&")) {
                if (kv.isEmpty()) continue;
                String[] a = kv.split("=",2);
                r.query.put(URLDecoder.decode(a[0], "UTF-8"), a.length>1?URLDecoder.decode(a[1],"UTF-8"):"");
            }
        }
        int len = 0;
        while (true) {
            String line = readLine(bin);
            if (line == null || line.isEmpty()) break;
            int c = line.indexOf(':');
            if (c > 0) {
                String k = line.substring(0,c).trim().toLowerCase(Locale.ROOT);
                String v = line.substring(c+1).trim();
                r.headers.put(k,v);
                if (k.equals("content-length")) len = Integer.parseInt(v);
            }
        }
        if (len > 0) {
            r.body = new byte[len];
            int off=0,n;
            while (off<len && (n=bin.read(r.body,off,len-off))>0) off+=n;
        }
        return r;
    }

    private String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int prev=-1,cur;
        while ((cur=in.read())!=-1) {
            if (prev=='\r' && cur=='\n') break;
            if (prev!=-1) b.write(prev);
            prev=cur;
        }
        if (cur==-1 && prev==-1) return null;
        if (prev!=-1 && prev!='\r') b.write(prev);
        return b.toString("UTF-8");
    }

    private boolean auth(Req r) {
        String t = r.query.get("token");
        if (t == null) t = r.headers.get("x-photobridge-token");
        return prefs.token().equals(t);
    }

    private void route(Req r, OutputStream out) throws Exception {
        if (r.method.equals("OPTIONS")) { send(out,204,"text/plain",new byte[0]); return; }
        if (r.path.equals("/ping")) {
            JSONObject o = new JSONObject();
            o.put("ok", true); o.put("app", "PhotoBridge"); o.put("version", "0.1.0");
            o.put("paired", prefs.pairedAt()>0); o.put("pairedAt", prefs.pairedAt());
            sendJson(out,200,o); return;
        }
        if (r.path.equals("/pair") && r.method.equals("POST")) {
            if (!auth(r)) { sendJson(out,403,new JSONObject().put("ok",false)); return; }
            long t = prefs.ensurePairedNow();
            for (String albumId : prefs.selectedAlbums()) {
                if (prefs.albumBaselineId(albumId) == 0L)
                    prefs.setAlbumBaselineId(albumId, media.maxIdForAlbum(albumId));
            }
            sendJson(out,200,new JSONObject().put("ok",true).put("pairedAt",t)); return;
        }
        if (!auth(r)) { sendJson(out,403,new JSONObject().put("ok",false).put("error","bad token")); return; }

        if (r.path.equals("/albums") && r.method.equals("GET")) {
            sendJson(out,200,new JSONObject().put("albums", media.albums(prefs.selectedAlbums()))); return;
        }
        if (r.path.equals("/albums") && r.method.equals("POST")) {
            JSONObject body = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
            JSONArray a = body.optJSONArray("ids");
            Set<String> ids = new HashSet<>();
            if (a != null) for(int i=0;i<a.length();i++) ids.add(a.getString(i));
            Set<String> old = prefs.selectedAlbums();
            if (prefs.pairedAt() > 0L) {
                for (String id : ids) {
                    if (!old.contains(id)) prefs.setAlbumBaselineId(id, media.maxIdForAlbum(id));
                }
            }
            prefs.setSelectedAlbums(ids);
            sendJson(out,200,new JSONObject().put("ok",true)); return;
        }
        if (r.path.equals("/photos") && r.method.equals("GET")) {
            JSONArray arr = new JSONArray();
            for (MediaRepo.Photo p : media.pending(prefs)) {
                JSONObject o = new JSONObject();
                o.put("id",p.id); o.put("name",p.name); o.put("mime",p.mime);
                o.put("album",p.bucketName); o.put("albumId",p.bucketId);
                o.put("dateAdded",p.dateAddedMs); o.put("size",p.size);
                arr.put(o);
            }
            sendJson(out,200,new JSONObject().put("photos",arr)); return;
        }
        if (r.path.startsWith("/photo/") && r.method.equals("GET")) {
            long id = Long.parseLong(r.path.substring("/photo/".length()));
            MediaRepo.Photo p = media.get(id);
            if (p == null) { send(out,404,"text/plain","not found".getBytes(StandardCharsets.UTF_8)); return; }
            if (!prefs.selectedAlbums().contains(p.bucketId) || p.id <= prefs.albumBaselineId(p.bucketId)) {
                send(out,403,"text/plain","not eligible".getBytes(StandardCharsets.UTF_8)); return;
            }
            try (InputStream in = media.open(p)) {
                if (in == null) { send(out,404,"text/plain",new byte[0]); return; }
                writeHeaders(out,200,p.mime==null?"application/octet-stream":p.mime,p.size);
                byte[] buf = new byte[64*1024]; int n;
                while ((n=in.read(buf))>0) out.write(buf,0,n);
                out.flush();
            }
            return;
        }
        if (r.path.equals("/ack") && r.method.equals("POST")) {
            JSONObject b = new JSONObject(new String(r.body, StandardCharsets.UTF_8));
            prefs.ack(String.valueOf(b.getLong("id")));
            sendJson(out,200,new JSONObject().put("ok",true)); return;
        }
        send(out,404,"text/plain","not found".getBytes(StandardCharsets.UTF_8));
    }

    private void sendJson(OutputStream out,int code,JSONObject o) throws Exception {
        send(out,code,"application/json; charset=utf-8",o.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void send(OutputStream out,int code,String type,byte[] body) throws Exception {
        writeHeaders(out,code,type,body.length); out.write(body); out.flush();
    }
    private void writeHeaders(OutputStream out,int code,String type,long len) throws Exception {
        String status = code==200?"OK":code==204?"No Content":code==403?"Forbidden":code==404?"Not Found":"OK";
        String h = "HTTP/1.1 "+code+" "+status+"\r\n"+
                "Content-Type: "+type+"\r\n"+
                "Content-Length: "+len+"\r\n"+
                "Access-Control-Allow-Origin: *\r\n"+
                "Access-Control-Allow-Headers: Content-Type, X-PhotoBridge-Token\r\n"+
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"+
                "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
    }
}
