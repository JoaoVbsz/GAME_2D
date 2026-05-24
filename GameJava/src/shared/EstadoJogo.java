package shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class EstadoJogo implements Serializable {
    private static final long serialVersionUID = 1L;

    public int j1x, j1y, j1tiros;
    public boolean j1recarregando;
    public int j2x, j2y, j2tiros;
    public boolean j2recarregando;
    public List<int[]> inimigos = new ArrayList<>();
    public List<int[]> voadores = new ArrayList<>();
    public List<int[]> projJ1 = new ArrayList<>();
    public List<int[]> projJ2 = new ArrayList<>();
    public int[] pontos = {0, 0};
    public boolean fimJogo;
}
