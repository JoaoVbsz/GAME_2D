package cliente;

import shared.EstadoJogo;
import shared.IClienteCallback;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.locks.ReentrantLock;

public class ClienteCallback extends UnicastRemoteObject implements IClienteCallback {
    private static final long serialVersionUID = 1L;

    private EstadoJogo estado;
    private final ReentrantLock lock = new ReentrantLock();

    public ClienteCallback() throws RemoteException {
        super(0);
    }

    @Override
    public void receberEstado(EstadoJogo e) throws RemoteException {
        lock.lock();
        try {
            this.estado = e;
        } finally {
            lock.unlock();
        }
    }

    public EstadoJogo getEstado() {
        lock.lock();
        try {
            return estado;
        } finally {
            lock.unlock();
        }
    }
}
