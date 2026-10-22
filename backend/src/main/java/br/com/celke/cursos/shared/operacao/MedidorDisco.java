package br.com.celke.cursos.shared.operacao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Espaço do sistema de arquivos de um caminho (separado para os alertas serem testáveis sem disco cheio). */
@Component
public class MedidorDisco {

    public Espaco medir(Path caminho) {
        try {
            FileStore loja = Files.getFileStore(caminho);
            return new Espaco(loja.getUsableSpace(), loja.getTotalSpace());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Espaco(long livre, long total) {

        /** Percentual livre, arredondado para baixo; disco de tamanho zero conta como cheio. */
        public int percentualLivre() {
            return total <= 0 ? 0 : (int) (livre * 100 / total);
        }
    }
}
