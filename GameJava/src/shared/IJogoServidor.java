package shared;
import java.rmi.*;
import java.util.List;
public interface IJogoServidor extends Remote {
    int conectar(IClienteCallback callback) throws RemoteException;
    void processarInput(int playerId, List<String> teclas) throws RemoteException;
    void desconectar(int playerId) throws RemoteException;
}
