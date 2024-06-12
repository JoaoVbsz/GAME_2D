import pygame


class Background(pygame.sprite.Sprite):
    def __init__(self, width, height, *groups):
        super().__init__(*groups)
        self.image = pygame.image.load("fundo_game.png")
        self.image = pygame.transform.scale(self.image, [width, height])
        self.rect = pygame.Rect(0, 0, width, height)
