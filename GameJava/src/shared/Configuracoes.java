package shared;

public class Configuracoes {
    public static final int LARGURA = 736;
    public static final int ALTURA = 414;
    public static final int FPS = 60;
    public static final int PORTA = 5555;

    public static String getIpLocal() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
