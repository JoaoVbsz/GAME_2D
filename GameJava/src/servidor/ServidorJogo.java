package servidor;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class ServidorJogo {
    // Constantes do Jogo
    private static final int LARGURA = 736;
    private static final int ALTURA = 414;
    private static final int PORTA = 5555;
    private static final double GRAVIDADE = 1;
    private static final double VEL_PULO = -15;
    private static final double ACEL_J1 = 0.5;
    private static final double FRICAO_J1 = 0.85;
    private static final int VEL_J2 = 4;
    private static final double RECARGA_MS = 2000.0;
    private static final int MAX_TIROS = 5;
    private static final int TICK_RATE = 30;
    private static final int VEL_PROJ = 7;
    private static final int TAM_PROJ = 20;
    private static final int TAM_J = 100;
    private static final int TAM_VOA = 80;

    // Estado do Servidor
    private final List<ClientHandler> clientes = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private boolean rodando = false;
    private boolean fimJogo = false;

    // Estado do Jogo
    private double j1x, j1y, j1vx, j1vy;
    private boolean j1pulando, j1recarregando;
    private int j1tiros;
    private long j1recargaT;

    private double j2x, j2y;
    private boolean j2recarregando;
    private int j2tiros;
    private long j2recargaT;

    private final List<double[]> inimigos = new ArrayList<>(); // [x, y, id]
    private final List<double[]> voadores = new ArrayList<>(); // [x, yBase, y, tick, id]
    private final List<double[]> projJ1 = new ArrayList<>();   // [x, y, id]
    private final List<double[]> projJ2 = new ArrayList<>();   // [x, y, id]

    private int[] pontos = {0, 0};
    private int nid = 0;
    private int tIni = 0, tVoa = 0;

    private Set<String> teclas0 = new HashSet<>(), teclas1 = new HashSet<>();
    private Set<String> teclasPrev0 = new HashSet<>(), teclasPrev1 = new HashSet<>();

    private final Random rng = new Random();

    public ServidorJogo() {
        reset();
    }

    private void reset() {
        double chao = ALTURA - TAM_J;
        j1x = 50; j1y = chao; j1vx = 0; j1vy = 0;
        j1pulando = false; j1tiros = MAX_TIROS; j1recarregando = false; j1recargaT = 0;
        j2x = 10; j2y = ALTURA / 2.0;
        j2tiros = MAX_TIROS; j2recarregando = false; j2recargaT = 0;
        inimigos.clear(); voadores.clear(); projJ1.clear(); projJ2.clear();
        pontos = new int[]{0, 0};
        fimJogo = false; nid = 0; tIni = 0; tVoa = 0;
    }

    private int nextId() { return ++nid; }

    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(PORTA)) {
            System.out.println("[*] Servidor TCP aguardando na porta " + PORTA);

            while (clientes.size() < 2) {
                Socket socket = serverSocket.accept();
                int playerId = clientes.size();
                ClientHandler handler = new ClientHandler(socket, playerId);
                clientes.add(handler);
                new Thread(handler).start();
                System.out.println("[+] J" + playerId + " conectado de " + socket.getInetAddress());
                
                // Enviar confirmação de conexão
                handler.enviar("{\"tipo\":\"conectado\",\"player_id\":" + playerId + "}");
            }

            System.out.println("[*] Dois jogadores conectados. Iniciando game loop...");
            gameLoop();

        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    private void gameLoop() {
        rodando = true;
        long dtMs = 1000 / TICK_RATE;
        while (rodando) {
            long t0 = System.currentTimeMillis();

            lock.lock();
            try {
                if (!fimJogo) {
                    tick();
                }
                String estadoJson = serializarJSON();
                broadcast(estadoJson);
            } finally {
                lock.unlock();
            }

            if (fimJogo) {
                // Pequeno delay antes de talvez fechar ou resetar, 
                // mas aqui o requisito é apenas tratar desconexão/fim.
            }

            long elapsed = System.currentTimeMillis() - t0;
            if (elapsed < dtMs) {
                try { Thread.sleep(dtMs - elapsed); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void tick() {
        long agora = System.currentTimeMillis();
        
        // J1 horizontal
        if (teclas0.contains("RIGHT")) j1vx += ACEL_J1;
        else if (teclas0.contains("LEFT")) j1vx -= ACEL_J1;
        else j1vx *= FRICAO_J1;
        j1x = Math.max(0, Math.min(LARGURA - TAM_J, j1x + j1vx));
        if (j1x == 0 || j1x == LARGURA - TAM_J) j1vx = 0;

        // J1 pulo
        if (teclas0.contains("UP") && !j1pulando) {
            j1pulando = true;
            j1vy = VEL_PULO;
        }
        if (j1pulando) {
            j1y += j1vy;
            j1vy += GRAVIDADE;
            double chao = ALTURA - TAM_J;
            if (j1y >= chao) { j1y = chao; j1pulando = false; j1vy = 0; }
        }

        // J1 recarga e tiro
        if (j1recarregando && (agora - j1recargaT) >= RECARGA_MS) {
            j1recarregando = false;
            j1tiros = MAX_TIROS;
        }
        if (teclas0.contains("K") && !teclasPrev0.contains("K") && !j1recarregando && j1tiros > 0) {
            j1tiros--;
            if (j1tiros == 0) { j1recarregando = true; j1recargaT = agora; }
            projJ1.add(new double[]{j1x + TAM_J / 2.0, j1y + TAM_J / 2.0, nextId()});
        }

        // J2 movimento
        if (teclas1.contains("D")) j2x += VEL_J2;
        if (teclas1.contains("A")) j2x -= VEL_J2;
        if (teclas1.contains("W")) j2y -= VEL_J2;
        if (teclas1.contains("S")) j2y += VEL_J2;
        j2x = Math.max(0, Math.min(LARGURA - TAM_J, j2x));
        j2y = Math.max(0, Math.min(ALTURA - TAM_J, j2y));

        // J2 recarga e tiro
        if (j2recarregando && (agora - j2recargaT) >= RECARGA_MS) {
            j2recarregando = false;
            j2tiros = MAX_TIROS;
        }
        if (teclas1.contains("V") && !teclasPrev1.contains("V") && !j2recarregando && j2tiros > 0) {
            j2tiros--;
            if (j2tiros == 0) { j2recarregando = true; j2recargaT = agora; }
            projJ2.add(new double[]{j2x + TAM_J / 2.0, j2y + TAM_J / 2.0, nextId()});
        }

        teclasPrev0 = new HashSet<>(teclas0);
        teclasPrev1 = new HashSet<>(teclas1);

        // Mover projéteis
        for (double[] p : projJ1) p[0] += VEL_PROJ;
        for (double[] p : projJ2) p[0] += VEL_PROJ;
        projJ1.removeIf(p -> p[0] < 0 || p[0] > LARGURA);
        projJ2.removeIf(p -> p[0] < 0 || p[0] > LARGURA);

        // Spawn inimigos
        tIni++;
        if (tIni >= 30) {
            tIni = 0;
            if (rng.nextDouble() < 0.3) {
                inimigos.add(new double[]{
                    LARGURA + rng.nextInt(LARGURA / 2) + 1,
                    ALTURA - 100.0,
                    nextId()
                });
            }
        }

        tVoa++;
        if (tVoa >= 30) {
            tVoa = 0;
            if (rng.nextDouble() < 0.55) {
                double yb = ALTURA * 0.1 + rng.nextInt((int)(ALTURA * 0.4));
                voadores.add(new double[]{
                    LARGURA + rng.nextInt(LARGURA / 3),
                    yb, yb, 0, nextId()
                });
            }
        }

        for (double[] e : inimigos) e[0] -= 2.5;
        for (double[] e : voadores) {
            e[0] -= 3.0;
            e[3]++;
            e[2] = e[1] + Math.sin(e[3] * 0.04) * 40;
        }

        // Colisão proj_j1 vs inimigos
        Set<Integer> mortosIni = new HashSet<>(), mortosP1 = new HashSet<>();
        for (double[] p : projJ1) {
            for (double[] e : inimigos) {
                int eid = (int) e[2];
                if (!mortosIni.contains(eid) && col(p[0], p[1], TAM_PROJ, TAM_PROJ, e[0], e[1], 100, 100)) {
                    mortosIni.add(eid);
                    mortosP1.add((int) p[2]);
                    pontos[0] += 3;
                }
            }
        }
        inimigos.removeIf(e -> mortosIni.contains((int) e[2]));
        projJ1.removeIf(p -> mortosP1.contains((int) p[2]));

        // Colisão proj_j2 vs voadores
        Set<Integer> mortosVoa = new HashSet<>(), mortosP2 = new HashSet<>();
        for (double[] p : projJ2) {
            for (double[] e : voadores) {
                int eid = (int) e[4];
                if (!mortosVoa.contains(eid) && col(p[0], p[1], TAM_PROJ, TAM_PROJ, e[0], e[2], TAM_VOA, TAM_VOA)) {
                    mortosVoa.add(eid);
                    mortosP2.add((int) p[2]);
                    pontos[1] += 3;
                }
            }
        }
        voadores.removeIf(e -> mortosVoa.contains((int) e[4]));
        projJ2.removeIf(p -> mortosP2.contains((int) p[2]));

        // Inimigo toca jogador → fim
        for (double[] e : inimigos) {
            if (col(e[0], e[1], 100, 100, j1x, j1y, TAM_J, TAM_J)) fimJogo = true;
        }
        for (double[] e : voadores) {
            if (col(e[0], e[2], TAM_VOA, TAM_VOA, j2x, j2y, TAM_J, TAM_J)) fimJogo = true;
        }

        // Penalidade saída de tela
        long saiuIni = inimigos.stream().filter(e -> e[0] < -100).count();
        inimigos.removeIf(e -> e[0] < -100);
        pontos[0] -= (int)(10 * saiuIni);

        long saiuVoa = voadores.stream().filter(e -> e[0] < -100).count();
        voadores.removeIf(e -> e[0] < -100);
        pontos[1] -= (int)(10 * saiuVoa);
    }

    private boolean col(double ax, double ay, double aw, double ah,
                        double bx, double by, double bw, double bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private String serializarJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tipo\":\"estado\",");
        
        sb.append("\"j1\":{\"x\":").append((int)j1x)
          .append(",\"y\":").append((int)j1y)
          .append(",\"tiros\":").append(j1tiros)
          .append(",\"recarregando\":").append(j1recarregando).append("},");
        
        sb.append("\"j2\":{\"x\":").append((int)j2x)
          .append(",\"y\":").append((int)j2y)
          .append(",\"tiros\":").append(j2tiros)
          .append(",\"recarregando\":").append(j2recarregando).append("},");
        
        sb.append("\"inimigos\":[");
        for (int i = 0; i < inimigos.size(); i++) {
            double[] ini = inimigos.get(i);
            sb.append("{\"x\":").append((int)ini[0]).append(",\"y\":").append((int)ini[1]).append("}");
            if (i < inimigos.size() - 1) sb.append(",");
        }
        sb.append("],");
        
        sb.append("\"voadores\":[");
        for (int i = 0; i < voadores.size(); i++) {
            double[] voa = voadores.get(i);
            sb.append("{\"x\":").append((int)voa[0]).append(",\"y\":").append((int)voa[2]).append("}");
            if (i < voadores.size() - 1) sb.append(",");
        }
        sb.append("],");
        
        sb.append("\"proj_j1\":[");
        for (int i = 0; i < projJ1.size(); i++) {
            double[] p = projJ1.get(i);
            sb.append("{\"x\":").append((int)p[0]).append(",\"y\":").append((int)p[1]).append("}");
            if (i < projJ1.size() - 1) sb.append(",");
        }
        sb.append("],");
        
        sb.append("\"proj_j2\":[");
        for (int i = 0; i < projJ2.size(); i++) {
            double[] p = projJ2.get(i);
            sb.append("{\"x\":").append((int)p[0]).append(",\"y\":").append((int)p[1]).append("}");
            if (i < projJ2.size() - 1) sb.append(",");
        }
        sb.append("],");
        
        sb.append("\"pontos\":[").append(pontos[0]).append(",").append(pontos[1]).append("],");
        sb.append("\"fim_jogo\":").append(fimJogo);
        sb.append("}");
        return sb.toString();
    }

    private void broadcast(String mensagem) {
        for (ClientHandler client : clientes) {
            client.enviar(mensagem);
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final int playerId;
        private PrintWriter out;

        public ClientHandler(Socket socket, int playerId) {
            this.socket = socket;
            this.playerId = playerId;
        }

        public void enviar(String mensagem) {
            if (out != null) {
                out.println(mensagem);
            }
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);
                
                String line;
                while ((line = in.readLine()) != null) {
                    processarInputJSON(line);
                }
            } catch (IOException e) {
                System.out.println("[-] J" + playerId + " desconectado");
            } finally {
                lock.lock();
                try {
                    fimJogo = true;
                    rodando = false;
                } finally {
                    lock.unlock();
                }
                clientes.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void processarInputJSON(String line) {
            // Exemplo esperado: {"tipo":"input","player_id":0,"teclas":["RIGHT","K"]}
            if (line.contains("\"teclas\":[")) {
                int start = line.indexOf("[") + 1;
                int end = line.indexOf("]");
                if (start > 0 && end > start) {
                    String keysStr = line.substring(start, end).replace("\"", "");
                    String[] keys = keysStr.isEmpty() ? new String[0] : keysStr.split(",");
                    
                    lock.lock();
                    try {
                        Set<String> tSet = new HashSet<>();
                        for (String k : keys) {
                            String trimmed = k.trim();
                            if (!trimmed.isEmpty()) tSet.add(trimmed);
                        }
                        if (playerId == 0) teclas0 = tSet;
                        else if (playerId == 1) teclas1 = tSet;
                    } finally {
                        lock.unlock();
                    }
                } else if (line.contains("\"teclas\":[]")) {
                    lock.lock();
                    try {
                        if (playerId == 0) teclas0 = new HashSet<>();
                        else if (playerId == 1) teclas1 = new HashSet<>();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        new ServidorJogo().iniciar();
    }
}
