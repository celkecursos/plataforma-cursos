package br.com.celke.cursos.curso.domain;

import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

/** Regras de posição dos itens dentro do pai: sempre 1, 2, 3... sem buracos nem repetição. */
public final class Ordenacao {

    private Ordenacao() {}

    /**
     * Reordena os itens conforme a lista de ids, que precisa ter exatamente os itens atuais, sem
     * faltar, sobrar ou repetir.
     */
    public static void reordenar(List<? extends Ordenavel> atuais, List<Long> idsNaNovaOrdem) {
        Map<Long, Ordenavel> porId = atuais.stream().collect(Collectors.toMap(Ordenavel::getId, Function.identity()));
        boolean mesmoConjunto = idsNaNovaOrdem.size() == atuais.size()
                && new HashSet<>(idsNaNovaOrdem).size() == idsNaNovaOrdem.size()
                && porId.keySet().containsAll(idsNaNovaOrdem);
        if (!mesmoConjunto) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "A nova ordem deve ter todos os itens atuais, sem repetir nenhum.");
        }
        for (int i = 0; i < idsNaNovaOrdem.size(); i++) {
            porId.get(idsNaNovaOrdem.get(i)).definirOrdem(i + 1);
        }
    }

    /** Refaz a sequência 1, 2, 3... mantendo a ordem atual (depois de uma exclusão). */
    public static void compactar(List<? extends Ordenavel> restantes) {
        List<? extends Ordenavel> ordenados = restantes.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdem(), b.getOrdem()))
                .toList();
        for (int i = 0; i < ordenados.size(); i++) {
            ordenados.get(i).definirOrdem(i + 1);
        }
    }

    /** Próxima posição: depois da maior existente. */
    public static int proxima(List<? extends Ordenavel> atuais) {
        return atuais.stream().mapToInt(Ordenavel::getOrdem).max().orElse(0) + 1;
    }
}
