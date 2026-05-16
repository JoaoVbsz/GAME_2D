import pygame
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS, LARGURA


class Jogador(pygame.sprite.Sprite):
    def __init__(self, altura, *grupos):
        super().__init__(*grupos)

        self.image = carregar_imagem(DIR_IMAGENS / "mago_player.png").convert_alpha()
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.mask = pygame.mask.from_surface(self.image)

        self.altura = altura
        self.rect = pygame.Rect(50, altura - 100, 100, 100)

        self.esta_pulando = False
        self.vel_pulo = 0
        self.gravidade = 1
        self.altura_pulo = 15

        self.velocidade = 0
        self.aceleracao = 0.5

        self.pode_pular = True

        self.tiros = 5
        self.recarregando = False
        self.tempo_inicio_recarga = 0

    def update(self):
        agora = pygame.time.get_ticks()
        if self.recarregando:
            if agora - self.tempo_inicio_recarga >= 2000:
                self.recarregando = False
                self.tiros = 5

        teclas = pygame.key.get_pressed()

        if teclas[pygame.K_RIGHT]:
            self.velocidade += self.aceleracao
        elif teclas[pygame.K_LEFT]:
            self.velocidade -= self.aceleracao
        else:
            self.velocidade *= 0.85

        self.rect.x += int(self.velocidade)

        if self.pode_pular and not self.esta_pulando and teclas[pygame.K_UP]:
            self.esta_pulando = True
            self.vel_pulo = -self.altura_pulo

        if self.esta_pulando:
            self.rect.y += self.vel_pulo
            self.vel_pulo += self.gravidade

            if self.rect.y >= self.altura - 100:
                self.rect.y = self.altura - 100
                self.esta_pulando = False
                self.vel_pulo = 0

        if self.rect.x < 0:
            self.rect.x = 0
            self.velocidade = 0

        if self.rect.right > LARGURA:
            self.rect.right = LARGURA
            self.velocidade = 0
