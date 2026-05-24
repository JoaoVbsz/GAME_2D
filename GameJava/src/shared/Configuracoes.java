package shared;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class Configuracoes {
    public static final int LARGURA = 736;
    public static final int ALTURA = 414;
    public static final int FPS = 60;
    public static final String TITULO = "Time out";
    public static final int PORTA = 5555;

    public static String getIpLocal() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    if (addr instanceof java.net.Inet4Address) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
