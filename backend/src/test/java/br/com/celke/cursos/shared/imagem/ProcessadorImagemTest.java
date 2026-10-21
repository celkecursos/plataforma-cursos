package br.com.celke.cursos.shared.imagem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.celke.cursos.support.fixtures.ImagemFixture;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProcessadorImagemTest {

    private final ProcessadorImagem processador = new ProcessadorImagem();

    private static BufferedImage ler(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    private static boolean ehJpeg(byte[] conteudo) {
        return (conteudo[0] & 0xFF) == 0xFF && (conteudo[1] & 0xFF) == 0xD8;
    }

    @ParameterizedTest(name = "{0}×{1} → {2}×{3}")
    @CsvSource({
        "4000, 1000, 1280, 320", // limitada pela largura
        "1000, 2000, 360,  720", // limitada pela altura
        "1280, 720,  1280, 720", // exatamente no limite
        "300,  200,  300,  200", // menor que o limite: não amplia
    })
    void reduzSemDistorcer(int largura, int altura, int larguraFinal, int alturaFinal) throws IOException {
        byte[] resultado = processador.normalizar(ImagemFixture.png(largura, altura), 1280, 720);

        BufferedImage final_ = ler(resultado);
        assertThat(ehJpeg(resultado)).isTrue();
        assertThat(final_.getWidth()).isEqualTo(larguraFinal);
        assertThat(final_.getHeight()).isEqualTo(alturaFinal);
    }

    @Test
    @DisplayName("PNG transparente vira JPEG com fundo branco")
    void transparenciaViraBranco() throws IOException {
        byte[] resultado = processador.normalizar(ImagemFixture.pngTransparente(100, 100), 1280, 720);

        Color canto = new Color(ler(resultado).getRGB(99, 50));
        assertThat(canto.getRed()).isGreaterThan(240);
        assertThat(canto.getGreen()).isGreaterThan(240);
        assertThat(canto.getBlue()).isGreaterThan(240);
    }

    @Test
    @DisplayName("descarta os metadados EXIF do JPEG original")
    void descartaExif() throws IOException {
        byte[] original = ImagemFixture.jpegComExif(200, 100, "GPS -23.5505 -46.6333");
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("Exif", "GPS -23.5505");

        byte[] resultado = processador.normalizar(original, 1280, 720);

        String bytes = new String(resultado, StandardCharsets.ISO_8859_1);
        assertThat(bytes).doesNotContain("Exif").doesNotContain("GPS -23.5505");
        assertThat(ler(resultado).getWidth()).isEqualTo(200);
    }

    @Test
    @DisplayName("lado de 8000 pixels é aceito; 8001 é recusado antes de decodificar")
    void limiteDeDimensoes() throws IOException {
        assertThat(processador.normalizar(
                        ImagemFixture.imagem("png", 8000, 1, BufferedImage.TYPE_BYTE_GRAY), 1280, 720))
                .isNotEmpty();
        byte[] grande = ImagemFixture.imagem("png", 1, 8001, BufferedImage.TYPE_BYTE_GRAY);

        assertThatThrownBy(() -> processador.normalizar(grande, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("A imagem pode ter no máximo 8000 × 8000 pixels.");
    }

    @Test
    @DisplayName("recusa formato que não é JPEG nem PNG, mesmo sendo imagem")
    void recusaGif() throws IOException {
        byte[] gif = ImagemFixture.imagem("gif", 10, 10, BufferedImage.TYPE_INT_RGB);

        assertThatThrownBy(() -> processador.normalizar(gif, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Envie uma imagem JPEG ou PNG.");
    }

    @Test
    @DisplayName("recusa conteúdo que não é imagem (ex.: HTML com extensão de imagem)")
    void recusaNaoImagem() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> processador.normalizar(html, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Envie uma imagem JPEG ou PNG.");
    }

    @Test
    @DisplayName("recusa PNG truncado")
    void recusaTruncado() throws IOException {
        byte[] png = ImagemFixture.png(200, 200);
        byte[] truncado = Arrays.copyOf(png, 40);

        assertThatThrownBy(() -> processador.normalizar(truncado, 1280, 720))
                .isInstanceOf(ImagemInvalidaException.class)
                .hasMessage("Não foi possível ler a imagem. Envie um JPEG ou PNG válido.");
    }
}
