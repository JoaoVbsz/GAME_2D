package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IClienteCallback extends Remote {
    void receberEstado(EstadoJogo estado) throws RemoteException;
}
