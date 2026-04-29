import pygame
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS


class Poder(pygame.sprite.Sprite):
    def __init__(self, largura, *grupos):
        super().__init__(*grupos)

        self.image = carregar_imagem(DIR_IMAGENS / "Fireball1.png").convert_alpha()
        self.image = pygame.transform.scale(self.image, [60, 60])
        self.mask = pygame.mask.from_surface(self.image)

        self.rect = self.image.get_rect()
        self.largura = largura
        self.velocidade = 6

    def update(self):
        self.rect.x += self.velocidade

        if self.rect.left > self.largura:
            self.kill()
