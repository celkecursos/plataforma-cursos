package br.com.celke.cursos.pagamento.domain;

import java.util.Map;

/** Como o aluno paga: indo para a página do meio de pagamento ou num formulário embutido. */
public sealed interface Checkout {

    record Redirecionamento(String url) implements Checkout {}

    /** Dados que o front entrega ao formulário/SDK do meio de pagamento. */
    record Embutido(Map<String, Object> dados) implements Checkout {

        public Embutido {
            dados = Map.copyOf(dados);
        }
    }
}
