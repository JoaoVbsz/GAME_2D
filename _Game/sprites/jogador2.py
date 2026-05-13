import pygame
from sprites.carregador import carregar_imagem
from configuracoes import DIR_IMAGENS, LARGURA, ALTURA

class Jogador2(pygame.sprite.Sprite):
    def __init__(self, *grupos):
        super().__init__(*grupos)
        self.image = carregar_imagem(DIR_IMAGENS / "mago2_novo.png").convert_alpha()
        self.image = pygame.transform.scale(self.image, [100, 100])
        self.mask = pygame.mask.from_surface(self.image)
        
        self.rect = pygame.Rect(10, ALTURA // 2, 100, 100)
        self.velocidade = 4
        
        self.tiros = 5
        self.recarregando = False
        self.tempo_inicio_recarga = 0

    def update(self):
        agora = pygame.time.get_ticks()
        if self.recarregando:
            if agora - self.tempo_inicio_recarga >= 2000:  # 2 segundos
                self.recarregando = False
                self.tiros = 5

        teclas = pygame.key.get_pressed()
        if teclas[pygame.K_a]:
            self.rect.x -= self.velocidade
        if teclas[pygame.K_d]:
            self.rect.x += self.velocidade
        if teclas[pygame.K_w]:
            self.rect.y -= self.velocidade
        if teclas[pygame.K_s]:
            self.rect.y += self.velocidade

        self.rect.clamp_ip(pygame.Rect(0, 0, LARGURA, ALTURA))
