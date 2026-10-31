package br.com.celke.cursos.curso.application;

import br.com.celke.cursos.curso.domain.Curso;
import br.com.celke.cursos.curso.domain.Modulo;
import br.com.celke.cursos.curso.domain.StatusCurso;
import br.com.celke.cursos.curso.domain.StatusTurma;
import br.com.celke.cursos.curso.domain.Turma;
import br.com.celke.cursos.curso.infra.CursoRepository;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cursos de exemplo publicados (turma ativa, regra do certificado, módulos, aulas com a primeira
 * gratuita e capa em degradê), criados ao subir a API com {@code CURSOS_EXEMPLO=true}: só na
 * homologação, para ver o site e a área do aluno com conteúdo. Curso que já existe (mesmo slug) é
 * pulado. Os mesmos cursos do {@code web/scripts/cursos-exemplo.ts}, que os cria por um ambiente
 * qualquer pela API do ADM.
 */
@Component
@ConditionalOnProperty(name = "app.exemplos.cursos", havingValue = "true")
@Order(10)
public class CursosExemplo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CursosExemplo.class);

    public record ModuloExemplo(String titulo, List<String> aulas) {}

    public record Exemplo(
            String slug,
            String titulo,
            String resumo,
            String descricao,
            int corInicial,
            int corFinal,
            int cargaHoras,
            List<ModuloExemplo> modulos) {}

    static final List<Exemplo> EXEMPLOS = List.of(
            new Exemplo(
                    "java-do-zero-ao-avancado",
                    "Java do zero ao avançado",
                    "Aprenda a programar em Java, da lógica à orientação a objetos, com projetos práticos.",
                    "Um curso completo para quem quer começar a programar com uma das linguagens mais usadas no"
                            + " mercado.\n\nVocê sai do primeiro programa até coleções, tratamento de erros e testes,"
                            + " sempre com exercícios para fixar.",
                    0x1f5fe0,
                    0x6a3df0,
                    40,
                    List.of(
                            new ModuloExemplo(
                                    "Primeiros passos",
                                    List.of("Instalando o JDK e a IDE", "Seu primeiro programa", "Variáveis e tipos")),
                            new ModuloExemplo(
                                    "Lógica de programação",
                                    List.of("Condicionais", "Laços de repetição", "Métodos", "Exercícios comentados")),
                            new ModuloExemplo(
                                    "Orientação a objetos",
                                    List.of(
                                            "Classes e objetos",
                                            "Herança e interfaces",
                                            "Coleções",
                                            "Projeto final")))),
            new Exemplo(
                    "react-com-typescript-na-pratica",
                    "React com TypeScript na prática",
                    "Construa interfaces modernas com componentes, hooks e tipagem do começo ao fim.",
                    "Do componente ao projeto publicado: estado, efeitos, formulários, rotas e chamadas à API, com"
                            + " TypeScript em tudo.\n\nIdeal para quem já conhece JavaScript e quer dar o próximo"
                            + " passo.",
                    0x0ea5b7,
                    0x1f5fe0,
                    30,
                    List.of(
                            new ModuloExemplo(
                                    "Fundamentos",
                                    List.of(
                                            "Criando o projeto com Vite",
                                            "Componentes e props",
                                            "Estado com useState")),
                            new ModuloExemplo(
                                    "Aplicação real",
                                    List.of("Rotas", "Formulários", "Consumindo uma API", "Testes com Vitest")),
                            new ModuloExemplo("Publicação", List.of("Build de produção", "Deploy")))),
            new Exemplo(
                    "excel-para-o-dia-a-dia",
                    "Excel para o dia a dia",
                    "Planilhas, fórmulas, gráficos e tabelas dinâmicas para ganhar tempo no trabalho.",
                    "Aprenda a organizar dados e automatizar tarefas repetitivas com as funções que mais aparecem no"
                            + " dia a dia.\n\nCada aula traz uma planilha de apoio para você praticar.",
                    0x16a34a,
                    0x0f766e,
                    16,
                    List.of(
                            new ModuloExemplo(
                                    "Básico", List.of("Conhecendo a planilha", "Formatação", "Fórmulas simples")),
                            new ModuloExemplo("Funções", List.of("SE e SOMASE", "PROCX", "Datas e textos")),
                            new ModuloExemplo(
                                    "Análise", List.of("Gráficos", "Tabelas dinâmicas", "Painel de indicadores")))),
            new Exemplo(
                    "marketing-digital-para-iniciantes",
                    "Marketing digital para iniciantes",
                    "Redes sociais, conteúdo e anúncios para atrair clientes pela internet.",
                    "Entenda como planejar a presença on-line de um negócio, produzir conteúdo que engaja e medir os"
                            + " resultados.\n\nSem jargão: exemplos reais e um plano de ação ao final.",
                    0xf7931e,
                    0xe11d48,
                    20,
                    List.of(
                            new ModuloExemplo("Planejamento", List.of("Público e persona", "Posicionamento", "Metas")),
                            new ModuloExemplo(
                                    "Conteúdo", List.of("Calendário editorial", "Instagram", "Vídeos curtos")),
                            new ModuloExemplo("Resultados", List.of("Anúncios", "Métricas", "Plano de ação")))),
            new Exemplo(
                    "fotografia-com-o-celular",
                    "Fotografia com o celular",
                    "Composição, luz e edição para fotos profissionais usando só o smartphone.",
                    "Descubra como tirar fotos melhores com o equipamento que você já tem no bolso.\n\nVocê pratica"
                            + " composição, aproveita a luz natural e aprende a editar no próprio celular.",
                    0x7c3aed,
                    0xdb2777,
                    12,
                    List.of(
                            new ModuloExemplo(
                                    "O olhar", List.of("Regra dos terços", "Linhas e enquadramento", "Perspectiva")),
                            new ModuloExemplo("Luz", List.of("Luz natural", "Contraluz", "Fotos à noite")),
                            new ModuloExemplo("Edição", List.of("Ajustes básicos", "Cor e estilo")))),
            new Exemplo(
                    "ingles-para-viagens",
                    "Inglês para viagens",
                    "Frases e situações para se virar no aeroporto, no hotel e no restaurante.",
                    "Um curso direto ao ponto para quem vai viajar e quer se comunicar com confiança.\n\nCada módulo"
                            + " simula uma situação real, com vocabulário, pronúncia e diálogos.",
                    0x0b1a36,
                    0x1f5fe0,
                    10,
                    List.of(
                            new ModuloExemplo(
                                    "No aeroporto", List.of("Check-in", "Imigração", "Perguntando o caminho")),
                            new ModuloExemplo("No hotel", List.of("Reserva", "Pedidos e problemas")),
                            new ModuloExemplo("Passeios", List.of("No restaurante", "Compras", "Emergências")))));

    private final CursoRepository cursos;
    private final CursoAdmService cursoAdmService;
    private final TurmaAdmService turmaAdmService;
    private final ModuloAdmService moduloAdmService;
    private final AulaAdmService aulaAdmService;
    private final StorageService storage;

    public CursosExemplo(
            CursoRepository cursos,
            CursoAdmService cursoAdmService,
            TurmaAdmService turmaAdmService,
            ModuloAdmService moduloAdmService,
            AulaAdmService aulaAdmService,
            StorageService storage) {
        this.cursos = cursos;
        this.cursoAdmService = cursoAdmService;
        this.turmaAdmService = turmaAdmService;
        this.moduloAdmService = moduloAdmService;
        this.aulaAdmService = aulaAdmService;
        this.storage = storage;
    }

    /** Uma falha aqui fica no log: a homologação sobe mesmo sem os exemplos. */
    @Override
    public void run(ApplicationArguments args) {
        for (Exemplo exemplo : EXEMPLOS) {
            try {
                if (criar(exemplo)) {
                    LOG.info("Curso de exemplo criado: {}", exemplo.slug());
                }
            } catch (RuntimeException e) {
                LOG.warn("Curso de exemplo {} não criado", exemplo.slug(), e);
            }
        }
    }

    /** Cria e publica o curso; devolve false se o slug já existe. */
    public boolean criar(Exemplo exemplo) {
        if (cursos.existsBySlug(exemplo.slug())) {
            return false;
        }
        Curso curso = cursoAdmService.criar(
                new DadosCurso(exemplo.titulo(), exemplo.slug(), exemplo.resumo(), exemplo.descricao()));
        Turma turma = turmaAdmService.criar(curso.getId(), "Turma de exemplo");
        turmaAdmService.definirCertificado(turma.getId(), 100, exemplo.cargaHoras());
        turmaAdmService.alterarStatus(turma.getId(), StatusTurma.ATIVA);
        boolean primeira = true;
        for (ModuloExemplo m : exemplo.modulos()) {
            Modulo modulo = moduloAdmService.criar(turma.getId(), m.titulo());
            for (String titulo : m.aulas()) {
                aulaAdmService.criar(
                        modulo.getId(),
                        new DadosAula(titulo, "Aula de exemplo do curso " + exemplo.titulo() + ".", primeira));
                primeira = false;
            }
        }
        String capa = "capas/%d/%s.jpg".formatted(curso.getId(), UUID.randomUUID());
        storage.gravar(capa, capa(exemplo.corInicial(), exemplo.corFinal()), "image/jpeg");
        cursoAdmService.trocarCapa(curso.getId(), capa);
        cursoAdmService.alterarStatus(curso.getId(), StatusCurso.PUBLICADO);
        return true;
    }

    /** JPEG de 1280 × 720 (o tamanho das capas): degradê diagonal com dois círculos claros e suaves. */
    static byte[] capa(int de, int ate) {
        int largura = CapaCursoService.LARGURA;
        int altura = CapaCursoService.ALTURA;
        double[][] circulos = {
            {largura * 0.82, altura * 0.18, altura * 0.55, 0.22},
            {largura * 0.12, altura * 0.95, altura * 0.45, 0.12}
        };
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < altura; y++) {
            for (int x = 0; x < largura; x++) {
                double t = (double) x / largura * 0.6 + (double) y / altura * 0.4;
                double luz = 0;
                for (double[] c : circulos) {
                    double d = Math.hypot(x - c[0], y - c[1]) / c[2];
                    if (d < 1) {
                        luz += (1 - d) * (1 - d) * c[3];
                    }
                }
                int rgb = 0;
                for (int deslocamento = 16; deslocamento >= 0; deslocamento -= 8) {
                    double base = ((de >> deslocamento) & 255)
                            + (((ate >> deslocamento) & 255) - ((de >> deslocamento) & 255)) * t;
                    rgb = (rgb << 8) | (int) Math.min(255, Math.round(base + (255 - base) * luz));
                }
                imagem.setRGB(x, y, rgb);
            }
        }
        var saida = new ByteArrayOutputStream();
        try {
            ImageIO.write(imagem, "jpg", saida);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return saida.toByteArray();
    }
}
