from typing import Any
import pygame

class Player(pygame.sprite.Sprite):
    def __init__(self, height, *groups):
        super().__init__(*groups)

        self.image = pygame.image.load("mago_player.png")
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.height = height
        self.rect = pygame.Rect(50, height - 100, 100, 100)

        self.is_jumping = False
        self.jump_velocity = 0
        self.gravity = 1
        self.jump_height = 15
        self.w_pressed = False

        self.speed = 0
        self.acceleration = 0.1

    def update(self):
        keys = pygame.key.get_pressed()

        if keys[pygame.K_RIGHT] or keys[pygame.K_d]:
             self.speed += self.acceleration             

        if keys[pygame.K_LEFT] or keys[pygame.K_a]:
            self.speed -= self.acceleration

        else:
            self.speed *= 0.95
            
        self.rect.x += self.speed


        if not self.is_jumping and keys[pygame.K_w]:
             self.is_jumping = True
             self.jump_velocity = -self.jump_height

        if self.is_jumping:
             self.rect.y += self.jump_velocity
             self.jump_velocity += self.gravity
  

             if self.rect.y >= self.height - 100:
                 self.rect.y = self.height - 100
                 self.is_jumping = False
                 self.jump_velocity = 0

        if self.rect.x < 0:
             self.rect.x = 0
             self.speed = 0 

    