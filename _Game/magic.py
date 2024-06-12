from typing import Any
import pygame
import math
import random


class Magic(pygame.sprite.Sprite):
    def __init__(self, height, *groups):
        super().__init__(*groups)

        self.image = pygame.image.load("pb_magic.png")
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.rect = pygame.Rect(736, height - 100, 100, 100)

        self.rect.x = 736 + random.randint(1, 368)

        self.speed = 2.5

    def update(self, ):
        self.rect.x -= self.speed

        if self.rect.right < 0:
            self.kill()
