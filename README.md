# Time Out

Jogo 2D multiplayer em Java com arquitetura cliente-servidor via **Java RMI**. Dois magos defendem a arena contra ondas de inimigos — um terrestre, um voador.

![Java](https://img.shields.io/badge/Java-11%2B-blue) ![Swing](https://img.shields.io/badge/UI-Java%20Swing-orange) ![RMI](https://img.shields.io/badge/Rede-Java%20RMI-green)

---

## Modos de jogo

| Modo | Descrição |
|---|---|
| **1 Jogador** | Servidor embutido + cliente no mesmo processo. Jogue sozinho. |
| **2 Jogadores** | Um computador roda o servidor, o outro conecta via IP (rede local). |

---

## Requisitos

- **JDK 11 ou superior** (obrigatório — o Launcher compila o código em tempo real via `javax.tools`)
- Sistema operacional: Windows, Linux ou macOS
- Para multiplayer: ambas as máquinas na mesma rede local (LAN)

> **Atenção:** JRE sozeiro não funciona. Precisa ser JDK completo.

---

## Como executar

```bash
# Entre na pasta do jogo
cd GameJava

# Execute o Launcher (ele compila e abre o menu automaticamente)
java Launcher.java
```

O Launcher compila todos os fontes de `src/` para `out/` e abre a janela de menu.

---

## Multiplayer (LAN)

**Máquina que vai hospedar:**
1. Abra o jogo → clique **2 Jogadores** → **Servidor**
2. Anote o IP exibido na tela (ex: `192.168.1.10`)
3. Aguarde o cliente conectar

**Máquina que vai conectar:**
1. Abra o jogo → clique **2 Jogadores** → **Cliente**
2. Digite o IP do servidor
3. O jogo inicia quando os dois estiverem conectados

> No Windows, o Launcher abre as portas **5555–5557** no firewall automaticamente. No Linux/macOS, libere essas portas manualmente se necessário.

---

## Controles

### Jogador 1 — Mago Terrestre
| Tecla | Ação |
|---|---|
| `←` / `→` | Mover |
| `↑` | Pular |
| `K` | Atirar fireball |

### Jogador 2 — Mago Voador *(apenas multiplayer)*
| Tecla | Ação |
|---|---|
| `W` `A` `S` `D` | Mover (livre) |
| `V` | Atirar fireball |

### Menu
| Tecla | Ação |
|---|---|
| `1` | 1 Jogador |
| `2` | 2 Jogadores |
| `S` / `C` | Servidor / Cliente (tela multiplayer) |
| `ESC` | Voltar / Sair |

---

## Mecânicas

- **Inimigos terrestres** avançam da direita em direção ao J1
- **Inimigos voadores** se movem em ondas senoidais em direção ao J2
- Matar inimigo: **+3 pontos**
- Inimigo escapar pela esquerda: **−10 pontos**
- **Fim de jogo** quando qualquer inimigo tocar o seu jogador
- Cada jogador tem **5 tiros** com recarga de 2 segundos

---

## Estrutura do projeto

```
GameJava/
├── Launcher.java              # Ponto de entrada — compila e exibe o menu
├── src/
│   ├── shared/
│   │   ├── Configuracoes.java # Constantes globais (resolução, porta, FPS)
│   │   ├── IJogoServidor.java # Interface RMI do servidor
│   │   └── IClienteCallback.java # Interface RMI de callback
│   ├── servidor/
│   │   └── ServidorJogo.java  # Game loop, física, colisão, broadcast
│   └── cliente/
│       └── ClienteJogo.java   # Renderização Swing, input, conexão RMI
├── assets/
│   ├── imagens/               # Sprites dos personagens e fundo
│   └── sons/                  # Trilha sonora e efeitos
└── out/                       # Bytecode compilado (gerado automaticamente)
```

---

## Arquitetura

```
[Launcher]
    │
    ├── Modo Solo: inicia ServidorJogo (thread) + ClienteJogo(127.0.0.1)
    │
    └── Modo Multiplayer:
            Servidor → cria Registry RMI na porta 5555
                     → exporta ServidorJogo na porta 5556
                     → aguarda 2 callbacks de clientes
            Cliente  → conecta ao Registry
                     → registra callback na porta 5557
                     → envia inputs a 30 Hz
                     → recebe estado JSON e renderiza a 60 FPS
```

**Portas utilizadas:**
| Porta | Uso |
|---|---|
| `5555` | RMI Registry |
| `5556` | Objeto ServidorJogo exportado |
| `5557` | Callback do cliente |

--- 

**Criadores:**
- joão Vitor Souza (JoaoVbsz)
- Paulo Henrique Souza (HenriqueSZ5)
- Manoel Souza (manoelvsz)
