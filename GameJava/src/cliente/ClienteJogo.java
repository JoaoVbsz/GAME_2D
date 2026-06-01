package cliente;

import shared.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.awt.event.*;
import java.rmi.registry.*;
import java.rmi.server.*;
import java.util.*;
import java.util.List;

/**
 * Cliente Java Swing para o jogo Time Out.
 * Conecta via RMI e renderiza o estado recebido via JSON.
 */
public class ClienteJogo extends UnicastRemoteObject implements IClienteCallback {
    private IJogoServidor srv;
    private int playerId = -1;
    private volatile String estadoJson = "";
    private final Set<String> teclasAtivas = Collections.synchronizedSet(new HashSet<>());
    private boolean muted = false;
    private final boolean singlePlayer;

    private BufferedImage imgJ1, imgJ2, imgIni, imgVoa, imgFireball, imgFundo;

    private static final int PORTA_CALLBACK = 5557;

    public ClienteJogo(String ip, boolean singlePlayer) throws Exception {
        super(PORTA_CALLBACK);
        this.singlePlayer = singlePlayer;
        String ipLocal = Configuracoes.getIpLocal();
        System.setProperty("java.rmi.server.hostname", ipLocal);
        System.out.println("[*] IP local do cliente: " + ipLocal);
        System.out.println("[*] Conectando ao servidor: " + ip + ":" + Configuracoes.PORTA);
        carregarImagens();
        try {
            Registry reg = LocateRegistry.getRegistry(ip, Configuracoes.PORTA);
            srv = (IJogoServidor) reg.lookup("ServidorJogo");

            playerId = srv.conectar(this, singlePlayer);
            if (playerId == -1) {
                System.err.println("[!] Servidor cheio.");
                JOptionPane.showMessageDialog(null, "Servidor cheio ou erro na conexao.");
                System.exit(0);
            }
            System.out.println("[+] Conectado como J" + playerId);
            iniciarUI();
            iniciarLoopInput();
        } catch (Exception e) {
            System.err.println("[!] Erro ao conectar: " + e);
            JOptionPane.showMessageDialog(null, "Erro ao conectar: " + e.getMessage());
            System.exit(0);
        }
    }

    private static final String DIR_IMG = "assets/imagens/";

    private void carregarImagens() {
        imgJ1       = carregarEscalar("mago_player.png",    100, 100, false);
        imgJ2       = carregarEscalar("mago2_novo.png",     100, 100, false);
        imgIni      = carregarEscalar("inimigo_novo.png",   100, 100, true);
        imgVoa      = carregarEscalar("inimigo_voador.png",  80,  80, true);
        imgFireball = carregarEscalar("Fireball1.png",       40,  40, false);
        imgFundo    = carregarEscalar("novo_fundo.png", Configuracoes.LARGURA, Configuracoes.ALTURA, false);
        if (imgFundo == null)
            imgFundo = carregarEscalar("fundo_game.png", Configuracoes.LARGURA, Configuracoes.ALTURA, false);
    }

    private BufferedImage carregarEscalar(String nome, int w, int h, boolean flipH) {
        try {
            BufferedImage src = ImageIO.read(new File(DIR_IMG + nome));
            BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (flipH) {
                g.drawImage(src, w, 0, -w, h, null);
            } else {
                g.drawImage(src, 0, 0, w, h, null);
            }
            g.dispose();
            return dst;
        } catch (Exception e) {
            return null;
        }
    }

    private void drawSprite(Graphics2D g, BufferedImage img, int x, int y, int w, int h, Color fallback) {
        if (img != null) {
            g.drawImage(img, x, y, w, h, null);
        } else {
            g.setColor(fallback);
            g.fillRect(x, y, w, h);
        }
    }

    private void iniciarUI() {
        JFrame frame = new JFrame("Time Out");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JPanel painel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                desenhar((Graphics2D) g);
            }
        };
        painel.setPreferredSize(new Dimension(Configuracoes.LARGURA, Configuracoes.ALTURA));
        painel.setBackground(new Color(20, 20, 40));
        frame.add(painel);
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String k = getNomeTecla(e.getKeyCode());
                if (k != null) teclasAtivas.add(k);
                if (e.getKeyCode() == KeyEvent.VK_U) muted = !muted;
            }

            @Override
            public void keyReleased(KeyEvent e) {
                String k = getNomeTecla(e.getKeyCode());
                if (k != null) teclasAtivas.remove(k);
            }
        });

        javax.swing.Timer timer = new javax.swing.Timer(1000 / Configuracoes.FPS, e -> painel.repaint());
        timer.start();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    if (srv != null && playerId != -1) {
                        srv.desconectar(playerId);
                    }
                } catch (Exception ex) {}
            }
        });

        frame.setVisible(true);
    }

    private String getNomeTecla(int code) {
        switch (code) {
            case KeyEvent.VK_RIGHT: return "RIGHT";
            case KeyEvent.VK_LEFT:  return "LEFT";
            case KeyEvent.VK_UP:    return "UP";
            case KeyEvent.VK_K:     return "K";
            case KeyEvent.VK_D:     return "D";
            case KeyEvent.VK_A:     return "A";
            case KeyEvent.VK_W:     return "W";
            case KeyEvent.VK_S:     return "S";
            case KeyEvent.VK_V:     return "V";
            default: return null;
        }
    }

    private void iniciarLoopInput() {
        new Thread(() -> {
            while (true) {
                try {
                    List<String> teclas;
                    synchronized (teclasAtivas) {
                        teclas = new ArrayList<>(teclasAtivas);
                    }
                    srv.processarInput(playerId, teclas);
                    Thread.sleep(1000 / 30);
                } catch (Exception e) {
                    System.err.println("Conexao perdida com servidor.");
                    break;
                }
            }
        }).start();
    }

    @Override
    public void receberJson(String json) {
        this.estadoJson = json;
    }

    private void desenhar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String json = estadoJson;
        if (json == null || json.isEmpty()) {
            if (!singlePlayer) {
                g.setColor(new Color(20, 20, 40));
                g.fillRect(0, 0, Configuracoes.LARGURA, Configuracoes.ALTURA);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 24));
                String msg = "Aguardando J2...";
                int mw = g.getFontMetrics().stringWidth(msg);
                g.drawString(msg, (Configuracoes.LARGURA - mw) / 2, Configuracoes.ALTURA / 2);
            }
            return;
        }

        try {
            // J1
            String j1Str = getObj(json, "j1");
            int j1x = getInt(j1Str, "x");
            int j1y = getInt(j1Str, "y");
            int j1tiros = getInt(j1Str, "tiros");
            boolean j1Rec = getBool(j1Str, "recarregando");

            // J2
            String j2Str = getObj(json, "j2");
            int j2x = getInt(j2Str, "x");
            int j2y = getInt(j2Str, "y");
            int j2tiros = getInt(j2Str, "tiros");
            boolean j2Rec = getBool(j2Str, "recarregando");

            // Listas
            List<Point> inimigos = getListaPoints(json, "inimigos");
            List<Point> voadores = getListaPoints(json, "voadores");
            List<Point> p1 = getListaPoints(json, "proj_j1");
            List<Point> p2 = getListaPoints(json, "proj_j2");

            // Pontos
            String ptsStr = getArr(json, "pontos");
            String[] ptsArr = ptsStr.split(",");
            int p1Pts = ptsArr.length > 0 ? Integer.parseInt(ptsArr[0].trim()) : 0;
            int p2Pts = ptsArr.length > 1 ? Integer.parseInt(ptsArr[1].trim()) : 0;

            boolean fim = getBool(json, "fim_jogo");
            int jogadores = getInt(json, "jogadores");

            // Tela de espera quando só 1 jogador conectado (apenas multiplayer)
            if (!singlePlayer && jogadores < 2) {
                g.setColor(new Color(20, 20, 40));
                g.fillRect(0, 0, Configuracoes.LARGURA, Configuracoes.ALTURA);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Monospaced", Font.BOLD, 24));
                String msg = "Aguardando J2...";
                int mw = g.getFontMetrics().stringWidth(msg);
                g.drawString(msg, (Configuracoes.LARGURA - mw) / 2, Configuracoes.ALTURA / 2);
                g.setFont(new Font("Monospaced", Font.PLAIN, 14));
                String sub = "Voce e J" + (playerId + 1);
                int sw = g.getFontMetrics().stringWidth(sub);
                g.drawString(sub, (Configuracoes.LARGURA - sw) / 2, Configuracoes.ALTURA / 2 + 35);
                return;
            }

            // Fundo
            if (imgFundo != null) g.drawImage(imgFundo, 0, 0, null);
            else { g.setColor(new Color(20, 20, 40)); g.fillRect(0, 0, Configuracoes.LARGURA, Configuracoes.ALTURA); }

            // Inimigos terrestres (J1)
            for (Point p : inimigos) drawSprite(g, imgIni, p.x, p.y, 100, 100, Color.RED);

            // Inimigos voadores e elementos de J2 (só multiplayer)
            if (!singlePlayer) {
                for (Point p : voadores) drawSprite(g, imgVoa, p.x, p.y, 80, 80, Color.ORANGE);
                for (Point p : p2) drawSprite(g, imgFireball, p.x - 20, p.y - 20, 40, 40, Color.YELLOW);
            }

            // Projéteis J1
            for (Point p : p1) drawSprite(g, imgFireball, p.x - 20, p.y - 20, 40, 40, Color.YELLOW);

            // Jogadores
            drawSprite(g, imgJ1, j1x, j1y, 100, 100, Color.BLUE);
            if (!singlePlayer) drawSprite(g, imgJ2, j2x, j2y, 100, 100, Color.GREEN);

            // HUD
            g.setColor(Color.WHITE);
            g.setFont(new Font("Monospaced", Font.BOLD, 16));
            
            // J1 HUD
            g.drawString("J1 Pts: " + p1Pts, 20, 30);
            g.drawString("Tiros: " + (j1Rec ? "Recarregando..." : j1tiros + "/5"), 20, 50);

            // J2 HUD (só multiplayer)
            if (!singlePlayer) {
                String j2TirosMsg = "Tiros: " + (j2Rec ? "Recarregando..." : j2tiros + "/5");
                int w2 = g.getFontMetrics().stringWidth(j2TirosMsg);
                g.drawString("J2 Pts: " + p2Pts, Configuracoes.LARGURA - 140, 30);
                g.drawString(j2TirosMsg, Configuracoes.LARGURA - w2 - 20, 50);

                String msgCentro = "Voce e " + (playerId == 0 ? "J1" : "J2");
                int wc = g.getFontMetrics().stringWidth(msgCentro);
                g.drawString(msgCentro, (Configuracoes.LARGURA - wc) / 2, 30);
            }

            if (fim) {
                g.setColor(Color.RED);
                g.setFont(new Font("Monospaced", Font.BOLD, 60));
                String text = "FIM DE JOGO";
                int w = g.getFontMetrics().stringWidth(text);
                g.drawString(text, (Configuracoes.LARGURA - w) / 2, Configuracoes.ALTURA / 2 + 20);
            }

        } catch (Exception e) {
            // Ignorar erros de parse para nao quebrar o loop de render
        }
    }

    // --- Helpers de Parse JSON ---
    private String getObj(String json, String key) {
        String s = "\"" + key + "\":{";
        int start = json.indexOf(s);
        if (start == -1) return "";
        start += s.length() - 1;
        int count = 1, i = start + 1;
        while (count > 0 && i < json.length()) {
            if (json.charAt(i) == '{') count++;
            if (json.charAt(i) == '}') count--;
            i++;
        }
        return json.substring(start, i);
    }

    private String getArr(String json, String key) {
        String s = "\"" + key + "\":[";
        int start = json.indexOf(s);
        if (start == -1) return "";
        start += s.length() - 1;
        int count = 1, i = start + 1;
        while (count > 0 && i < json.length()) {
            if (json.charAt(i) == '[') count++;
            if (json.charAt(i) == ']') count--;
            i++;
        }
        return json.substring(start + 1, i - 1);
    }

    private int getInt(String json, String key) {
        String s = "\"" + key + "\":";
        int start = json.indexOf(s);
        if (start == -1) return 0;
        start += s.length();
        int end = json.indexOf(",", start);
        if (end == -1 || end > json.indexOf("}", start)) end = json.indexOf("}", start);
        if (end == -1) return 0;
        return Integer.parseInt(json.substring(start, end).trim());
    }

    private boolean getBool(String json, String key) {
        String s = "\"" + key + "\":";
        int start = json.indexOf(s);
        if (start == -1) return false;
        start += s.length();
        int end = json.indexOf(",", start);
        if (end == -1 || end > json.indexOf("}", start)) end = json.indexOf("}", start);
        if (end == -1) return false;
        return Boolean.parseBoolean(json.substring(start, end).trim());
    }

    private List<Point> getListaPoints(String json, String key) {
        List<Point> lista = new ArrayList<>();
        String arr = getArr(json, key);
        if (arr.isEmpty()) return lista;
        String[] itens = arr.split("\\},\\{");
        for (String item : itens) {
            item = item.replace("{", "").replace("}", "");
            if (item.isEmpty()) continue;
            lista.add(new Point(getInt("{" + item + "}", "x"), getInt("{" + item + "}", "y")));
        }
        return lista;
    }

    public static void main(String[] args) throws Exception {
        String ip = args.length > 0 ? args[0] : "127.0.0.1";
        boolean solo = args.length > 1 && args[1].equals("solo");
        new ClienteJogo(ip, solo);
    }
}
