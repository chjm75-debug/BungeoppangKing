package com.photobridge.app;
import java.net.*;
import java.util.*;
public class NetUtil {
    public static String wifiIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while(ifs.hasMoreElements()) {
                NetworkInterface ni=ifs.nextElement();
                if(!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> as=ni.getInetAddresses();
                while(as.hasMoreElements()) {
                    InetAddress a=as.nextElement();
                    if(a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch(Exception ignored){}
        return "확인 불가";
    }
}
