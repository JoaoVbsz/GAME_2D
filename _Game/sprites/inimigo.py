import pygame
import random
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS


class Inimigo(pygame.sprite.Sprite):
    def __init__(self, altura, largura, *grupos):
        super().__init__(*grupos)

        self.image = carregar_imagem(DIR_IMAGENS / "inimigo_novo.png").convert_alpha()
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.image = pygame.transform.flip(self.image, True, False)
        self.mask = pygame.mask.from_surface(self.image)

        self.largura = largura
        self.rect = pygame.Rect(largura + random.randint(1, largura // 2), altura - 100, 100, 100)

        self.velocidade = 2.5

    def update(self):
        self.rect.x -= self.velocidade
