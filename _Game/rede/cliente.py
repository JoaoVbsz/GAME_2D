import socket
import json
import threading

class Cliente:
    def __init__(self):
        self._socket = None
        self._estado = None
        self._lock = threading.Lock()
        self.player_id = None
        self.conectado = False
        self.erro = None

    def conectar(self, ip: str, porta: int):
        try:
            self._socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._socket.connect((ip, porta))
            
            # Usar makefile para ler linhas com segurança
            self._arquivo_leitura = self._socket.makefile('r', encoding='utf-8')
            
            # Receber handshake: {"tipo":"conectado","player_id":0}
            linha = self._arquivo_leitura.readline()
            if not linha:
                raise Exception("Conexão fechada pelo servidor no handshake")
            
            msg = json.loads(linha.strip())
            if msg.get("tipo") == "conectado":
                self.player_id = msg.get("player_id")
                self.conectado = True
                threading.Thread(target=self._receber_loop, daemon=True).start()
            else:
                raise Exception(f"Mensagem de boas-vindas inesperada: {msg}")
        except Exception as e:
            self.erro = str(e)
            self.conectado = False
            if self._socket:
                self._socket.close()
            raise e

    def _receber_loop(self):
        try:
            while self.conectado:
                linha = self._arquivo_leitura.readline()
                if not linha:
                    break
                
                try:
                    estado = json.loads(linha.strip())
                    with self._lock:
                        self._estado = estado
                except json.JSONDecodeError:
                    continue
        except Exception as e:
            if self.conectado:
                self.erro = str(e)
        finally:
            self.conectado = False
            self._socket.close()

    def enviar_input(self, teclas: list):
        if not self.conectado or self.player_id is None:
            return
        
        payload = {
            "tipo": "input",
            "player_id": self.player_id,
            "teclas": teclas
        }
        
        try:
            with self._lock:
                msg = json.dumps(payload) + "\n"
                self._socket.sendall(msg.encode('utf-8'))
        except Exception as e:
            self.erro = str(e)
            self.conectado = False

    @property
    def estado_atual(self):
        with self._lock:
            return self._estado

    def desconectar(self):
        self.conectado = False
        if self._socket:
            try:
                self._socket.close()
            except Exception:
                pass
