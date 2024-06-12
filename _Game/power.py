from typing import Any
import pygame
import math
import random


class Power(pygame.sprite.Sprite):
    def __init__(self, *groups):
        super().__init__(*groups)

        self.image = pygame.image.load("atack_game.png")
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.rect = self.image.get_rect()
        self.speed = 4

    def update(self, ):
        self.rect.x += self.speed

        if self.rect.left > 736:
            self.kill()
