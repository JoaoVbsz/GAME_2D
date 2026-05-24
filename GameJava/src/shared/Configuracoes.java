package shared;

import java.net.Socket;

public class Configuracoes {
    public static final int LARGURA = 736;
    public static final int ALTURA = 414;
    public static final int FPS = 60;
    public static final String TITULO = "Time out";
    public static final int PORTA = 5555;

    public static String getIpLocal() {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress("8.8.8.8", 80));
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
