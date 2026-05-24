import javax.swing.*;
import javax.tools.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Launcher {

    private static final String OUT_DIR = "out";
    private static final String SRC_DIR = "src";
    private static JFrame frame;
    private static JPanel cardPanel;
    private static CardLayout cardLayout;
    private static ClassLoader gameLoader;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        if (!compilar()) {
            JOptionPane.showMessageDialog(null,
                "Erro de compilacao. Verifique o terminal.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        mostrarMenu();
    }

    // ─── Compilação via javax.tools ────────────────────────────────────────────

    private static boolean compilar() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("JavaCompiler nao disponivel. Use JDK (nao JRE).");
            return false;
        }

        List<String> fontes = new ArrayList<>();
        coletarJava(Paths.get(SRC_DIR), fontes);
        if (fontes.isEmpty()) {
            System.err.println("Nenhum .java encontrado em " + SRC_DIR + "/");
            return false;
        }

        Files.createDirectories(Paths.get(OUT_DIR));

        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, null)) {
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromStrings(fontes);
            List<String> opts = Arrays.asList("-d", OUT_DIR, "-encoding", "UTF-8");
            boolean ok = compiler.getTask(null, fm, diags, opts, null, units).call();
            for (Diagnostic<?> d : diags.getDiagnostics())
                if (d.getKind() == Diagnostic.Kind.ERROR)
                    System.err.println(d.toString());
            if (ok) gameLoader = new URLClassLoader(
                new URL[]{ Paths.get(OUT_DIR).toAbsolutePath().toUri().toURL() },
                Launcher.class.getClassLoader()
            );
            return ok;
        }
    }

    private static void coletarJava(Path dir, List<String> lista) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
             .filter(p -> p.toString().endsWith(".java"))
             .map(Path::toString)
             .forEach(lista::add);
    }

    // ─── Menu ──────────────────────────────────────────────────────────────────

    private static void mostrarMenu() {
        frame = new JFrame("Time out");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setSize(420, 320);
        frame.setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);

        cardPanel.add(criarTelaPrincipal(), "principal");
        cardPanel.add(criarTelaMultiplayer(), "multiplayer");

        frame.setContentPane(cardPanel);

        // Gerenciador de teclas global para atalhos
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() != KeyEvent.KEY_PRESSED) return false;
                
                String telaAtual = getVisibleCardName();
                int code = e.getKeyCode();

                if ("principal".equals(telaAtual)) {
                    if (code == KeyEvent.VK_1 || code == KeyEvent.VK_NUMPAD1) {
                        clicarBotao("1 Jogador");
                        return true;
                    }
                    if (code == KeyEvent.VK_2 || code == KeyEvent.VK_NUMPAD2) {
                        clicarBotao("2 Jogadores");
                        return true;
                    }
                    if (code == KeyEvent.VK_ESCAPE) {
                        System.exit(0);
                        return true;
                    }
                } else if ("multiplayer".equals(telaAtual)) {
                    if (code == KeyEvent.VK_S) {
                        clicarBotao("Servidor");
                        return true;
                    }
                    if (code == KeyEvent.VK_C) {
                        clicarBotao("Cliente");
                        return true;
                    }
                    if (code == KeyEvent.VK_ESCAPE) {
                        clicarBotao("Voltar");
                        return true;
                    }
                }
                return false;
            }
        });

        frame.setVisible(true);
    }

    private static String getVisibleCardName() {
        for (Component comp : cardPanel.getComponents()) {
            if (comp.isVisible()) return comp.getName();
        }
        return "principal";
    }

    private static void clicarBotao(String texto) {
        JButton btn = buscarBotao(cardPanel, texto);
        if (btn != null) btn.doClick();
    }

    private static JButton buscarBotao(Container container, String texto) {
        for (Component c : container.getComponents()) {
            if (c instanceof JButton && texto.equals(((JButton) c).getText())) {
                return (JButton) c;
            } else if (c instanceof Container) {
                JButton b = buscarBotao((Container) c, texto);
                if (b != null) return b;
            }
        }
        return null;
    }

    private static JPanel criarTelaPrincipal() {
        JPanel p = new JPanel(new BorderLayout());
        p.setName("principal");
        p.setBackground(new Color(20, 20, 40));

        JLabel titulo = new JLabel("TIME OUT", SwingConstants.CENTER);
        titulo.setFont(new Font("Monospaced", Font.BOLD, 48));
        titulo.setForeground(new Color(220, 100, 50));
        titulo.setBorder(BorderFactory.createEmptyBorder(30, 0, 20, 0));

        JButton btn1P = criarBotao("1 Jogador", new Color(100, 220, 100));
        JButton btn2P = criarBotao("2 Jogadores", new Color(100, 180, 255));

        btn1P.addActionListener(e -> {
            new Thread(() -> {
                liberarPorta(5555);
                iniciarServidorEmbutido();
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                iniciarClienteDireto("127.0.0.1");
                SwingUtilities.invokeLater(() -> frame.dispose());
            }).start();
        });

        btn2P.addActionListener(e -> cardLayout.show(cardPanel, "multiplayer"));

        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 14));
        btns.setOpaque(false);
        btns.setBorder(BorderFactory.createEmptyBorder(0, 50, 40, 50));
        btns.add(btn1P);
        btns.add(btn2P);

        p.add(titulo, BorderLayout.NORTH);
        p.add(btns, BorderLayout.CENTER);
        return p;
    }

    private static JPanel criarTelaMultiplayer() {
        JPanel p = new JPanel(new BorderLayout());
        p.setName("multiplayer");
        p.setBackground(new Color(20, 20, 40));

        JLabel titulo = new JLabel("2 JOGADORES", SwingConstants.CENTER);
        titulo.setFont(new Font("Monospaced", Font.BOLD, 40));
        titulo.setForeground(new Color(220, 100, 50));
        titulo.setBorder(BorderFactory.createEmptyBorder(30, 0, 20, 0));

        JButton btnSrv = criarBotao("Servidor", new Color(100, 220, 100));
        JButton btnCli = criarBotao("Cliente", new Color(100, 180, 255));
        JButton btnVol = criarBotao("Voltar", new Color(150, 150, 150));

        btnSrv.addActionListener(e -> {
            String ip = ipLocal();
            JOptionPane.showMessageDialog(frame,
                "Seu IP: " + ip + " | Porta: 5555\nAguardando cliente...",
                "Servidor", JOptionPane.INFORMATION_MESSAGE);
            liberarPorta(5555);
            iniciarServidorEmbutido();
            frame.dispose();
        });

        btnCli.addActionListener(e -> {
            String ip = (String) JOptionPane.showInputDialog(frame,
                "IP do servidor:", "Conectar", JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (ip != null && !ip.isBlank()) {
                rodar("cliente.ClienteJogo", ip.trim());
                frame.dispose();
            }
        });

        btnVol.addActionListener(e -> cardLayout.show(cardPanel, "principal"));

        JPanel btns = new JPanel(new GridLayout(3, 1, 0, 10));
        btns.setOpaque(false);
        btns.setBorder(BorderFactory.createEmptyBorder(0, 50, 30, 50));
        btns.add(btnSrv);
        btns.add(btnCli);
        btns.add(btnVol);

        p.add(titulo, BorderLayout.NORTH);
        p.add(btns, BorderLayout.CENTER);
        return p;
    }

    // ─── Lógica de Execução ────────────────────────────────────────────────────

    private static void abrirFirewall() {
        try {
            new ProcessBuilder("netsh", "advfirewall", "firewall", "delete", "rule", "name=TimeOut Game")
                .redirectErrorStream(true).start().waitFor();
            new ProcessBuilder("netsh", "advfirewall", "firewall", "add", "rule",
                "name=TimeOut Game", "protocol=TCP", "dir=in", "localport=5555-5557", "action=allow")
                .redirectErrorStream(true).start().waitFor();
            System.out.println("[*] Regra de firewall criada (5555-5557)");
        } catch (Exception e) {
            System.err.println("[!] Firewall: " + e.getMessage() + " (abra as portas manualmente)");
        }
    }

    private static void iniciarServidorEmbutido() {
        new Thread(() -> {
            try {
                abrirFirewall();
                String ip = ipLocal();
                System.setProperty("java.rmi.server.hostname", ip);
                Object srv = gameLoader.loadClass("servidor.ServidorJogo")
                                       .getDeclaredConstructor().newInstance();
                java.rmi.registry.Registry reg = java.rmi.registry.LocateRegistry.createRegistry(5555);
                reg.rebind("ServidorJogo", (java.rmi.Remote) srv);
                System.out.println("[*] Servidor embutido rodando em " + ip + ":5555");
                Thread.currentThread().join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "servidor-thread").start();
    }

    private static void iniciarClienteDireto(String ip) {
        SwingUtilities.invokeLater(() -> {
            try {
                gameLoader.loadClass("cliente.ClienteJogo")
                          .getDeclaredConstructor(String.class, boolean.class)
                          .newInstance(ip, true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Erro ao iniciar: " + e.getMessage());
            }
        });
    }

    private static void rodar(String classeMain, String... extras) {
        try {
            String javaExe = ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");

            List<String> cmd = new ArrayList<>();
            cmd.add(javaExe);
            cmd.add("-cp");
            cmd.add(OUT_DIR);
            cmd.add(classeMain);
            Collections.addAll(cmd, extras);

            new ProcessBuilder(cmd)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Erro ao iniciar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── Utilitários ───────────────────────────────────────────────────────────

    private static JButton criarBotao(String texto, Color cor) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Monospaced", Font.BOLD, 18));
        b.setForeground(cor);
        b.setBackground(new Color(35, 35, 60));
        b.setFocusPainted(false);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(cor.darker(), 2),
            BorderFactory.createEmptyBorder(12, 24, 12, 24)
        ));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(new Color(55, 55, 85)); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(new Color(35, 35, 60)); }
        });
        return b;
    }

    private static void liberarPorta(int porta) {
        try {
            Process netstat = new ProcessBuilder("netstat", "-ano")
                .redirectErrorStream(true).start();
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(netstat.getInputStream()))) {
                String linha;
                while ((linha = br.readLine()) != null) {
                    if (linha.contains(":" + porta) && linha.contains("LISTENING")) {
                        String[] partes = linha.trim().split("\\s+");
                        String pid = partes[partes.length - 1];
                        new ProcessBuilder("taskkill", "/PID", pid, "/F")
                            .redirectErrorStream(true).start().waitFor();
                        System.out.println("[Launcher] Processo " + pid + " encerrado (porta " + porta + ").");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Launcher] Falha ao liberar porta: " + e.getMessage());
        }
    }

    private static String ipLocal() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(java.net.InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
