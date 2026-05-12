import json


def empacotar(dados: dict) -> bytes:
    return (json.dumps(dados) + "\n").encode("utf-8")


def desempacotar(linha: bytes) -> dict:
    return json.loads(linha.decode("utf-8").strip())
