# Time Out — Visão Geral da Arquitetura

## O que é o projeto

Jogo multiplayer 2D para dois jogadores na mesma rede local. O **jogo e a renderização** rodam em Python (pygame). A **lógica de rede e o estado da partida** rodam em Java com RMI.

## Arquitetura

```
PC A                                    PC B
┌─────────────────────┐                ┌──────────────────────────────┐
│  Launcher.java       │                │  Launcher.java               │
│  → ServidorJogo.java │                │  → BridgeJogo.java           │
│    (RMI :5555)       │◄──── RMI ─────►│    (RMI client + TCP :5556)  │
│                      │                │  → main.py                   │
│                      │                │    (pygame, TCP :5556)       │
└─────────────────────┘                └──────────────────────────────┘
```

## Componentes

| Componente | Linguagem | Porta | Responsabilidade |
|---|---|---|---|
| `ServidorJogo.java` | Java | 5555 (RMI) | Game loop, física, estado |
| `BridgeJogo.java` | Java | 5556 (TCP) | Traduz RMI ↔ TCP+JSON |
| `main.py` + `rede/cliente.py` | Python | — | Renderização, input, HUD |

## Por que essa arquitetura?

O trabalho exige Java RMI. O jogo foi desenvolvido em Python (pygame). A solução foi usar um componente **Bridge** em Java que:
- Fala RMI com o servidor (satisfaz o requisito acadêmico)
- Fala TCP+JSON com o Python (protocolo que qualquer linguagem entende)

## Protocolo TCP+JSON (Bridge ↔ Python)

Todas as mensagens são linhas JSON terminadas em `\n`.

**Handshake** (Bridge → Python, ao conectar):
```json
{"tipo": "conectado", "player_id": 0}
```

**Input** (Python → Bridge, a cada frame):
```json
{"tipo": "input", "player_id": 0, "teclas": ["RIGHT", "K"]}
```

**Estado** (Bridge → Python, ~30x por segundo):
```json
{
  "tipo": "estado",
  "j1": {"x": 100, "y": 314, "tiros": 3, "recarregando": false},
  "j2": {"x": 400, "y": 200, "tiros": 5, "recarregando": false},
  "inimigos": [{"x": 600, "y": 314}],
  "voadores": [{"x": 500, "y": 150}],
  "proj_j1": [{"x": 200, "y": 350}],
  "proj_j2": [],
  "pontos": [9, 6],
  "fim_jogo": false
}
```

## Fluxo de execução

### PC A — Servidor
1. `javac Launcher.java && java Launcher`
2. Clicar **1 — Servidor**
3. Anota o IP exibido na tela (ex: `192.168.1.10`)
4. `ServidorJogo` sobe e aguarda dois clientes RMI

### PC B — Cliente
1. `javac Launcher.java && java Launcher`
2. Clicar **2 — Cliente**, digitar o IP do PC A
3. `BridgeJogo` conecta ao RMI do servidor
4. `main.py` é lançado automaticamente e conecta ao Bridge local

## Controles

| Jogador | Tecla | Ação |
|---|---|---|
| J1 | `←` `→` | Mover |
| J1 | `↑` | Pular |
| J1 | `K` | Atirar |
| J2 | `A` `D` `W` `S` | Mover (voa livre) |
| J2 | `V` | Atirar |
| Ambos | `U` | Mutar som |

## Dependências

**Java:** JDK 11+ (sem bibliotecas externas)

**Python:**
```
pygame
Pillow
```
