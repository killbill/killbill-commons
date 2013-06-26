package com.ning.billing;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Hostname {

    public static String get() {
        try {
            final InetAddress addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "hostname-unknown";
        }
    }

}
