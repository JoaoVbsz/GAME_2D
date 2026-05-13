import Pyro5.api
import Pyro5.server
import threading
import time
import math
import random
from configuracoes import LARGURA, ALTURA, PORTA

GRAVIDADE = 1
VEL_PULO = -15
ACEL_J1 = 0.5
FRICAO_J1 = 0.85
VEL_J2 = 4
RECARGA_S = 2.0
MAX_TIROS = 5
TICK_RATE = 30
VEL_PROJ = 7
TAM_PROJ = 20
TAM_J = 100
TAM_VOA = 80


@Pyro5.api.expose
class ServidorJogo:
    def __init__(self):
        self._uris_clientes = {}
        self._lock = threading.Lock()
        self._rodando = False
        self._n_conectados = 0
        self.teclas = {0: set(), 1: set()}
        self.teclas_prev = {0: set(), 1: set()}
        self._reset()

    def _reset(self):
        chao = float(ALTURA - TAM_J)
        self.j1 = {"x": 50.0, "y": chao, "vx": 0.0, "vy": 0.0,
                   "pulando": False, "tiros": MAX_TIROS,
                   "recarregando": False, "recarga_t": 0.0}
        self.j2 = {"x": 10.0, "y": float(ALTURA // 2),
                   "tiros": MAX_TIROS, "recarregando": False, "recarga_t": 0.0}
        self.inimigos = []
        self.voadores = []
        self.proj_j1 = []
        self.proj_j2 = []
        self.pontos = [0, 0]
        self.fim_jogo = False
        self._nid = 0
        self._t_ini = 0
        self._t_voa = 0

    def _nid_inc(self):
        self._nid += 1
        return self._nid

    def conectar(self, uri_cliente: str) -> int:
        with self._lock:
            if self._n_conectados >= 2:
                raise Exception("Partida cheia")
            pid = self._n_conectados
            self._uris_clientes[pid] = uri_cliente
            self._n_conectados += 1
            n = self._n_conectados
        print(f"[+] J{pid} conectado via RMI: {uri_cliente}")
        if n == 2:
            threading.Thread(target=self._game_loop, daemon=True).start()
        return pid

    def processar_input(self, player_id: int, teclas: list):
        with self._lock:
            self.teclas[player_id] = set(teclas)

    def desconectar(self, player_id: int):
        with self._lock:
            self.fim_jogo = True
            self._uris_clientes.pop(player_id, None)
        print(f"[-] J{player_id} desconectado")

    def _game_loop(self):
        self._rodando = True
        with self._lock:
            proxies = {pid: Pyro5.api.Proxy(uri)
                       for pid, uri in self._uris_clientes.items()}
        dt = 1.0 / TICK_RATE
        while self._rodando:
            t0 = time.time()
            with self._lock:
                if not self.fim_jogo:
                    self._tick()
                estado = self._serializar()
            for proxy in proxies.values():
                try:
                    proxy.receber_estado(estado)
                except Exception:
                    self._rodando = False
                    break
            time.sleep(max(0.0, dt - (time.time() - t0)))

    def _tick(self):
        agora = time.time()
        tk1 = self.teclas[0]
        tk2 = self.teclas[1]
        pk1 = self.teclas_prev[0]
        pk2 = self.teclas_prev[1]

        # J1 horizontal
        j1 = self.j1
        if "RIGHT" in tk1:
            j1["vx"] += ACEL_J1
        elif "LEFT" in tk1:
            j1["vx"] -= ACEL_J1
        else:
            j1["vx"] *= FRICAO_J1
        j1["x"] = max(0.0, min(float(LARGURA - TAM_J), j1["x"] + j1["vx"]))
        if j1["x"] in (0.0, float(LARGURA - TAM_J)):
            j1["vx"] = 0.0

        # J1 pulo
        if "UP" in tk1 and not j1["pulando"]:
            j1["pulando"] = True
            j1["vy"] = float(VEL_PULO)
        if j1["pulando"]:
            j1["y"] += j1["vy"]
            j1["vy"] += GRAVIDADE
            chao = float(ALTURA - TAM_J)
            if j1["y"] >= chao:
                j1["y"] = chao
                j1["pulando"] = False
                j1["vy"] = 0.0

        # J1 recarga
        if j1["recarregando"] and agora - j1["recarga_t"] >= RECARGA_S:
            j1["recarregando"] = False
            j1["tiros"] = MAX_TIROS

        # J1 tiro (borda de subida)
        if "K" in tk1 and "K" not in pk1 and not j1["recarregando"] and j1["tiros"] > 0:
            j1["tiros"] -= 1
            if j1["tiros"] == 0:
                j1["recarregando"] = True
                j1["recarga_t"] = agora
            self.proj_j1.append({"x": j1["x"] + TAM_J / 2, "y": j1["y"] + TAM_J / 2, "id": self._nid_inc()})

        # J2 movimento livre
        j2 = self.j2
        if "D" in tk2:
            j2["x"] += VEL_J2
        if "A" in tk2:
            j2["x"] -= VEL_J2
        if "W" in tk2:
            j2["y"] -= VEL_J2
        if "S" in tk2:
            j2["y"] += VEL_J2
        j2["x"] = max(0.0, min(float(LARGURA - TAM_J), j2["x"]))
        j2["y"] = max(0.0, min(float(ALTURA - TAM_J), j2["y"]))

        # J2 recarga
        if j2["recarregando"] and agora - j2["recarga_t"] >= RECARGA_S:
            j2["recarregando"] = False
            j2["tiros"] = MAX_TIROS

        # J2 tiro (borda de subida)
        if "V" in tk2 and "V" not in pk2 and not j2["recarregando"] and j2["tiros"] > 0:
            j2["tiros"] -= 1
            if j2["tiros"] == 0:
                j2["recarregando"] = True
                j2["recarga_t"] = agora
            self.proj_j2.append({"x": j2["x"] + TAM_J / 2, "y": j2["y"] + TAM_J / 2, "id": self._nid_inc()})

        self.teclas_prev[0] = set(tk1)
        self.teclas_prev[1] = set(tk2)

        # Projéteis
        for p in self.proj_j1:
            p["x"] += VEL_PROJ
        for p in self.proj_j2:
            p["x"] -= VEL_PROJ
        self.proj_j1 = [p for p in self.proj_j1 if 0 <= p["x"] <= LARGURA]
        self.proj_j2 = [p for p in self.proj_j2 if 0 <= p["x"] <= LARGURA]

        # Spawn inimigos
        self._t_ini += 1
        if self._t_ini >= 30:
            self._t_ini = 0
            if random.random() < 0.3:
                self.inimigos.append({"x": float(LARGURA + random.randint(1, LARGURA // 2)),
                                      "y": float(ALTURA - 100), "id": self._nid_inc()})

        self._t_voa += 1
        if self._t_voa >= 50:
            self._t_voa = 0
            if random.random() < 0.25:
                yb = float(random.randint(int(ALTURA * 0.1), int(ALTURA * 0.5)))
                self.voadores.append({"x": float(LARGURA + random.randint(0, LARGURA // 3)),
                                      "y_base": yb, "y": yb, "tick": 0, "id": self._nid_inc()})

        # Mover inimigos
        for e in self.inimigos:
            e["x"] -= 2.5
        for e in self.voadores:
            e["x"] -= 3.0
            e["tick"] += 1
            e["y"] = e["y_base"] + math.sin(e["tick"] * 0.04) * 40

        # Colisão proj_j1 × terrestres
        mortos_ini = set()
        mortos_p1 = set()
        for p in self.proj_j1:
            for e in self.inimigos:
                if e["id"] not in mortos_ini and self._col(p["x"], p["y"], TAM_PROJ, TAM_PROJ,
                                                            e["x"], e["y"], 100, 100):
                    mortos_ini.add(e["id"])
                    mortos_p1.add(p["id"])
                    self.pontos[0] += 3
        self.inimigos = [e for e in self.inimigos if e["id"] not in mortos_ini]
        self.proj_j1 = [p for p in self.proj_j1 if p["id"] not in mortos_p1]

        # Colisão proj_j2 × voadores
        mortos_voa = set()
        mortos_p2 = set()
        for p in self.proj_j2:
            for e in self.voadores:
                if e["id"] not in mortos_voa and self._col(p["x"], p["y"], TAM_PROJ, TAM_PROJ,
                                                            e["x"], e["y"], TAM_VOA, TAM_VOA):
                    mortos_voa.add(e["id"])
                    mortos_p2.add(p["id"])
                    self.pontos[1] += 3
        self.voadores = [e for e in self.voadores if e["id"] not in mortos_voa]
        self.proj_j2 = [p for p in self.proj_j2 if p["id"] not in mortos_p2]

        # Inimigo toca jogador
        for e in self.inimigos:
            if self._col(e["x"], e["y"], 100, 100, j1["x"], j1["y"], TAM_J, TAM_J):
                self.fim_jogo = True
        for e in self.voadores:
            if self._col(e["x"], e["y"], TAM_VOA, TAM_VOA, j2["x"], j2["y"], TAM_J, TAM_J):
                self.fim_jogo = True

        # Saíram da tela
        saiu_ini = [e for e in self.inimigos if e["x"] < -100]
        self.inimigos = [e for e in self.inimigos if e["x"] >= -100]
        self.pontos[0] -= 10 * len(saiu_ini)

        saiu_voa = [e for e in self.voadores if e["x"] < -100]
        self.voadores = [e for e in self.voadores if e["x"] >= -100]
        self.pontos[1] -= 10 * len(saiu_voa)

    def _col(self, ax, ay, aw, ah, bx, by, bw, bh):
        return ax < bx + bw and ax + aw > bx and ay < by + bh and ay + ah > by

    def _serializar(self) -> dict:
        j1, j2 = self.j1, self.j2
        return {
            "tipo": "estado",
            "j1": {"x": int(j1["x"]), "y": int(j1["y"]),
                   "tiros": j1["tiros"], "recarregando": j1["recarregando"]},
            "j2": {"x": int(j2["x"]), "y": int(j2["y"]),
                   "tiros": j2["tiros"], "recarregando": j2["recarregando"]},
            "inimigos": [{"x": int(e["x"]), "y": int(e["y"])} for e in self.inimigos],
            "voadores": [{"x": int(e["x"]), "y": int(e["y"])} for e in self.voadores],
            "proj_j1": [{"x": int(p["x"]), "y": int(p["y"])} for p in self.proj_j1],
            "proj_j2": [{"x": int(p["x"]), "y": int(p["y"])} for p in self.proj_j2],
            "pontos": self.pontos,
            "fim_jogo": self.fim_jogo,
        }

    def iniciar(self):
        daemon = Pyro5.server.Daemon(host="0.0.0.0", port=PORTA)
        daemon.register(self, "ServidorJogo")
        print(f"[*] Servidor RMI aguardando conexões na porta {PORTA}...")
        daemon.requestLoop(lambda: True)


if __name__ == "__main__":
    ServidorJogo().iniciar()
