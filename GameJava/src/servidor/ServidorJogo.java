package servidor;

import shared.*;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ServidorJogo extends UnicastRemoteObject implements IJogoServidor {
    private static final long serialVersionUID = 1L;

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

    private final Map<Integer, IClienteCallback> callbacks = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int nConectados = 0;
    private boolean rodando = false;

    // j1: {x, y, vx, vy, pulando(0/1), tiros, recarregando(0/1), recargaT}
    private double j1x, j1y, j1vx, j1vy;
    private boolean j1pulando, j1recarregando;
    private int j1tiros;
    private long j1recargaT;

    // j2: {x, y, tiros, recarregando, recargaT}
    private double j2x, j2y;
    private boolean j2recarregando;
    private int j2tiros;
    private long j2recargaT;

    // inimigos: [x, y, id]
    private final List<double[]> inimigos = new ArrayList<>();
    // voadores: [x, yBase, y, tick, id]
    private final List<double[]> voadores = new ArrayList<>();
    // proj: [x, y, id]
    private final List<double[]> projJ1 = new ArrayList<>();
    private final List<double[]> projJ2 = new ArrayList<>();

    private int[] pontos = {0, 0};
    private boolean fimJogo = false;
    private int nid = 0;
    private int tIni = 0, tVoa = 0;

    private Set<String> teclas0 = new HashSet<>(), teclas1 = new HashSet<>();
    private Set<String> teclasPrev0 = new HashSet<>(), teclasPrev1 = new HashSet<>();

    private final Random rng = new Random();

    public ServidorJogo() throws RemoteException {
        super(0);
        reset();
    }

    private void reset() {
        double chao = Configuracoes.ALTURA - TAM_J;
        j1x = 50; j1y = chao; j1vx = 0; j1vy = 0;
        j1pulando = false; j1tiros = MAX_TIROS; j1recarregando = false; j1recargaT = 0;
        j2x = 10; j2y = Configuracoes.ALTURA / 2.0;
        j2tiros = MAX_TIROS; j2recarregando = false; j2recargaT = 0;
        inimigos.clear(); voadores.clear(); projJ1.clear(); projJ2.clear();
        pontos = new int[]{0, 0};
        fimJogo = false; nid = 0; tIni = 0; tVoa = 0;
    }

    private int nextId() { return ++nid; }

    @Override
    public int conectar(IClienteCallback callback) throws RemoteException {
        lock.lock();
        try {
            if (nConectados >= 2) throw new RemoteException("Partida cheia");
            int pid = nConectados;
            callbacks.put(pid, callback);
            nConectados++;
            int n = nConectados;
            System.out.println("[+] J" + pid + " conectado");
            if (n == 2) {
                Thread t = new Thread(this::gameLoop, "GameLoop");
                t.setDaemon(true);
                t.start();
            }
            return pid;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void processarInput(int playerId, String[] teclas) throws RemoteException {
        lock.lock();
        try {
            Set<String> t = new HashSet<>(Arrays.asList(teclas));
            if (playerId == 0) teclas0 = t;
            else teclas1 = t;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void desconectar(int playerId) throws RemoteException {
        lock.lock();
        try {
            fimJogo = true;
            callbacks.remove(playerId);
        } finally {
            lock.unlock();
        }
        System.out.println("[-] J" + playerId + " desconectado");
    }

    private void gameLoop() {
        rodando = true;
        long dtMs = 1000 / TICK_RATE;
        while (rodando) {
            long t0 = System.currentTimeMillis();

            EstadoJogo estado;
            lock.lock();
            try {
                if (!fimJogo) tick();
                estado = serializar();
            } finally {
                lock.unlock();
            }

            List<IClienteCallback> cbs;
            lock.lock();
            try {
                cbs = new ArrayList<>(callbacks.values());
            } finally {
                lock.unlock();
            }

            for (IClienteCallback cb : cbs) {
                try {
                    cb.receberEstado(estado);
                } catch (RemoteException e) {
                    rodando = false;
                    break;
                }
            }

            long elapsed = System.currentTimeMillis() - t0;
            if (elapsed < dtMs) {
                try { Thread.sleep(dtMs - elapsed); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void tick() {
        long agora = System.currentTimeMillis();
        Set<String> tk1 = teclas0, tk2 = teclas1;
        Set<String> pk1 = teclasPrev0, pk2 = teclasPrev1;

        // J1 horizontal
        if (tk1.contains("RIGHT")) j1vx += ACEL_J1;
        else if (tk1.contains("LEFT")) j1vx -= ACEL_J1;
        else j1vx *= FRICAO_J1;
        j1x = Math.max(0, Math.min(Configuracoes.LARGURA - TAM_J, j1x + j1vx));
        if (j1x == 0 || j1x == Configuracoes.LARGURA - TAM_J) j1vx = 0;

        // J1 pulo
        if (tk1.contains("UP") && !j1pulando) {
            j1pulando = true;
            j1vy = VEL_PULO;
        }
        if (j1pulando) {
            j1y += j1vy;
            j1vy += GRAVIDADE;
            double chao = Configuracoes.ALTURA - TAM_J;
            if (j1y >= chao) { j1y = chao; j1pulando = false; j1vy = 0; }
        }

        // J1 recarga e tiro
        if (j1recarregando && (agora - j1recargaT) >= RECARGA_MS) {
            j1recarregando = false;
            j1tiros = MAX_TIROS;
        }
        if (tk1.contains("K") && !pk1.contains("K") && !j1recarregando && j1tiros > 0) {
            j1tiros--;
            if (j1tiros == 0) { j1recarregando = true; j1recargaT = agora; }
            projJ1.add(new double[]{j1x + TAM_J / 2.0, j1y + TAM_J / 2.0, nextId()});
        }

        // J2 movimento
        if (tk2.contains("D")) j2x += VEL_J2;
        if (tk2.contains("A")) j2x -= VEL_J2;
        if (tk2.contains("W")) j2y -= VEL_J2;
        if (tk2.contains("S")) j2y += VEL_J2;
        j2x = Math.max(0, Math.min(Configuracoes.LARGURA - TAM_J, j2x));
        j2y = Math.max(0, Math.min(Configuracoes.ALTURA - TAM_J, j2y));

        // J2 recarga e tiro
        if (j2recarregando && (agora - j2recargaT) >= RECARGA_MS) {
            j2recarregando = false;
            j2tiros = MAX_TIROS;
        }
        if (tk2.contains("V") && !pk2.contains("V") && !j2recarregando && j2tiros > 0) {
            j2tiros--;
            if (j2tiros == 0) { j2recarregando = true; j2recargaT = agora; }
            projJ2.add(new double[]{j2x + TAM_J / 2.0, j2y + TAM_J / 2.0, nextId()});
        }

        teclasPrev0 = new HashSet<>(tk1);
        teclasPrev1 = new HashSet<>(tk2);

        // Mover projéteis
        for (double[] p : projJ1) p[0] += VEL_PROJ;
        for (double[] p : projJ2) p[0] += VEL_PROJ;
        projJ1.removeIf(p -> p[0] < 0 || p[0] > Configuracoes.LARGURA);
        projJ2.removeIf(p -> p[0] < 0 || p[0] > Configuracoes.LARGURA);

        // Spawn inimigos
        tIni++;
        if (tIni >= 30) {
            tIni = 0;
            if (rng.nextDouble() < 0.3) {
                inimigos.add(new double[]{
                    Configuracoes.LARGURA + rng.nextInt(Configuracoes.LARGURA / 2) + 1,
                    Configuracoes.ALTURA - 100.0,
                    nextId()
                });
            }
        }

        tVoa++;
        if (tVoa >= 30) {
            tVoa = 0;
            if (rng.nextDouble() < 0.55) {
                double yb = Configuracoes.ALTURA * 0.1 + rng.nextInt((int)(Configuracoes.ALTURA * 0.4));
                voadores.add(new double[]{
                    Configuracoes.LARGURA + rng.nextInt(Configuracoes.LARGURA / 3),
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

    private EstadoJogo serializar() {
        EstadoJogo e = new EstadoJogo();
        e.j1x = (int) j1x; e.j1y = (int) j1y;
        e.j1tiros = j1tiros; e.j1recarregando = j1recarregando;
        e.j2x = (int) j2x; e.j2y = (int) j2y;
        e.j2tiros = j2tiros; e.j2recarregando = j2recarregando;
        for (double[] ini : inimigos) e.inimigos.add(new int[]{(int)ini[0], (int)ini[1]});
        for (double[] voa : voadores) e.voadores.add(new int[]{(int)voa[0], (int)voa[2]});
        for (double[] p : projJ1) e.projJ1.add(new int[]{(int)p[0], (int)p[1]});
        for (double[] p : projJ2) e.projJ2.add(new int[]{(int)p[0], (int)p[1]});
        e.pontos = pontos.clone();
        e.fimJogo = fimJogo;
        return e;
    }

    public static void main(String[] args) throws Exception {
        String ip = Configuracoes.getIpLocal();
        System.setProperty("java.rmi.server.hostname", ip);
        System.out.println("[*] IP local: " + ip);

        ServidorJogo servidor = new ServidorJogo();
        LocateRegistry.createRegistry(Configuracoes.PORTA);
        java.rmi.registry.Registry reg = LocateRegistry.getRegistry(Configuracoes.PORTA);
        reg.rebind("ServidorJogo", servidor);
        System.out.println("[*] Servidor RMI aguardando na porta " + Configuracoes.PORTA);
    }
}
