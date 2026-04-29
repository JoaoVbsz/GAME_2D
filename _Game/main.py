import pygame
import sys
import random
from configuracoes import LARGURA, ALTURA, FPS, TITULO, DIR_SONS
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

grupo_sprites = pygame.sprite.Group()
grupo_inimigos = pygame.sprite.Group()
grupo_inimigos_voadores = pygame.sprite.Group()
grupo_poderes = pygame.sprite.Group()
grupo_poderes2 = pygame.sprite.Group()

Fundo(LARGURA, ALTURA, grupo_sprites)
jogador = Jogador(ALTURA, grupo_sprites)
jogador.pode_pular = True   # single player: pulo sempre liberado
jogador2 = None
Inimigo(ALTURA, LARGURA, grupo_sprites, grupo_inimigos)

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
    fonte = pygame.font.SysFont(None, 72)
    fonte_ui = pygame.font.SysFont(None, 36)
except (pygame.error, NotImplementedError):
    fonte = None
    fonte_ui = None

relogio = pygame.time.Clock()
temporizador_terrestre = 15
temporizador_voador = 30
fim_jogo = False
som_mudo = False
pontos_j1 = 0
pontos_j2 = 0

def render_texto(texto, cor=(255, 255, 255), tamanho_multiplicador=3):
    # Usando Pillow para garantir o contorno (stroke) em todos os textos
    largura_estimada = max(len(texto) * 7, 20) + 10
    img = Image.new("RGBA", (largura_estimada, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.text((2, 2), texto, fill=(*cor, 255), stroke_width=1, stroke_fill=(0, 0, 0, 255))
    img = img.resize((img.width * tamanho_multiplicador, img.height * tamanho_multiplicador), Image.NEAREST)
    return pygame.image.frombytes(img.tobytes(), img.size, "RGBA")


def desenhar_fim_de_jogo():
    texto = render_texto("FIM DE JOGO", (220, 50, 50), tamanho_multiplicador=6)
    tela.blit(texto, (LARGURA // 2 - texto.get_width() // 2, ALTURA // 2 - texto.get_height() // 2))


if __name__ == "__main__":

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
                    # Alternar Jogador 2 (Multiplayer local)
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

            # spawn inimigo terrestre
            temporizador_terrestre += 1
            if temporizador_terrestre > 30:
                temporizador_terrestre = 0
                if random.random() < 0.3:
                    Inimigo(ALTURA, LARGURA, grupo_sprites, grupo_inimigos)

            # spawn inimigo voador
            temporizador_voador += 1
            if temporizador_voador > 50:
                temporizador_voador = 0
                if random.random() < 0.25:
                    InimigoVoador(jogador2 is None, grupo_sprites, grupo_inimigos_voadores)

            # colisão J1 com inimigos terrestres
            if pygame.sprite.spritecollide(jogador, grupo_inimigos, False, pygame.sprite.collide_mask):
                fim_jogo = True

            if jogador2 is None:
                # colisão J1 com inimigos voadores (single player: J1 é o único defensor)
                if pygame.sprite.spritecollide(jogador, grupo_inimigos_voadores, False, pygame.sprite.collide_mask):
                    fim_jogo = True

                # poder J1 destrói ambos os tipos (single player)
                abates_terra = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos, True, True, pygame.sprite.collide_mask)
                abates_ar = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos_voadores, True, True, pygame.sprite.collide_mask)
                
                for poder in abates_terra:
                    pontos_j1 += 3 * len(abates_terra[poder])
                for poder in abates_ar:
                    pontos_j1 += 3 * len(abates_ar[poder])
            else:
                # colisão J2 com inimigos voadores
                if pygame.sprite.spritecollide(jogador2, grupo_inimigos_voadores, False, pygame.sprite.collide_mask):
                    fim_jogo = True
                    
                # poder J1 destrói terrestres, poder J2 destrói voadores
                abates_terra = pygame.sprite.groupcollide(grupo_poderes, grupo_inimigos, True, True, pygame.sprite.collide_mask)
                abates_ar = pygame.sprite.groupcollide(grupo_poderes2, grupo_inimigos_voadores, True, True, pygame.sprite.collide_mask)
                
                for poder in abates_terra:
                    pontos_j1 += 3 * len(abates_terra[poder])
                for poder in abates_ar:
                    pontos_j2 += 3 * len(abates_ar[poder])

            # Verifica inimigos que passaram da tela
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
            # J1 UI (Esquerda)
            texto_pts_j1 = render_texto(f"J1 Pts: {pontos_j1}", (255, 255, 255))
            tela.blit(texto_pts_j1, (20, 20))
            
            str_tiros_j1 = "Recarregando..." if jogador.recarregando else f"Tiros: {jogador.tiros}/5"
            texto_tiros_j1 = render_texto(str_tiros_j1, (255, 255, 255))
            tela.blit(texto_tiros_j1, (20, 20 + texto_pts_j1.get_height() + 5))

            # J2 UI (Direita)
            if jogador2 is not None:
                texto_pts_j2 = render_texto(f"J2 Pts: {pontos_j2}", (255, 255, 255))
                tela.blit(texto_pts_j2, (LARGURA - texto_pts_j2.get_width() - 20, 20))
                
                str_tiros_j2 = "Recarregando..." if jogador2.recarregando else f"Tiros: {jogador2.tiros}/5"
                texto_tiros_j2 = render_texto(str_tiros_j2, (255, 255, 255))
                tela.blit(texto_tiros_j2, (LARGURA - texto_tiros_j2.get_width() - 20, 20 + texto_pts_j2.get_height() + 5))

        if fim_jogo:
            desenhar_fim_de_jogo()

        pygame.display.update()
