package br.com.celke.cursos.shared.auditoria;

import jakarta.persistence.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.hibernate.proxy.HibernateProxy;

/**
 * Converte o estado de uma entidade (como o Hibernate entrega no evento) em valores simples para o JSON
 * da trilha: entidade relacionada vira o id, data vira texto ISO-8601, binário vira um marcador e
 * campo {@link DadoSensivel} vira {@code "***"}.
 */
public final class ValoresAuditoria {

    public static final String OCULTO = "***";
    static final String BINARIO = "[binário]";

    private static final Map<Class<?>, Set<String>> SENSIVEIS = new ConcurrentHashMap<>();

    private ValoresAuditoria() {}

    /**
     * Campos escolhidos do estado.
     *
     * @param indices posições a incluir; nulo, todas
     */
    public static Map<String, Object> campos(Class<?> tipo, String[] nomes, Object[] estado, int[] indices) {
        if (estado == null) {
            return null;
        }
        Map<String, Object> campos = new LinkedHashMap<>();
        if (indices == null) {
            for (int i = 0; i < nomes.length; i++) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        } else {
            for (int i : indices) {
                campos.put(nomes[i], valor(tipo, nomes[i], estado[i]));
            }
        }
        return campos;
    }

    static Object valor(Class<?> tipo, String campo, Object valor) {
        if (valor == null) {
            return null;
        }
        if (sensiveis(tipo).contains(campo)) {
            return OCULTO;
        }
        return simples(valor);
    }

    static Object simples(Object valor) {
        if (valor == null || valor instanceof String || valor instanceof Number || valor instanceof Boolean) {
            return valor;
        }
        if (valor instanceof Enum<?> constante) {
            return constante.name();
        }
        if (valor instanceof TemporalAccessor || valor instanceof UUID) {
            return valor.toString();
        }
        if (valor instanceof byte[]) {
            return BINARIO;
        }
        if (valor instanceof Collection<?> colecao) {
            return colecao.stream().map(ValoresAuditoria::simples).toList();
        }
        if (valor instanceof HibernateProxy proxy) {
            return simples(proxy.getHibernateLazyInitializer().getIdentifier());
        }
        if (valor.getClass().isAnnotationPresent(Entity.class)) {
            return idDe(valor);
        }
        return valor.toString();
    }

    /** Campos {@link DadoSensivel} da classe e das superclasses (lidos uma vez por classe). */
    static Set<String> sensiveis(Class<?> tipo) {
        return SENSIVEIS.computeIfAbsent(tipo, classe -> {
            java.util.stream.Stream.Builder<Field> campos = java.util.stream.Stream.builder();
            for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field campo : c.getDeclaredFields()) {
                    campos.add(campo);
                }
            }
            return campos.build()
                    .filter(campo -> campo.isAnnotationPresent(DadoSensivel.class))
                    .map(Field::getName)
                    .collect(Collectors.toUnmodifiableSet());
        });
    }

    private static Object idDe(Object entidade) {
        try {
            Method getId = entidade.getClass().getMethod("getId");
            return simples(getId.invoke(entidade));
        } catch (ReflectiveOperationException e) {
            return entidade.getClass().getSimpleName();
        }
    }
}
