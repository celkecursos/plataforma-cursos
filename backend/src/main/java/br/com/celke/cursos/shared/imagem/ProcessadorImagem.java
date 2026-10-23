package br.com.celke.cursos.shared.imagem;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Normaliza imagens enviadas pelos usuários (capa do curso, foto de perfil): aceita só JPEG e PNG,
 * reduz para caber no limite e regrava em JPEG. A re-codificação descarta todos os metadados (EXIF
 * com GPS e dados da câmera, comentários) e qualquer conteúdo escondido no arquivo original.
 */
@Component
public class ProcessadorImagem {

    /** Formatos aceitos, pelo conteúdo real do arquivo (não pela extensão ou Content-Type). */
    private static final Set<String> FORMATOS = Set.of("jpeg", "png");

    /** Lado máximo do original: protege contra "bombas de descompressão" (arquivo pequeno, imagem gigante). */
    static final int LADO_MAXIMO_ORIGINAL = 8000;

    private static final float QUALIDADE_JPEG = 0.85f;

    /**
     * Devolve a imagem em JPEG, reduzida para caber em {@code larguraMaxima} × {@code alturaMaxima}
     * sem distorcer (imagem menor que o limite não é ampliada).
     */
    public byte[] normalizar(byte[] original, int larguraMaxima, int alturaMaxima) {
        BufferedImage imagem = ler(original);
        double escala = Math.min(
                1.0, Math.min((double) larguraMaxima / imagem.getWidth(), (double) alturaMaxima / imagem.getHeight()));
        int largura = Math.max(1, (int) Math.round(imagem.getWidth() * escala));
        int altura = Math.max(1, (int) Math.round(imagem.getHeight() * escala));
        return gravarJpeg(redesenhar(imagem, largura, altura));
    }

    private static BufferedImage ler(byte[] original) {
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
            }
            ImageReader leitor = leitores.next();
            try {
                if (!FORMATOS.contains(leitor.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new ImagemInvalidaException("Envie uma imagem JPEG ou PNG.");
                }
                leitor.setInput(entrada, true, true);
                // As dimensões vêm do cabeçalho: a imagem gigante é recusada antes de ocupar a memória
                if (leitor.getWidth(0) > LADO_MAXIMO_ORIGINAL || leitor.getHeight(0) > LADO_MAXIMO_ORIGINAL) {
                    throw new ImagemInvalidaException("A imagem pode ter no máximo %d × %d pixels."
                            .formatted(LADO_MAXIMO_ORIGINAL, LADO_MAXIMO_ORIGINAL));
                }
                return leitor.read(0);
            } finally {
                leitor.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImagemInvalidaException invalida) {
                throw invalida;
            }
            throw new ImagemInvalidaException("Não foi possível ler a imagem. Envie um JPEG ou PNG válido.");
        }
    }

    /** Copia para RGB no tamanho final; a transparência do PNG vira fundo branco. */
    private static BufferedImage redesenhar(BufferedImage origem, int largura, int altura) {
        BufferedImage destino = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, largura, altura);
            g.drawImage(origem, 0, 0, largura, altura, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    /** Grava só os pixels: sem metadados de entrada, o JPEG sai sem EXIF. */
    private static byte[] gravarJpeg(BufferedImage imagem) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream destino = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(QUALIDADE_JPEG);
            escritor.setOutput(destino);
            escritor.write(null, new IIOImage(imagem, null, null), parametros);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            escritor.dispose();
        }
        return saida.toByteArray();
    }
}
