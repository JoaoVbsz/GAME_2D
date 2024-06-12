
import pygame
import sys
from background import Background
from player import Player
from magic import Magic
from power import Power
import random

pygame.init()

#Nome e dimensão do display
pygame.display.set_caption("Time out")
width = 736
height = 414
display = pygame.display.set_mode((width, height))

#Grupos
object_grup = pygame.sprite.Group()
magic_grup = pygame.sprite.Group()
power_grup = pygame.sprite.Group()

#Definição das class
background = Background(width, height, object_grup)
player = Player(height, object_grup)
magic = Magic(height, object_grup, magic_grup)
power = Power(object_grup, magic_grup)

#musica do jogo
music = pygame.mixer.music.load("som_game.wav")
music = pygame.mixer.music.play(-1)
#som de ataque
atack = pygame.mixer.Sound("som_ataque.wav")


gameLoop = True

#clock do fps
clock = pygame.time.Clock() 
timer = 15
gameover = False

mute_sound = False
running = True


if __name__ == "__main__":
    
    while gameLoop:
        clock.tick(60) #fps
        
        # saida da janela
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                gameLoop = False
                running = False

            #Evento de acionar os botões
            elif event.type == pygame.KEYDOWN:

                #som e reprodução do ataque, ataque "espaço"
                if event.key == pygame.K_SPACE and not gameover:
                    atack.play()
                    newpower = Power(object_grup, power_grup)
                    newpower.rect.center = player.rect.center
                    
                #MUltar som do jogo, tecla "U"
                elif event.key == pygame.K_u:
                    mute_sound = not mute_sound
                    if mute_sound:
                        pygame.mixer.music.set_volume(0)
                    else:
                        pygame.mixer.music.set_volume(1)

        #se nada ocorre contra o player; continua.
        #se algo ocorrer contra o player; jogo trava.
        if not gameover:
            object_grup.update()

            #tempo de spaw e probabilidade de spaw do inimigo
            timer += 1
            if timer > 30:
                timer = 0
                if random.random() < 0.3: 
                    newmagic = Magic(height, object_grup, magic_grup)

            #colisão do player com o mago inimigo
            collision = pygame.sprite.spritecollide (player, magic_grup, False, pygame.sprite.collide_mask)
            if collision:
                print("game over")
                gameover = True

            #Colisão do poder do player com o mago inimigo
            power_collision = pygame.sprite.groupcollide(
                power_grup, magic_grup, True, True, pygame.sprite.collide_mask)

        object_grup.draw(display)
        pygame.display.update()
        

pygame.quit()
sys.exit()