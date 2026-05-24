# ServidorJogo.java — Servidor RMI

**Pacote:** `servidor`
**Arquivo:** `GameJava/src/servidor/ServidorJogo.java`
**Porta:** 5555 (RMI Registry)

## Responsabilidade

Autoridade central da partida. Controla toda a física, o game loop e o estado do jogo. Expõe uma interface RMI para que clientes (Bridge) se conectem e recebam atualizações.

## Interface RMI

Implementa `IJogoServidor` (em `shared/`):

```java
public interface IJogoServidor extends Remote {
    int conectar(IClienteCallback callback) throws RemoteException;
    void processarInput(int playerId, List<String> teclas) throws RemoteException;
    void desconectar(int playerId) throws RemoteException;
}
```

## Como funciona

### Conexão
- Aceita até 2 clientes via `conectar(callback)`
- Cada cliente passa um `IClienteCallback` — o servidor usa para enviar o estado de volta
- Quando o 2º cliente conecta, o **game loop inicia automaticamente** em uma thread separada

### Game loop (30 FPS)
A cada tick:
1. Lê os inputs (`teclas0`, `teclas1`) com lock
2. Executa `tick()` — atualiza posições, colisões, spawn de inimigos
3. Serializa o estado em JSON via `StringBuilder` (sem bibliotecas)
4. Chama `callback.receberJson(json)` para cada cliente conectado

### Física — Jogador 1 (terrestre)
- Movimento horizontal com aceleração (`ACEL_J1 = 0.5`) e fricção (`FRICAO_J1 = 0.85`)
- Pulo com gravidade: `VEL_PULO = -15`, `GRAVIDADE = 1`
- Tiro: projétil vai para a direita com `VEL_PROJ = 7`
- Máximo de 5 tiros; recarga automática após 2 segundos

### Física — Jogador 2 (voador)
- Movimento livre em 4 direções (`VEL_J2 = 4`)
- Sem gravidade
- Tiro: projétil vai para a direita com `VEL_PROJ = 7`

### Inimigos
- **Terrestres:** surgem pela direita (`x > LARGURA`), andam para a esquerda (`-2.5/tick`)
- **Voadores:** surgem pela direita, movimento senoidal vertical (`Math.sin`)
- Colisão com projétil → +3 pontos para o jogador correspondente
- Sair pela esquerda sem ser abatido → -10 pontos
- Encostar no jogador → `fimJogo = true`

### Serialização JSON
Construída manualmente com `StringBuilder`. Exemplo de saída:
```json
{"tipo":"estado","j1":{"x":100,"y":314,"tiros":3,"recarregando":false},...}
```

## Callback RMI

O servidor chama `IClienteCallback.receberJson(String json)` em cada cliente a cada tick. O `BridgeJogo` implementa essa interface e repassa o JSON para o Python via TCP.

```java
public interface IClienteCallback extends Remote {
    void receberJson(String json) throws RemoteException;
}
```

## Inicialização

```java
public static void main(String[] args) {
    // Define IP da interface de rede local (não loopback)
    System.setProperty("java.rmi.server.hostname", ip);
    // Registra no RMI Registry na porta 5555
    LocateRegistry.createRegistry(5555);
    reg.rebind("ServidorJogo", new ServidorJogo());
}
```

## Constantes

| Constante | Valor | Descrição |
|---|---|---|
| `LARGURA` | 736 | Largura da tela em pixels |
| `ALTURA` | 414 | Altura da tela em pixels |
| `TICK_RATE` | 30 | Atualizações por segundo |
| `GRAVIDADE` | 1.0 | Aceleração vertical do J1 |
| `VEL_PULO` | -15.0 | Velocidade inicial do pulo |
| `MAX_TIROS` | 5 | Tiros antes de recarregar |
| `RECARGA_MS` | 2000 | Tempo de recarga em ms |
| `VEL_PROJ` | 7 | Velocidade dos projéteis |
