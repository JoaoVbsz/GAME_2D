import socket
import threading
import json


class Cliente:
    def __init__(self):
        self._sock = None
        self._buffer = ""
        self._lock = threading.Lock()
        self._rodando = False
        self.estado_atual = None   # último GameState recebido
        self.player_id = None      # 0 ou 1, enviado pelo servidor na init
        self.conectado = False
        self.erro = None

    def conectar(self, ip: str, porta: int):
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.settimeout(10.0)
        self._sock.connect((ip, porta))
        self._sock.settimeout(None)
        self._rodando = True
        self.conectado = True
        threading.Thread(target=self._loop_receber, daemon=True).start()

    def _loop_receber(self):
        try:
            while self._rodando:
                dados = self._sock.recv(4096).decode("utf-8", errors="ignore")
                if not dados:
                    break
                with self._lock:
                    self._buffer += dados
                    while "\n" in self._buffer:
                        linha, self._buffer = self._buffer.split("\n", 1)
                        if not linha:
                            continue
                        try:
                            msg = json.loads(linha)
                            if msg.get("tipo") == "init":
                                self.player_id = msg["player_id"]
                            elif msg.get("tipo") == "estado":
                                self.estado_atual = msg
                            elif msg.get("tipo") == "erro":
                                self.erro = msg.get("msg", "Erro desconhecido")
                                self._rodando = False
                        except json.JSONDecodeError:
                            pass
        except Exception as e:
            self.erro = str(e)
        finally:
            self.conectado = False
            self._rodando = False

    def enviar_input(self, teclas: list):
        if not self.conectado or self.player_id is None:
            return
        try:
            payload = (json.dumps({"tipo": "input", "player_id": self.player_id,
                                   "teclas": teclas}) + "\n").encode("utf-8")
            self._sock.sendall(payload)
        except OSError:
            self.conectado = False

    def desconectar(self):
        self._rodando = False
        self.conectado = False
        try:
            self._sock.close()
        except OSError:
            pass
