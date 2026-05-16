import pygame
import math
import random
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS, LARGURA, ALTURA


class InimigoVoador(pygame.sprite.Sprite):
    def __init__(self, single_player, *grupos):
        super().__init__(*grupos)

        self.image = carregar_imagem(DIR_IMAGENS / "inimigo_voador.png").convert_alpha()
        self.image = pygame.transform.scale(self.image, [80, 80])
        self.image = pygame.transform.flip(self.image, True, False)
        self.mask = pygame.mask.from_surface(self.image)

        if single_player:
            min_y = ALTURA - 220
            max_y = ALTURA - 150
            self.y_base = random.randint(min_y, max_y)
        else:
            self.y_base = random.randint(int(ALTURA * 0.1), int(ALTURA * 0.5))

        self.rect = pygame.Rect(
            LARGURA + random.randint(0, LARGURA // 3),
            self.y_base, 80, 80
        )

        self.velocidade = 3.0
        self.tick = 0

    def update(self):
        self.rect.x -= self.velocidade
        self.rect.y = self.y_base + int(math.sin(self.tick * 0.04) * 40)
        self.tick += 1
