package br.com.celke.cursos.shared.lgpd;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Linhas de uma consulta prontas para a exportação: datas como instantes ISO-8601. */
public final class Linhas {

    private Linhas() {}

    public static List<Map<String, Object>> de(List<Map<String, Object>> linhas) {
        return linhas.stream().map(Linhas::linha).toList();
    }

    private static Map<String, Object> linha(Map<String, Object> original) {
        Map<String, Object> linha = new LinkedHashMap<>();
        original.forEach((coluna, valor) -> linha.put(coluna, valor(valor)));
        return linha;
    }

    private static Object valor(Object valor) {
        if (valor instanceof Timestamp t) {
            return t.toInstant().toString();
        }
        if (valor instanceof OffsetDateTime o) {
            return o.toInstant().toString();
        }
        return valor;
    }
}
