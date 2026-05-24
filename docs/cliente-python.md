# Cliente Python — main.py + rede/cliente.py

**Arquivos:**
- `_Game/main.py` — entrada do jogo, renderização, HUD
- `_Game/rede/cliente.py` — comunicação TCP com a Bridge
- `_Game/configuracoes.py` — constantes (`PORTA = 5556`)

## Responsabilidade

Todo o visual do jogo. Renderiza o estado recebido do servidor, captura inputs do teclado e os envia para a Bridge. Não possui lógica de jogo — apenas exibe o que o servidor manda.

## Estrutura

```
main.py
├── tela_menu()          → tela inicial ([E] Entrar)
├── tela_digitar_ip()    → tela para digitar o IP
├── tela_info()          → tela de loading/mensagens
├── loop_rede(cliente)   → loop principal de renderização
└── Cliente              → (importado de rede/cliente.py)
```

## Cliente TCP (`rede/cliente.py`)

Gerencia a conexão TCP com a Bridge.

### Atributos públicos

| Atributo | Tipo | Descrição |
|---|---|---|
| `player_id` | `int` | 0 ou 1, recebido no handshake |
| `conectado` | `bool` | False se a conexão caiu |
| `erro` | `str` | Mensagem do último erro |
| `estado_atual` | `dict` | Último estado recebido (thread-safe) |

### Métodos públicos

| Método | Descrição |
|---|---|
| `conectar(ip, porta)` | Abre TCP, faz handshake, inicia thread de recebimento |
| `enviar_input(teclas)` | Serializa e envia `{"tipo":"input",...}` |
| `desconectar()` | Fecha socket |

### Thread de recebimento

Roda em background (daemon thread). Lê linhas JSON do socket e atualiza `_estado` com lock:

```python
def _receber_loop(self):
    while self.conectado:
        linha = self._arquivo_leitura.readline()
        estado = json.loads(linha.strip())
        with self._lock:
            self._estado = estado
```

### Envio de inputs

Chamado a cada frame pelo `loop_rede`. Usa lock para evitar colisão com a thread de recebimento:

```python
def enviar_input(self, teclas: list):
    payload = {"tipo": "input", "player_id": self.player_id, "teclas": teclas}
    with self._lock:
        self._socket.sendall((json.dumps(payload) + "\n").encode('utf-8'))
```

## Loop de renderização (`loop_rede`)

Executa a 60 FPS. A cada frame:

1. Processa eventos pygame (fechar janela, mutar som com `U`)
2. Verifica `cliente.conectado` — exibe "Desconectado" se False
3. Lê teclas pressionadas e chama `cliente.enviar_input(teclas)`
4. Lê `cliente.estado_atual` — se `None`, exibe "Aguardando 2º jogador"
5. Renderiza fundo, inimigos, voadores, projéteis, jogadores, HUD

## Mapeamento de teclas

```python
MAPA_TECLAS = {
    pygame.K_RIGHT: "RIGHT",   # J1 direita
    pygame.K_LEFT:  "LEFT",    # J1 esquerda
    pygame.K_UP:    "UP",      # J1 pular
    pygame.K_k:     "K",       # J1 atirar
    pygame.K_a:     "A",       # J2 esquerda
    pygame.K_d:     "D",       # J2 direita
    pygame.K_w:     "W",       # J2 cima
    pygame.K_s:     "S",       # J2 baixo
    pygame.K_v:     "V",       # J2 atirar
}
```

## Renderização de texto

O jogo usa `Pillow` para renderizar texto pixel-art (sem fontes do sistema):

```python
def render_texto(texto, cor=(255,255,255), tamanho_multiplicador=3):
    img = Image.new("RGBA", (largura, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.text((2, 2), texto, fill=(*cor, 255))
    img = img.resize((img.width * tamanho_multiplicador, ...), Image.NEAREST)
    return pygame.image.frombytes(img.tobytes(), img.size, "RGBA")
```

## Inicialização via linha de comando

```bash
# Lançado pelo Launcher automaticamente:
python main.py --join 127.0.0.1

# Ou manualmente (abre menu de IP):
python main.py
```

## Dependências

```
pygame>=2.0
Pillow>=9.0
```

Instalar com:
```bash
pip install -r requirements.txt
```
