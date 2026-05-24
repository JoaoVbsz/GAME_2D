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
    private static final String ASSETS  = "../_Game/assets";

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

        btnSrv.addActionListener(e -> { frame.dispose(); rodar("servidor.ServidorJogo"); });
        btnCli.addActionListener(e -> { frame.dispose(); rodar("cliente.Main", ASSETS);  });

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
