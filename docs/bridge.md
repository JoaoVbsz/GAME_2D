# BridgeJogo.java — Bridge RMI/TCP

**Pacote:** `bridge`
**Arquivo:** `GameJava/src/bridge/BridgeJogo.java`
**Porta TCP:** 5556 (aguarda Python)
**Porta RMI:** 5555 (conecta ao servidor)

## Responsabilidade

Componente intermediário que traduz entre dois protocolos incompatíveis:
- **RMI** (lado Java, falando com `ServidorJogo`)
- **TCP+JSON** (lado Python, falando com `main.py`)

Sem a Bridge, Python não consegue falar com um servidor Java RMI.

## Diagrama de fluxo

```
main.py (Python)
    │
    │  TCP+JSON :5556
    ▼
BridgeJogo (Java)
    │  ← implementa IClienteCallback via RMI
    │  → chama IJogoServidor via RMI
    │
    │  RMI :5555
    ▼
ServidorJogo (Java)
```

## Classe interna: `Ponte`

Cada conexão Python cria uma instância de `Ponte`, que:
- Implementa `IClienteCallback` (RMI) — recebe estado do servidor
- Roda como `Runnable` em thread separada
- Lê inputs do Python e encaminha ao servidor via RMI

```
Ponte
├── implements IClienteCallback  → receberJson(json) vindo do servidor
├── implements Runnable          → loop de leitura do socket Python
└── chama srv.processarInput()  → envia inputs ao servidor via RMI
```

## Sequência de inicialização

```
1. BridgeJogo abre ServerSocket na porta 5556
2. Aguarda até 2 conexões Python (TCP)
3. Para cada conexão:
   a. Cria instância de Ponte
   b. Ponte se exporta como objeto RMI (UnicastRemoteObject)
   c. Ponte conecta ao ServidorJogo via Registry.lookup("ServidorJogo")
   d. Chama srv.conectar(this) → recebe player_id (0 ou 1)
   e. Envia handshake para Python: {"tipo":"conectado","player_id":N}
   f. Inicia loop de leitura de inputs Python
```

## Fluxo de dados — Input (Python → Servidor)

```
Python envia:  {"tipo":"input","player_id":0,"teclas":["RIGHT","K"]}\n
                                │
                     Ponte.run() lê linha
                                │
                     parseInput() extrai ["RIGHT","K"]
                                │
                     srv.processarInput(0, ["RIGHT","K"])  ← RMI call
                                │
                     ServidorJogo.teclas0 = {"RIGHT","K"}
```

## Fluxo de dados — Estado (Servidor → Python)

```
ServidorJogo executa tick()
                    │
         callback.receberJson(json)  ← RMI call
                    │
         Ponte.receberJson(json) é invocado via RMI
                    │
         out.println(json)  ← escreve na conexão TCP do Python
                    │
         Python recebe e renderiza
```

## Parsing de input

O parser de input é manual (sem biblioteca JSON) para evitar dependências:

```java
// Extrai teclas de: {"tipo":"input","player_id":0,"teclas":["RIGHT","K"]}
if (line.contains("\"teclas\":[")) {
    int start = line.indexOf("[") + 1;
    int end   = line.indexOf("]");
    String keysStr = line.substring(start, end).replace("\"", "");
    // → "RIGHT,K"
    for (String k : keysStr.split(",")) teclas.add(k.trim());
}
```

## Desconexão

Quando o Python fecha o socket (ou ocorre erro de rede):
- `in.readLine()` retorna `null`
- `finally` chama `srv.desconectar(playerId)` via RMI
- `ServidorJogo` seta `fimJogo = true`

## Como é lançado

O `Launcher.java` inicia a Bridge assim:

```java
// 1. Inicia Bridge em thread separada (não bloqueia o Launcher)
new Thread(() -> rodar("bridge.BridgeJogo", ipServidor)).start();

// 2. Aguarda Bridge estar pronta
Thread.sleep(800);

// 3. Lança Python conectando na Bridge local
rodarPython("127.0.0.1");
```

O Python sempre conecta em `127.0.0.1:5556` (Bridge local), nunca diretamente no servidor remoto.
