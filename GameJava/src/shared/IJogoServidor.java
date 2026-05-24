package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IJogoServidor extends Remote {
    int conectar(IClienteCallback callback) throws RemoteException;
    void processarInput(int playerId, String[] teclas) throws RemoteException;
    void desconectar(int playerId) throws RemoteException;
}
