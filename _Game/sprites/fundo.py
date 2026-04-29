import pygame
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS


class Fundo(pygame.sprite.Sprite):
    def __init__(self, largura, altura, *grupos):
        super().__init__(*grupos)
        self.image = carregar_imagem(DIR_IMAGENS / "fundo_game.png").convert()
        self.image = pygame.transform.scale(self.image, [largura, altura])
        self.rect = pygame.Rect(0, 0, largura, altura)
