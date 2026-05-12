"""
Servidor do jogo Time Out — Multiplayer em Rede
Rodar: python servidor.py
Aguarda 2 jogadores conectarem na porta 5555.
"""
from rede.servidor import Servidor

if __name__ == "__main__":
    Servidor().iniciar()
