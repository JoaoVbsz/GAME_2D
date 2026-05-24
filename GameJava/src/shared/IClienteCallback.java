package shared;
import java.rmi.*;
public interface IClienteCallback extends Remote {
    void receberJson(String json) throws RemoteException;
}
