"""
Cliente do jogo Time Out — Multiplayer em Rede
Rodar: python main.py
  [H] Hospedar — inicia servidor interno e entra como J1
  [E] Entrar   — conecta no IP do host
"""
import pygame
import sys
import socket
import threading
import time
from configuracoes import LARGURA, ALTURA, FPS, TITULO, DIR_SONS, PORTA
from PIL import Image, ImageDraw
from sprites.fundo import Fundo

pygame.init()

tela = pygame.display.set_mode((LARGURA, ALTURA))
pygame.display.set_caption(TITULO)
relogio = pygame.time.Clock()

try:
    pygame.mixer.music.load(str(DIR_SONS / "som_game.wav"))
    pygame.mixer.music.play(-1)
except (pygame.error, NotImplementedError, FileNotFoundError):
    pass

try:
    som_ataque = pygame.mixer.Sound(str(DIR_SONS / "som_ataque.wav"))
except (pygame.error, NotImplementedError, FileNotFoundError):
    som_ataque = None

try:
    pygame.font.SysFont(None, 36)
except (pygame.error, NotImplementedError):
    pass


# ─── Utilitários de render ────────────────────────────────────────────────────

def render_texto(texto, cor=(255, 255, 255), tamanho_multiplicador=3):
    largura_estimada = max(len(texto) * 7, 20) + 10
    img = Image.new("RGBA", (largura_estimada, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.text((2, 2), texto, fill=(*cor, 255), stroke_width=1, stroke_fill=(0, 0, 0, 255))
    img = img.resize((img.width * tamanho_multiplicador, img.height * tamanho_multiplicador), Image.NEAREST)
    return pygame.image.frombytes(img.tobytes(), img.size, "RGBA")


def tela_info(linha1, linha2=""):
    tela.fill((20, 20, 40))
    t1 = render_texto(linha1, (200, 220, 255), 4)
    tela.blit(t1, (LARGURA // 2 - t1.get_width() // 2, ALTURA // 2 - t1.get_height() // 2 - 25))
    if linha2:
        t2 = render_texto(linha2, (160, 160, 200), 3)
        tela.blit(t2, (LARGURA // 2 - t2.get_width() // 2, ALTURA // 2 + 25))
    pygame.display.update()


def tela_digitar_ip():
    ip = ""
    label = render_texto("IP do servidor:", (200, 220, 255), 4)
    while True:
        relogio.tick(30)
        for ev in pygame.event.get():
            if ev.type == pygame.QUIT:
                pygame.quit()
                sys.exit()
            elif ev.type == pygame.KEYDOWN:
                if ev.key == pygame.K_RETURN and ip:
                    return ip
                elif ev.key == pygame.K_BACKSPACE:
                    ip = ip[:-1]
                elif ev.unicode in "0123456789.":
                    ip += ev.unicode
        tela.fill((20, 20, 40))
        tela.blit(label, (LARGURA // 2 - label.get_width() // 2, ALTURA // 2 - 60))
        cursor = render_texto(ip or "_", (255, 255, 100), 4)
        tela.blit(cursor, (LARGURA // 2 - cursor.get_width() // 2, ALTURA // 2))
        dica = render_texto("Enter para conectar  |  ESC voltar", (100, 100, 150), 2)
        tela.blit(dica, (LARGURA // 2 - dica.get_width() // 2, ALTURA // 2 + 60))
        pygame.display.update()


def tela_menu():
    """Retorna ('host', None) ou ('guest', <ip>)."""
    titulo   = render_texto("TIME OUT", (220, 100, 50), 6)
    opt_h    = render_texto("[H]  Hospedar partida", (100, 220, 100), 3)
    opt_e    = render_texto("[E]  Entrar na partida", (100, 180, 255), 3)
    opt_esc  = render_texto("[ESC]  Sair", (130, 130, 130), 3)
    while True:
        relogio.tick(30)
        for ev in pygame.event.get():
            if ev.type == pygame.QUIT:
                pygame.quit()
                sys.exit()
            elif ev.type == pygame.KEYDOWN:
                if ev.key == pygame.K_h:
                    return ("host", None)
                elif ev.key == pygame.K_e:
                    ip = tela_digitar_ip()
                    return ("guest", ip)
                elif ev.key == pygame.K_ESCAPE:
                    pygame.quit()
                    sys.exit()
        tela.fill((20, 20, 40))
        tela.blit(titulo, (LARGURA // 2 - titulo.get_width() // 2, 55))
        tela.blit(opt_h,   (LARGURA // 2 - opt_h.get_width() // 2, 180))
        tela.blit(opt_e,   (LARGURA // 2 - opt_e.get_width() // 2, 240))
        tela.blit(opt_esc, (LARGURA // 2 - opt_esc.get_width() // 2, 320))
        pygame.display.update()


def obter_ip_local():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def desenhar_fim_de_jogo():
    texto = render_texto("FIM DE JOGO", (220, 50, 50), tamanho_multiplicador=6)
    tela.blit(texto, (LARGURA // 2 - texto.get_width() // 2, ALTURA // 2 - texto.get_height() // 2))


# ─── Loop principal em modo rede ──────────────────────────────────────────────

def loop_rede(cliente):
    from sprites.carregador import carregar_imagem
    from configuracoes import DIR_IMAGENS

    img_j1 = pygame.transform.scale(
        carregar_imagem(DIR_IMAGENS / "mago_player.png").convert_alpha(), (100, 100))
    img_j2 = pygame.transform.scale(
        carregar_imagem(DIR_IMAGENS / "mago2_novo.png").convert_alpha(), (100, 100))
    img_ini = pygame.transform.flip(
        pygame.transform.scale(
            carregar_imagem(DIR_IMAGENS / "inimigo_novo.png").convert_alpha(), (100, 100)), True, False)
    img_voa = pygame.transform.flip(
        pygame.transform.scale(
            carregar_imagem(DIR_IMAGENS / "inimigo_voador.png").convert_alpha(), (80, 80)), True, False)

    grupo_fundo = pygame.sprite.Group()
    Fundo(LARGURA, ALTURA, grupo_fundo)

    MAPA_TECLAS = {
        pygame.K_RIGHT: "RIGHT", pygame.K_LEFT: "LEFT", pygame.K_UP: "UP",
        pygame.K_k: "K",
        pygame.K_a: "A", pygame.K_d: "D", pygame.K_w: "W", pygame.K_s: "S",
        pygame.K_v: "V",
    }
    som_mudo = False

    while True:
        relogio.tick(FPS)

        for ev in pygame.event.get():
            if ev.type == pygame.QUIT:
                cliente.desconectar()
                pygame.quit()
                sys.exit()
            elif ev.type == pygame.KEYDOWN and ev.key == pygame.K_u:
                som_mudo = not som_mudo
                try:
                    pygame.mixer.music.set_volume(0 if som_mudo else 1)
                except Exception:
                    pass

        if not cliente.conectado:
            tela_info("Desconectado", cliente.erro or "")
            relogio.tick(5)
            continue

        pressed = pygame.key.get_pressed()
        teclas = [nome for key, nome in MAPA_TECLAS.items() if pressed[key]]
        cliente.enviar_input(teclas)

        estado = cliente.estado_atual
        if estado is None:
            tela_info("Aguardando 2o jogador...", f"IP: {obter_ip_local()}  Porta: {PORTA}")
            continue

        grupo_fundo.draw(tela)

        for e in estado.get("inimigos", []):
            tela.blit(img_ini, (e["x"], e["y"]))
        for e in estado.get("voadores", []):
            tela.blit(img_voa, (e["x"], e["y"]))

        for p in estado.get("proj_j1", []):
            pygame.draw.rect(tela, (255, 200, 50), (int(p["x"]), int(p["y"]), 20, 20))
        for p in estado.get("proj_j2", []):
            pygame.draw.rect(tela, (100, 200, 255), (int(p["x"]), int(p["y"]), 20, 20))

        j1 = estado.get("j1", {})
        j2 = estado.get("j2", {})
        tela.blit(img_j1, (j1.get("x", 50), j1.get("y", ALTURA - 100)))
        tela.blit(img_j2, (j2.get("x", 10), j2.get("y", ALTURA // 2)))

        pontos = estado.get("pontos", [0, 0])
        fim = estado.get("fim_jogo", False)

        if not fim:
            t_p1 = render_texto(f"J1 Pts: {pontos[0]}")
            tela.blit(t_p1, (20, 20))
            str_t1 = "Recarregando..." if j1.get("recarregando") else f"Tiros: {j1.get('tiros', 0)}/5"
            tela.blit(render_texto(str_t1), (20, 20 + t_p1.get_height() + 5))

            t_p2 = render_texto(f"J2 Pts: {pontos[1]}")
            tela.blit(t_p2, (LARGURA - t_p2.get_width() - 20, 20))
            str_t2 = "Recarregando..." if j2.get("recarregando") else f"Tiros: {j2.get('tiros', 0)}/5"
            t_t2 = render_texto(str_t2)
            tela.blit(t_t2, (LARGURA - t_t2.get_width() - 20, 20 + t_p2.get_height() + 5))

            pid_txt = render_texto(f"Voce e J{cliente.player_id + 1}", (200, 200, 100), 2)
            tela.blit(pid_txt, (LARGURA // 2 - pid_txt.get_width() // 2, 5))

        if fim:
            desenhar_fim_de_jogo()

        pygame.display.update()


# ─── Entry point ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    from rede.cliente import Cliente
    from rede.servidor import Servidor

    escolha, ip_guest = tela_menu()

    if escolha == "host":
        srv = Servidor()
        threading.Thread(target=srv.iniciar, daemon=True).start()
        ip_conectar = "127.0.0.1"
        tela_info("Aguardando 2o jogador...", f"Seu IP: {obter_ip_local()}  Porta: {PORTA}")
    else:
        ip_conectar = ip_guest

    cli = Cliente()
    tela_info("Conectando...", ip_conectar)
    for _ in range(20):
        try:
            cli.conectar(ip_conectar, PORTA)
            break
        except OSError:
            time.sleep(0.3)
    else:
        tela_info("Erro ao conectar", ip_conectar)
        time.sleep(3)
        pygame.quit()
        sys.exit()

    for _ in range(50):
        if cli.player_id is not None:
            break
        time.sleep(0.1)

    loop_rede(cli)
