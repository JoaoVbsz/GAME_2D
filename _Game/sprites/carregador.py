import pygame


def carregar_imagem(caminho) -> pygame.Surface:
    if pygame.image.get_extended():
        return pygame.image.load(str(caminho))
    from PIL import Image
    img = Image.open(caminho).convert("RGBA")
    return pygame.image.frombytes(img.tobytes(), img.size, "RGBA")
