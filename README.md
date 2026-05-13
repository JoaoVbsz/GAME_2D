# Time Out — Multiplayer 2D

Jogo multiplayer local/rede em Python com Pygame. Dois jogadores (mago de chão e mago voador) eliminam inimigos para acumular pontos.

## Requisitos

- Python 3.14+
- Dependências:

```bash
pip install pygame pillow
```

## Como rodar

```bash
cd _Game
python main.py
```

---

## Conectar na partida

### Host (quem cria a sala)

1. Rode `python main.py`
2. Pressione **`H`** no menu
3. O jogo exibe seu IP local — passe para o outro jogador
4. Aguarde a conexão do Guest

### Guest (quem entra na sala)

1. Rode `python main.py`
2. Pressione **`E`** no menu
3. Digite o IP do Host e pressione **Enter**

> Ambos precisam estar na mesma rede local, ou o Host deve ter a porta `5555` aberta no roteador para conexões externas.

### Via linha de comando

```bash
# Hospedar
python main.py --host

# Entrar diretamente
python main.py --join 192.168.1.10
```

---

## Controles

### Jogador 1 — Mago do Chão

| Tecla | Ação |
|-------|------|
| `←` / `→` | Mover |
| `↑` | Pular |
| `K` | Atirar |

### Jogador 2 — Mago Voador

| Tecla | Ação |
|-------|------|
| `A` / `D` | Mover horizontal |
| `W` / `S` | Mover vertical |
| `V` | Atirar |

### Geral

| Tecla | Ação |
|-------|------|
| `U` | Mutar / desmutar música |
| `ESC` | Sair (no menu) |

---

## Sistema de tiros

Cada jogador tem **5 tiros**. Ao esgotar, entra em recarga automática de **2 segundos**. O HUD mostra `Tiros: X/5` ou `Recarregando...` no canto da tela.

---

## Inimigos

| Tipo | Comportamento |
|------|--------------|
| Inimigo terrestre | Anda pelo chão |
| Inimigo voador | Voa pela tela |

Eliminar inimigos aumenta a pontuação de cada jogador individualmente.

---

## Arquitetura

```
_Game/
├── main.py          # Entry point, menu, loop de render
├── configuracoes.py # Resolução (736×414), FPS, porta
├── servidor.py      # Servidor standalone (alternativo)
├── rede/
│   ├── servidor.py  # Lógica autoritativa do servidor TCP
│   ├── cliente.py   # Conexão e recebimento de estado
│   └── protocolo.py # Serialização JSON
└── sprites/
    ├── jogador.py       # J1 — mago do chão
    ├── jogador2.py      # J2 — mago voador
    ├── inimigo.py
    ├── inimigo_voador.py
    └── ...
```

O servidor roda a simulação completa (física, colisões, pontuação) e envia o estado a 30 ticks/s. Os clientes só enviam inputs e renderizam o estado recebido.
