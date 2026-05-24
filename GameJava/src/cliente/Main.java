package cliente;

import shared.Configuracoes;
import shared.IJogoServidor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Main {

    private static JFrame frame;
    private static String assetsDir;

    public static void main(String[] args) throws Exception {
        // Pasta de assets: argumento ou padrão relativo
        assetsDir = (args.length > 0) ? args[0] : "../_Game/assets";

        String ip = Configuracoes.getIpLocal();
        System.setProperty("java.rmi.server.hostname", ip);

        if (args.length > 0 && args[0].equals("--host")) {
            iniciarComoHost(ip);
        } else if (args.length > 1 && args[0].equals("--join")) {
            conectar(args[1], ip);
        } else {
            mostrarMenu(ip);
        }
    }

    private static void mostrarMenu(String ipLocal) {
        frame = new JFrame(Configuracoes.TITULO);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(20, 20, 40));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Monospaced", Font.BOLD, 48));
                g.setColor(new Color(220, 100, 50));
                g.drawString("TIME OUT", Configuracoes.LARGURA / 2 - 130, 100);
                g.setFont(new Font("Monospaced", Font.BOLD, 22));
                g.setColor(new Color(100, 220, 100));
                g.drawString("[H]  Hospedar partida", Configuracoes.LARGURA / 2 - 140, 200);
                g.setColor(new Color(100, 180, 255));
                g.drawString("[E]  Entrar na partida", Configuracoes.LARGURA / 2 - 140, 245);
                g.setColor(new Color(130, 130, 130));
                g.drawString("[ESC]  Sair", Configuracoes.LARGURA / 2 - 70, 310);
            }
        };
        panel.setPreferredSize(new Dimension(Configuracoes.LARGURA, Configuracoes.ALTURA));
        panel.setFocusable(true);

        panel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_H) {
                    frame.dispose();
                    iniciarComoHost(ipLocal);
                } else if (e.getKeyCode() == KeyEvent.VK_E) {
                    String ip = JOptionPane.showInputDialog(frame, "IP do servidor:", "Conectar", JOptionPane.PLAIN_MESSAGE);
                    if (ip != null && !ip.isBlank()) {
                        frame.dispose();
                        conectar(ip.trim(), ipLocal);
                    }
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
        });

        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        panel.requestFocusInWindow();
    }

    private static void iniciarComoHost(String ipLocal) {
        mostrarInfo("Iniciando servidor...", "IP: " + ipLocal + "  Porta: " + Configuracoes.PORTA);
        new Thread(() -> {
            try {
                servidor.ServidorJogo.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ServidorThread").start();

        // Pequena espera para o registry subir
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        conectar("127.0.0.1", ipLocal);
    }

    private static void conectar(String ipServidor, String ipLocal) {
        mostrarInfo("Conectando...", ipServidor);
        try {
            Registry reg = LocateRegistry.getRegistry(ipServidor, Configuracoes.PORTA);
            IJogoServidor servidor = (IJogoServidor) reg.lookup("ServidorJogo");

            ClienteCallback callback = new ClienteCallback();
            int pid = servidor.conectar(callback);

            System.out.println("[+] Conectado como J" + (pid + 1));
            abrirJogo(servidor, callback, pid);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Erro ao conectar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            mostrarMenu(ipLocal);
        }
    }

    private static void mostrarInfo(String linha1, String linha2) {
        if (frame != null) frame.dispose();
        frame = new JFrame(Configuracoes.TITULO);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(20, 20, 40));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(new Font("Monospaced", Font.BOLD, 22));
                g.setColor(new Color(200, 220, 255));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(linha1, (getWidth() - fm.stringWidth(linha1)) / 2, getHeight() / 2 - 20);
                g.setFont(new Font("Monospaced", Font.PLAIN, 16));
                g.setColor(new Color(160, 160, 200));
                fm = g.getFontMetrics();
                g.drawString(linha2, (getWidth() - fm.stringWidth(linha2)) / 2, getHeight() / 2 + 20);
            }
        };
        p.setPreferredSize(new Dimension(Configuracoes.LARGURA, Configuracoes.ALTURA));
        frame.setContentPane(p);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void abrirJogo(IJogoServidor srv, ClienteCallback cb, int pid) {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.dispose();
            frame = new JFrame(Configuracoes.TITULO);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setResizable(false);

            GamePanel gamePanel = new GamePanel(srv, cb, pid, assetsDir);
            frame.setContentPane(gamePanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            gamePanel.requestFocusInWindow();

            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) {
                    gamePanel.parar();
                    try { srv.desconectar(pid); } catch (Exception ignored) {}
                    System.exit(0);
                }
            });
        });
    }
}
