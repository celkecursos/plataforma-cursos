package br.com.celke.cursos.support.fixtures;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Imagens geradas em memória para os testes (metade azul, para conferir o conteúdo depois). */
public final class ImagemFixture {

    private ImagemFixture() {}

    public static byte[] png(int largura, int altura) {
        return imagem("png", largura, altura, BufferedImage.TYPE_INT_RGB);
    }

    /** PNG com canal alfa (fundo transparente). */
    public static byte[] pngTransparente(int largura, int altura) {
        return imagem("png", largura, altura, BufferedImage.TYPE_INT_ARGB);
    }

    public static byte[] jpeg(int largura, int altura) {
        return imagem("jpeg", largura, altura, BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] imagem(String formato, int largura, int altura, int tipo) {
        BufferedImage imagem = new BufferedImage(largura, altura, tipo);
        var g = imagem.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, Math.max(1, largura / 2), altura);
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagem, formato, saida);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }

    /** JPEG com um segmento APP1 (Exif) contendo {@code conteudo}, logo depois do marcador SOI. */
    public static byte[] jpegComExif(int largura, int altura, String conteudo) {
        byte[] jpeg = jpeg(largura, altura);
        byte[] dados = ("Exif\0\0" + conteudo).getBytes(StandardCharsets.ISO_8859_1);
        int tamanho = dados.length + 2;
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpeg, 0, 2);
        saida.write(0xFF);
        saida.write(0xE1);
        saida.write(tamanho >> 8);
        saida.write(tamanho & 0xFF);
        saida.write(dados, 0, dados.length);
        saida.write(jpeg, 2, jpeg.length - 2);
        return saida.toByteArray();
    }
}
