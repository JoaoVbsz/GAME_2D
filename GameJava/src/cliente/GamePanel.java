package cliente;

import shared.Configuracoes;
import shared.EstadoJogo;
import shared.IJogoServidor;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

public class GamePanel extends JPanel implements KeyListener {

    private final IJogoServidor servidor;
    private final ClienteCallback callback;
    private final int playerId;

    private final Set<Integer> pressed = new HashSet<>();
    private volatile boolean rodando = true;

    private BufferedImage imgFundo, imgJ1, imgJ2, imgIni, imgVoa, imgFireball;

    private static final int[] KEY_MAP_CODES = {
        KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_UP,
        KeyEvent.VK_K,
        KeyEvent.VK_A, KeyEvent.VK_D, KeyEvent.VK_W, KeyEvent.VK_S,
        KeyEvent.VK_V
    };
    private static final String[] KEY_MAP_NAMES = {
        "RIGHT", "LEFT", "UP", "K", "A", "D", "W", "S", "V"
    };

    public GamePanel(IJogoServidor servidor, ClienteCallback callback, int playerId, String assetsDir) {
        this.servidor = servidor;
        this.callback = callback;
        this.playerId = playerId;

        setPreferredSize(new Dimension(Configuracoes.LARGURA, Configuracoes.ALTURA));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        carregarImagens(assetsDir);
        iniciarLoop();
    }

    private void carregarImagens(String assetsDir) {
        String img = assetsDir + "/imagens/";
        imgFundo    = escalar(lerImg(img + "fundo_game.png"),      Configuracoes.LARGURA, Configuracoes.ALTURA);
        imgJ1       = escalar(lerImg(img + "mago_player.png"),     100, 100);
        imgJ2       = escalar(lerImg(img + "mago2_novo.png"),      100, 100);
        imgIni      = flipH(escalar(lerImg(img + "inimigo_novo.png"),    100, 100));
        imgVoa      = flipH(escalar(lerImg(img + "inimigo_voador.png"),  80,  80));
        imgFireball = escalar(lerImg(img + "Fireball1.png"),        40,  40);
    }

    private BufferedImage lerImg(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage escalar(BufferedImage src, int w, int h) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private BufferedImage flipH(BufferedImage src) {
        if (src == null) return null;
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-src.getWidth(), 0);
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(src, null);
    }

    private void iniciarLoop() {
        Thread t = new Thread(() -> {
            long dtMs = 1000 / Configuracoes.FPS;
            while (rodando) {
                long t0 = System.currentTimeMillis();
                enviarInput();
                repaint();
                long elapsed = System.currentTimeMillis() - t0;
                if (elapsed < dtMs) {
                    try { Thread.sleep(dtMs - elapsed); } catch (InterruptedException ignored) {}
                }
            }
        }, "RenderLoop");
        t.setDaemon(true);
        t.start();
    }

    private void enviarInput() {
        String[] teclas;
        synchronized (pressed) {
            teclas = pressed.stream()
                .map(code -> {
                    for (int i = 0; i < KEY_MAP_CODES.length; i++) {
                        if (KEY_MAP_CODES[i] == code) return KEY_MAP_NAMES[i];
                    }
                    return null;
                })
                .filter(s -> s != null)
                .toArray(String[]::new);
        }
        try {
            servidor.processarInput(playerId, teclas);
        } catch (RemoteException e) {
            rodando = false;
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        EstadoJogo estado = callback.getEstado();

        if (estado == null) {
            g.setColor(new Color(20, 20, 40));
            g.fillRect(0, 0, getWidth(), getHeight());
            desenharTexto(g, "Aguardando 2o jogador...", Configuracoes.LARGURA / 2, Configuracoes.ALTURA / 2 - 20, 22, Color.WHITE);
            return;
        }

        // Fundo
        if (imgFundo != null) g.drawImage(imgFundo, 0, 0, null);
        else { g.setColor(new Color(20, 20, 40)); g.fillRect(0, 0, getWidth(), getHeight()); }

        // Inimigos
        for (int[] e : estado.inimigos) {
            if (imgIni != null) g.drawImage(imgIni, e[0], e[1], null);
            else { g.setColor(Color.RED); g.fillRect(e[0], e[1], 100, 100); }
        }
        for (int[] e : estado.voadores) {
            if (imgVoa != null) g.drawImage(imgVoa, e[0], e[1], null);
            else { g.setColor(Color.ORANGE); g.fillRect(e[0], e[1], 80, 80); }
        }

        // Projéteis
        for (int[] p : estado.projJ1) {
            if (imgFireball != null) g.drawImage(imgFireball, p[0], p[1], null);
            else { g.setColor(Color.YELLOW); g.fillOval(p[0], p[1], 20, 20); }
        }
        for (int[] p : estado.projJ2) {
            if (imgFireball != null) g.drawImage(imgFireball, p[0], p[1], null);
            else { g.setColor(Color.CYAN); g.fillOval(p[0], p[1], 20, 20); }
        }

        // Jogadores
        if (imgJ1 != null) g.drawImage(imgJ1, estado.j1x, estado.j1y, null);
        else { g.setColor(Color.GREEN); g.fillRect(estado.j1x, estado.j1y, 100, 100); }
        if (imgJ2 != null) g.drawImage(imgJ2, estado.j2x, estado.j2y, null);
        else { g.setColor(Color.BLUE); g.fillRect(estado.j2x, estado.j2y, 100, 100); }

        if (!estado.fimJogo) {
            // HUD J1
            desenharTexto(g, "J1 Pts: " + estado.pontos[0], 20, 20, 16, Color.WHITE);
            String t1 = estado.j1recarregando ? "Recarregando..." : "Tiros: " + estado.j1tiros + "/5";
            desenharTexto(g, t1, 20, 42, 14, Color.WHITE);

            // HUD J2
            String ptJ2 = "J2 Pts: " + estado.pontos[1];
            desenharTexto(g, ptJ2, Configuracoes.LARGURA - 130, 20, 16, Color.WHITE);
            String t2 = estado.j2recarregando ? "Recarregando..." : "Tiros: " + estado.j2tiros + "/5";
            desenharTexto(g, t2, Configuracoes.LARGURA - 130, 42, 14, Color.WHITE);

            // Identificação
            String pid = "Voce e J" + (playerId + 1);
            desenharTexto(g, pid, Configuracoes.LARGURA / 2 - 40, 8, 12, new Color(200, 200, 100));
        } else {
            // FIM DE JOGO
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, getWidth(), getHeight());
            desenharTexto(g, "FIM DE JOGO", Configuracoes.LARGURA / 2 - 100, Configuracoes.ALTURA / 2, 36, new Color(220, 50, 50));
        }
    }

    private void desenharTexto(Graphics2D g, String texto, int x, int y, int tamanho, Color cor) {
        g.setFont(new Font("Monospaced", Font.BOLD, tamanho));
        // sombra
        g.setColor(Color.BLACK);
        g.drawString(texto, x + 1, y + 1);
        g.setColor(cor);
        g.drawString(texto, x, y);
    }

    public void parar() {
        rodando = false;
    }

    @Override public void keyPressed(KeyEvent e)  { synchronized (pressed) { pressed.add(e.getKeyCode()); } }
    @Override public void keyReleased(KeyEvent e) { synchronized (pressed) { pressed.remove(e.getKeyCode()); } }
    @Override public void keyTyped(KeyEvent e)    {}
}
