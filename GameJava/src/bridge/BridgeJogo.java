package bridge;

import shared.IClienteCallback;
import shared.IJogoServidor;
import java.io.*;
import java.net.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class BridgeJogo {
    private static final int PORTA_BRIDGE = 5556;
    private static final int PORTA_RMI = 5555;

    public static void main(String[] args) {
        String ipRmi = args.length > 0 ? args[0] : "127.0.0.1";
        new BridgeJogo().iniciar(ipRmi);
    }

    public void iniciar(String ipRmi) {
        try (ServerSocket serverSocket = new ServerSocket(PORTA_BRIDGE)) {
            System.out.println("[*] Bridge aguardando Python na porta " + PORTA_BRIDGE);
            
            int count = 0;
            while (count < 2) {
                Socket pythonSocket = serverSocket.accept();
                System.out.println("[+] Python conectado à Bridge: " + pythonSocket.getInetAddress());
                new Thread(new Ponte(pythonSocket, ipRmi)).start();
                count++;
            }
        } catch (IOException e) {
            System.err.println("Erro na Bridge: " + e.getMessage());
        }
    }

    private class Ponte extends UnicastRemoteObject implements IClienteCallback, Runnable {
        private final Socket socket;
        private final String ipRmi;
        private PrintWriter out;
        private IJogoServidor srv;
        private int playerId = -1;

        public Ponte(Socket socket, String ipRmi) throws RemoteException {
            super();
            this.socket = socket;
            this.ipRmi = ipRmi;
        }

        @Override
        public void receberJson(String json) throws RemoteException {
            if (out != null) {
                out.println(json);
            }
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Conectar ao RMI
                Registry reg = LocateRegistry.getRegistry(ipRmi, PORTA_RMI);
                srv = (IJogoServidor) reg.lookup("ServidorJogo");
                
                playerId = srv.conectar(this);
                if (playerId == -1) {
                    System.err.println("Servidor cheio");
                    socket.close();
                    return;
                }

                // Handshake Python
                out.println("{\"tipo\":\"conectado\",\"player_id\":" + playerId + "}");
                System.out.println("[*] Handshake enviado para Python (P" + playerId + ")");

                String line;
                while ((line = in.readLine()) != null) {
                    List<String> teclas = parseInput(line);
                    srv.processarInput(playerId, teclas);
                }

            } catch (Exception e) {
                System.err.println("Erro na ponte (P" + playerId + "): " + e.getMessage());
            } finally {
                try {
                    if (srv != null && playerId != -1) srv.desconectar(playerId);
                    socket.close();
                } catch (Exception ignored) {}
            }
        }

        private List<String> parseInput(String line) {
            List<String> teclas = new ArrayList<>();
            if (line.contains("\"teclas\":[")) {
                int start = line.indexOf("[") + 1;
                int end = line.indexOf("]");
                if (start > 0 && end > start) {
                    String keysStr = line.substring(start, end).replace("\"", "");
                    if (!keysStr.isEmpty()) {
                        String[] parts = keysStr.split(",");
                        for (String k : parts) {
                            String trimmed = k.trim();
                            if (!trimmed.isEmpty()) teclas.add(trimmed);
                        }
                    }
                }
            }
            return teclas;
        }
    }
}
