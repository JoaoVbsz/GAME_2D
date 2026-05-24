import javax.swing.*;
import javax.tools.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Launcher {

    private static final String OUT_DIR = "out";
    private static final String SRC_DIR = "src";
    private static final String GAME_PY = "../_Game/main.py";

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
        JFrame frame = new JFrame("Time out");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        JLabel titulo = new JLabel("TIME OUT", SwingConstants.CENTER);
        titulo.setFont(new Font("Monospaced", Font.BOLD, 48));
        titulo.setForeground(new Color(220, 100, 50));
        titulo.setBorder(BorderFactory.createEmptyBorder(30, 0, 20, 0));

        JButton btnSrv = criarBotao("1 — Servidor",  new Color(100, 220, 100));
        JButton btnCli = criarBotao("2 — Cliente",   new Color(100, 180, 255));

        btnSrv.addActionListener(e -> {
            String ip = ipLocal();
            JOptionPane.showMessageDialog(frame,
                "Servidor iniciando...\nSeu IP: " + ip + "\nPorta: 5555",
                "Servidor", JOptionPane.INFORMATION_MESSAGE);
            frame.dispose();
            liberarPorta(5555);
            rodar("servidor.ServidorJogo");
        });
        btnCli.addActionListener(e -> {
            String ip = (String) JOptionPane.showInputDialog(frame,
                "IP do servidor:", "Conectar", JOptionPane.PLAIN_MESSAGE, null, null, "");
            if (ip != null && !ip.isBlank()) {
                frame.dispose();
                new Thread(() -> {
                    try {
                        rodar("bridge.BridgeJogo", ip.trim());
                    } catch (Exception ex) { ex.printStackTrace(); }
                }).start();
                try { Thread.sleep(800); } catch (InterruptedException ignored) {}
                rodarPython("127.0.0.1");
            }
        });

        JPanel btns = new JPanel(new GridLayout(2, 1, 0, 14));
        btns.setOpaque(false);
        btns.setBorder(BorderFactory.createEmptyBorder(0, 50, 40, 50));
        btns.add(btnSrv);
        btns.add(btnCli);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(20, 20, 40));
        root.add(titulo, BorderLayout.NORTH);
        root.add(btns,   BorderLayout.CENTER);

        frame.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_1) btnSrv.doClick();
                if (e.getKeyCode() == KeyEvent.VK_2) btnCli.doClick();
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) System.exit(0);
            }
        });

        frame.setContentPane(root);
        frame.setSize(420, 320);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        frame.requestFocusInWindow();
    }

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
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (java.net.InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr.isLoopbackAddress() || addr.isLinkLocalAddress()) continue;
                    if (addr instanceof java.net.Inet4Address) return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private static void rodarPython(String ipServidor) {
        try {
            String python = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
            List<String> cmd = new ArrayList<>(Arrays.asList(python, GAME_PY, "--join", ipServidor));
            new ProcessBuilder(cmd)
                .directory(Paths.get("").toAbsolutePath().toFile())
                .inheritIO()
                .start()
                .waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Erro ao iniciar Python: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ─── Execução em subprocess ────────────────────────────────────────────────

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
                .start()
                .waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                "Erro ao iniciar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
