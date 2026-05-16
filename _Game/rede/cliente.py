import Pyro5.api
import Pyro5.server
import threading


@Pyro5.api.expose
class ClienteRecebedor:
    def __init__(self):
        self._estado = None
        self._lock = threading.Lock()

    def receber_estado(self, estado: dict):
        with self._lock:
            self._estado = estado

    @property
    def estado_atual(self):
        with self._lock:
            return self._estado


class Cliente:
    def __init__(self):
        self._recebedor = ClienteRecebedor()
        self._daemon = None
        self._proxy_servidor = None
        self.player_id = None
        self.conectado = False
        self.erro = None

    def conectar(self, ip: str, porta: int):
        import socket as _socket
        _s = _socket.socket(_socket.AF_INET, _socket.SOCK_DGRAM)
        _s.connect((ip, porta))
        _meu_ip = _s.getsockname()[0]
        _s.close()
        self._daemon = Pyro5.server.Daemon(host=_meu_ip)
        uri = self._daemon.register(self._recebedor)
        threading.Thread(target=self._daemon.requestLoop, daemon=True).start()

        self._proxy_servidor = Pyro5.api.Proxy(f"PYRO:ServidorJogo@{ip}:{porta}")
        self.player_id = self._proxy_servidor.conectar(str(uri))
        self.conectado = True

    def enviar_input(self, teclas: list):
        if not self.conectado:
            return
        try:
            self._proxy_servidor.processar_input(self.player_id, teclas)
        except Exception as e:
            self.erro = str(e)
            self.conectado = False

    @property
    def estado_atual(self):
        return self._recebedor.estado_atual

    def desconectar(self):
        if self._proxy_servidor and self.player_id is not None:
            try:
                self._proxy_servidor.desconectar(self.player_id)
            except Exception:
                pass
        self.conectado = False
        if self._daemon:
            try:
                self._daemon.shutdown()
            except Exception:
                pass
