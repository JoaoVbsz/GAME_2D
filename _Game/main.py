import pygame
import sys
import random
import socket
import threading
from configuracoes import LARGURA, ALTURA, FPS, TITULO, DIR_SONS, PORTA
from PIL import Image, ImageDraw
from sprites.fundo import Fundo
from sprites.jogador import Jogador
from sprites.inimigo import Inimigo
from sprites.inimigo_voador import InimigoVoador
from sprites.poder import Poder
from sprites.jogador2 import Jogador2
from sprites.poder2 import Poder2

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
    fonte_ui = pygame.font.SysFont(None, 36)
except (pygame.error, NotImplementedError):
    fonte_ui = None


def render_texto(texto, cor=(255, 255, 255), tamanho_multiplicador=3):
    largura_estimada = max(len(texto) * 7, 20) + 10
    img = Image.new("RGBA", (largura_estimada, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.text((2, 2), texto, fill=(*cor, 255), stroke_width=1, stroke_fill=(0, 0, 0, 255))
    img = img.resize((img.width * tamanho_multiplicador, img.height * tamanho_multiplicador), Image.NEAREST)
    return pygame.image.frombytes(img.tobytes(), img.size, "RGBA")


def desenhar_fim_de_jogo():
    texto = render_texto("FIM DE JOGO", (220, 50, 50), tamanho_multiplicador=6)
    tela.blit(texto, (LARGURA // 2 - texto.get_width() // 2, ALTURA // 2 - texto.get_height() // 2))


# ─── Parse argumentos ────────────────────────────────────────────────────────
modo = "local"
ip_join = None

if "--host" in sys.argv:
    modo = "host"
elif "--join" in sys.argv:
    idx = sys.argv.index("--join")
    ip_join = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else None
    modo = "guest" if ip_join else "local"


# ─── MODO REDE ────────────────────────────────────────────────────────────────
def obter_ip_local():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def tela_lobby(mensagem: str, sub: str = ""):
    fundo_cor = (20, 20, 40)
    tela.fill(fundo_cor)
    txt = render_texto(mensagem, (200, 220, 255), tamanho_multiplicador=4)
    tela.blit(txt, (LARGURA // 2 - txt.get_width() // 2, ALTURA // 2 - txt.get_height() // 2 - 30))
    if sub:
        txt2 = render_texto(sub, (160, 160, 200), tamanho_multiplicador=3)
        tela.blit(txt2, (LARGURA // 2 - txt2.get_width() // 2, ALTURA // 2 + 20))
    pygame.display.update()


def tela_digitar_ip():
    ip_digitado = ""
    instrucao = render_texto("IP do Host:", (200, 220, 255), 4)
    while True:
        relogio.tick(30)
        for ev in pygame.event.get():
            if ev.type == pygame.QUIT:
                pygame.quit()
                sys.exit()
            elif ev.type == pygame.KEYDOWN:
                if ev.key == pygame.K_RETURN and ip_digitado:
                    return ip_digitado
                elif ev.key == pygame.K_BACKSPACE:
                    ip_digitado = ip_digitado[:-1]
                elif ev.unicode in "0123456789.":
                    ip_digitado += ev.unicode
        tela.fill((20, 20, 40))
        tela.blit(instrucao, (LARGURA // 2 - instrucao.get_width() // 2, ALTURA // 2 - 60))
        txt_ip = render_texto(ip_digitado or "_", (255, 255, 100), 4)
        tela.blit(txt_ip, (LARGURA // 2 - txt_ip.get_width() // 2, ALTURA // 2))
        pygame.display.update()


def tela_menu():
    """Retorna ('host', None) ou ('guest', <ip>)."""
    instrucoes = [
        render_texto("[H] Hospedar partida", (100, 220, 100), 3),
        render_texto("[E] Entrar na partida", (100, 180, 255), 3),
        render_texto("[ESC] Sair", (150, 150, 150), 3),
    ]
    titulo = render_texto("TIME OUT", (220, 100, 50), 6)
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
        tela.blit(titulo, (LARGURA // 2 - titulo.get_width() // 2, 60))
        for i, txt in enumerate(instrucoes):
            tela.blit(txt, (LARGURA // 2 - txt.get_width() // 2, 180 + i * 55))
        pygame.display.update()


def loop_rede(cliente):
    """Loop principal no modo rede."""
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

    cor_proj_j1 = (255, 200, 50)
    cor_proj_j2 = (100, 200, 255)

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
            tela_lobby("Desconectado", cliente.erro or "")
            relogio.tick(5)
            continue

        # Captura teclas e envia
        pressed = pygame.key.get_pressed()
        teclas_ativas = [nome for key, nome in MAPA_TECLAS.items() if pressed[key]]
        cliente.enviar_input(teclas_ativas)

        estado = cliente.estado_atual
        if estado is None:
            tela_lobby("Aguardando estado...", "")
            continue

        # Render fundo
        grupo_fundo.draw(tela)

        # Render inimigos
        for e in estado.get("inimigos", []):
            tela.blit(img_ini, (e["x"], e["y"]))
        for e in estado.get("voadores", []):
            tela.blit(img_voa, (e["x"], e["y"]))

        # Render projéteis
        for p in estado.get("proj_j1", []):
            pygame.draw.rect(tela, cor_proj_j1, (int(p["x"]), int(p["y"]), 20, 20))
        for p in estado.get("proj_j2", []):
            pygame.draw.rect(tela, cor_proj_j2, (int(p["x"]), int(p["y"]), 20, 20))

        # Render jogadores
        j1 = estado.get("j1", {})
        j2 = estado.get("j2", {})
        tela.blit(img_j1, (j1.get("x", 50), j1.get("y", ALTURA - 100)))
        tela.blit(img_j2, (j2.get("x", 10), j2.get("y", ALTURA // 2)))

        pontos = estado.get("pontos", [0, 0])
        fim = estado.get("fim_jogo", False)

        if not fim:
            txt_p1 = render_texto(f"J1 Pts: {pontos[0]}")
            tela.blit(txt_p1, (20, 20))
            str_t1 = "Recarregando..." if j1.get("recarregando") else f"Tiros: {j1.get('tiros', 0)}/5"
            tela.blit(render_texto(str_t1), (20, 20 + txt_p1.get_height() + 5))

            txt_p2 = render_texto(f"J2 Pts: {pontos[1]}")
            tela.blit(txt_p2, (LARGURA - txt_p2.get_width() - 20, 20))
            str_t2 = "Recarregando..." if j2.get("recarregando") else f"Tiros: {j2.get('tiros', 0)}/5"
            txt_t2 = render_texto(str_t2)
            tela.blit(txt_t2, (LARGURA - txt_t2.get_width() - 20, 20 + txt_p2.get_height() + 5))

            pid_txt = render_texto(f"Voce e J{cliente.player_id + 1}", (200, 200, 100), 2)
            tela.blit(pid_txt, (LARGURA // 2 - pid_txt.get_width() // 2, 5))

        if fim:
            desenhar_fim_de_jogo()

        pygame.display.update()


# ─── MODO LOCAL (código original) ────────────────────────────────────────────
def loop_local():
    grupo_sprites = pygame.sprite.Group()
    grupo_inimigos = pygame.sprite.Group()
    grupo_inimigos_voadores = pygame.sprite.Group()
    grupo_poderes = pygame.sprite.Group()
    grupo_poderes2 = pygame.sprite.Group()

    Fundo(LARGURA, ALTURA, grupo_sprites)
    jogador = Jogador(ALTURA, grupo_sprites)
    jogador.pode_pular = True
    jogador2 = None
    Inimigo(ALTURA, LARGURA, grupo_sprites, grupo_inimigos)

    temporizador_terrestre = 15
    temporizador_voador = 30
    fim_jogo = False
    som_mudo = False
    pontos_j1 = 0
    pontos_j2 = 0

    while True:
        relogio.tick(FPS)

        for evento in pygame.event.get():
            if evento.type == pygame.QUIT:
                pygame.quit()
                sys.exit()

            elif evento.type == pygame.KEYDOWN:
                if evento.key == pygame.K_k and not fim_jogo:
                    if not jogador.recarregando and jogador.tiros > 0:
                        jogador.tiros -= 1
                        if jogador.tiros == 0:
                            jogador.recarregando = True
                            jogador.tempo_inicio_recarga = pygame.time.get_ticks()
                        if som_ataque:
                            som_ataque.play()
                        novo_poder = Poder(LARGURA, grupo_sprites, grupo_poderes)
                        novo_poder.rect.center = jogador.rect.center

                elif evento.key == pygame.K_u:
                    som_mudo = not som_mudo
                    try:
                        pygame.mixer.music.set_volume(0 if som_mudo else 1)
                    except (NotImplementedError, pygame.error):
                        pass

                elif evento.key == pygame.K_2:
                    if jogador2 is None:
                        jogador2 = Jogador2(grupo_sprites)
                        jogador.pode_pular = False
                    else:
                        jogador2.kill()
                        jogador2 = None
                        jogador.pode_pular = True

                elif evento.key == pygame.K_v and not fim_jogo:
                    if jogador2 is not None and not jogador2.recarregando and jogador2.tiros > 0:
                        jogador2.tiros -= 1
                        if jogador2.tiros == 0:
                            jogador2.recarregando = True
                            jogador2.tempo_inicio_recarga = pygame.time.get_ticks()
                        if som_ataque:
                            som_ataque.play()
                        novo_poder = Poder2(LARGURA, grupo_sprites, grupo_poderes2)
                        novo_poder.rect.center = jogador2.rect.center

        if not fim_jogo:
            grupo_sprites.update()

            temporizador_terrestre += 1
            if temporizador_terrestre > 30:
                temporizador_terrestre = 0
                if random.random() < 0.3:
                    Inimigo(ALTURA, LARGURA, grupo_sprites, grupo_inimigos)

            temporizador_voador += 1
            if temporizador_voador > 50:
                temporizador_voador = 0
                if random.random() < 0.25:
                    InimigoVoador(jogador2 is None, grupo_sprites, grupo_inimigos_voadores)

            if pygame.sprite.spritecollide(jogador, grupo_inimigos, False, pygame.sprite.collide_mask):
                fim_jogo = True

            if jogador2 is None:
                if pygame.sprite.spritecollide(jogador, grupo_inimigos_voadores, False, pygame.sprite.collide_mask):
                    fim_jogo = True
                abates_terra = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos, True, True, pygame.sprite.collide_mask)
                abates_ar = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos_voadores, True, True, pygame.sprite.collide_mask)
                for poder in abates_terra:
                    pontos_j1 += 3 * len(abates_terra[poder])
                for poder in abates_ar:
                    pontos_j1 += 3 * len(abates_ar[poder])
            else:
                if pygame.sprite.spritecollide(jogador2, grupo_inimigos_voadores, False, pygame.sprite.collide_mask):
                    fim_jogo = True
                abates_terra = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos, True, True, pygame.sprite.collide_mask)
                abates_ar = pygame.sprite.groupcollide(grupo_poderes2, grupo_inimigos_voadores, True, True, pygame.sprite.collide_mask)
                for poder in abates_terra:
                    pontos_j1 += 3 * len(abates_terra[poder])
                for poder in abates_ar:
                    pontos_j2 += 3 * len(abates_ar[poder])

            for inimigo in list(grupo_inimigos):
                if inimigo.rect.right < 0:
                    pontos_j1 -= 10
                    inimigo.kill()
            for voador in list(grupo_inimigos_voadores):
                if voador.rect.right < 0:
                    if jogador2 is None:
                        pontos_j1 -= 10
                    else:
                        pontos_j2 -= 10
                    voador.kill()

        grupo_sprites.draw(tela)

        if not fim_jogo:
            txt_pts_j1 = render_texto(f"J1 Pts: {pontos_j1}")
            tela.blit(txt_pts_j1, (20, 20))
            str_t1 = "Recarregando..." if jogador.recarregando else f"Tiros: {jogador.tiros}/5"
            tela.blit(render_texto(str_t1), (20, 20 + txt_pts_j1.get_height() + 5))

            if jogador2 is not None:
                txt_pts_j2 = render_texto(f"J2 Pts: {pontos_j2}")
                tela.blit(txt_pts_j2, (LARGURA - txt_pts_j2.get_width() - 20, 20))
                str_t2 = "Recarregando..." if jogador2.recarregando else f"Tiros: {jogador2.tiros}/5"
                txt_t2 = render_texto(str_t2)
                tela.blit(txt_t2, (LARGURA - txt_t2.get_width() - 20, 20 + txt_pts_j2.get_height() + 5))

        if fim_jogo:
            desenhar_fim_de_jogo()

        pygame.display.update()


# ─── ENTRY POINT ─────────────────────────────────────────────────────────────
if __name__ == "__main__":
    if modo == "local":
        # Mostrar menu para escolher local ou rede
        escolha, ip_escolhido = tela_menu()
        if escolha == "host":
            modo = "host"
        else:
            modo = "guest"
            ip_join = ip_escolhido

    if modo == "host":
        from rede.servidor import Servidor
        from rede.cliente import Cliente

        srv = Servidor()
        t_srv = threading.Thread(target=srv.iniciar, daemon=True)
        t_srv.start()

        ip_local = obter_ip_local()
        tela_lobby("Aguardando J2...", f"IP: {ip_local}  Porta: {PORTA}")

        cli = Cliente()
        # Aguarda servidor estar pronto
        import time
        for _ in range(20):
            try:
                cli.conectar("127.0.0.1", PORTA)
                break
            except OSError:
                time.sleep(0.3)

        # Aguarda player_id chegar
        for _ in range(50):
            if cli.player_id is not None:
                break
            time.sleep(0.1)

        loop_rede(cli)

    elif modo == "guest":
        from rede.cliente import Cliente
        import time

        tela_lobby("Conectando...", ip_join or "")
        cli = Cliente()
        try:
            cli.conectar(ip_join, PORTA)
        except OSError as e:
            tela_lobby("Erro ao conectar", str(e))
            time.sleep(3)
            pygame.quit()
            sys.exit()

        # Aguarda player_id
        for _ in range(50):
            if cli.player_id is not None:
                break
            time.sleep(0.1)

        loop_rede(cli)
