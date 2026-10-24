# Área do aluno

**Fase:** 13
**Branch:** `feature/web-aluno`

> **Configuração do projeto:** troque os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`,
> `<sub-adm>`, `<sub-media>` e `<dono-github>` pelos valores de `docs/configuracao.md` (criado no
> primeiro prompt) em todo o conteúdo deste prompt, inclusive nos testes. Se o arquivo não existir,
> pergunte esses valores ao usuário antes de começar.

## Prompt

### Especificação e tasks (SDD)

Antes do código, registre a funcionalidade no SDD: crie a spec e as tasks abaixo com **exatamente** este conteúdo (troque os marcadores pelos valores de `docs/configuracao.md`). As tasks nascem com `Status: Pronta`; ao fim da **Verificação**, passam para `Concluída`.

`.sdd/specs/area-do-aluno.spec.md`:

`````markdown
---
feature: area-do-aluno
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Área do aluno

## 1. Resumo Executivo

O aluno estuda pelas telas de `<sub-aluno>.<dominio>`: vê os cursos, abre a turma, assiste às aulas
retomando de onde parou, baixa arquivos, pergunta no fórum, emite o certificado e cuida do perfil.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno | Todas as telas da área do aluno, dentro do acesso de cada matrícula |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE mostrar os cursos em abas (ativos, arquivados, expirados) e permitir
  arquivar e desarquivar.
- **RF-02** — O sistema DEVE mostrar a turma com módulos, aulas, arquivos do módulo e o progresso, com as
  marcações de vista e revisar.
- **RF-03** — O sistema DEVE reproduzir o vídeo da aula por streaming adaptativo, retomando da última
  posição e enviando a posição periodicamente.
- **RF-04** — O sistema DEVE navegar para a aula anterior e a próxima e marcar a aula como vista ou para
  revisar.
- **RF-05** — O sistema DEVE mostrar os arquivos e o fórum da aula, com pergunta e resposta.
- **RF-06** — O sistema DEVE mostrar a situação do certificado, emitir com o nome completo, baixar o PDF
  e listar os certificados.
- **RF-07** — O sistema DEVE permitir trocar nome, foto e senha no perfil.
- **RF-08** — O sistema DEVE mostrar estados de carregando, vazio e erro, e bloquear com mensagem clara
  o conteúdo sem acesso.

## 4. Modelo de Domínio

Usa as entidades das specs `matricula-e-acesso`, `reproducao-e-progresso`, `arquivos-de-apoio`,
`forum`, `certificado` e `perfil-e-termos`.

## 5. Estados e Fluxos

- **Estudo:** entrar → meus cursos → turma → aula → vídeo até o fim → vista → progresso da turma →
  certificado.

## 6. Contratos de Interface

| Tela | Faz |
|---|---|
| Meus cursos | abas e arquivar |
| Turma | módulos, aulas, arquivos, progresso |
| Aula | vídeo, navegação, vista/revisar, arquivos, fórum |
| Certificados | situação, emissão, PDF, lista |
| Perfil | nome, foto, senha |

## 7. Casos de Borda

- Matrícula futura: curso visível, aulas bloqueadas até o início.
- Sem matrícula: só aulas gratuitas.
- Vídeo ainda em processamento: aviso no lugar do player.

## 8. Premissas Validadas

- A credencial de acesso é enviada só às listas de reprodução da API, nunca ao armazenamento.

## 9. Riscos

- **Posição perdida ao fechar a aba:** envio periódico e ao sair da página.
`````

`.sdd/tasks/area-do-aluno-v1.0/README.md`:

`````markdown
# Tasks — Área do aluno (v1.0)

Spec: [`.sdd/specs/area-do-aluno.spec.md`](../../specs/area-do-aluno.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f13-01-area-do-aluno.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ALUNO-001](ALUNO-001-cursos-e-turma.md) | Meus cursos e turma | Pleno | — |
| 2 | [ALUNO-002](ALUNO-002-aula-e-player.md) | Aula e player | Sênior | — |
| 3 | [ALUNO-003](ALUNO-003-certificado-e-perfil.md) | Certificados e perfil | Pleno | — |
| 4 | [ALUNO-004](ALUNO-004-fiacao.md) | Fiação da área do aluno | Pleno | ALUNO-001, ALUNO-002, ALUNO-003 |

## Grafo

```
ALUNO-001 (início)
ALUNO-002 (início)
ALUNO-003 (início)
ALUNO-001 + ALUNO-002 + ALUNO-003 → ALUNO-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ALUNO-001 |
| RF-02 | ALUNO-001 |
| RF-03 | ALUNO-002 |
| RF-04 | ALUNO-002 |
| RF-05 | ALUNO-002 |
| RF-06 | ALUNO-003 |
| RF-07 | ALUNO-003 |
| RF-08 | ALUNO-001, ALUNO-004 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
`````

`.sdd/tasks/area-do-aluno-v1.0/ALUNO-001-cursos-e-turma.md`:

`````markdown
# ALUNO-001 — Meus cursos e turma

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-01, RF-02, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/aluno/src/paginas/MeusCursosPagina.tsx`
- `web/aluno/src/paginas/turma/TurmaPagina.tsx`
- `web/aluno/src/paginas/turma/ConteudoTurma.tsx`
- `web/aluno/src/paginas/turma/ArquivosModulo.tsx`

## Critérios de aceite

- [ ] `web/e2e/aluno/estudo.spec.ts` verde
`````

`.sdd/tasks/area-do-aluno-v1.0/ALUNO-002-aula-e-player.md`:

`````markdown
# ALUNO-002 — Aula e player

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-03, RF-04, RF-05
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/aluno/src/paginas/aula/AulaPagina.tsx`
- `web/aluno/src/paginas/aula/PlayerVideo.tsx` — hls.js
- `web/aluno/src/paginas/aula/ListaArquivos.tsx`
- `web/aluno/src/paginas/forum/ForumLista.tsx`
- `web/aluno/src/paginas/forum/TopicoPagina.tsx`
- `web/aluno/src/paginas/turma/ForumTurma.tsx`

## Descrição técnica

Token só nas playlists da API; retomada e envio periódico da posição.

## Restrições

Nunca enviar o token ao armazenamento.

## Critérios de aceite

- [ ] `web/e2e/aluno/estudo.spec.ts` (vídeo até o fim) verde

## Armadilhas

Enviar o `Authorization` aos trechos assinados quebra a assinatura.
`````

`.sdd/tasks/area-do-aluno-v1.0/ALUNO-003-certificado-e-perfil.md`:

`````markdown
# ALUNO-003 — Certificados e perfil

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-06, RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/aluno/src/paginas/CertificadosPagina.tsx`
- `web/aluno/src/paginas/turma/CertificadoAba.tsx`
- `web/aluno/src/paginas/perfil/PerfilPagina.tsx`

## Critérios de aceite

- [ ] `web/e2e/aluno/perfil.spec.ts` verde
`````

`.sdd/tasks/area-do-aluno-v1.0/ALUNO-004-fiacao.md`:

`````markdown
# ALUNO-004 — Fiação da área do aluno

- **Spec:** .sdd/specs/area-do-aluno.spec.md (v1.0) · atende RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** ALUNO-001, ALUNO-002, ALUNO-003

## Arquivos a criar/alterar

- `web/aluno/src/rotas.tsx`

## Descrição técnica

Rotas `turmas/:turmaId`, aulas, fórum, `certificados` e `perfil`.

## Critérios de aceite

- [ ] `npm test` e Playwright verdes
- [ ] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
`````

Acrescente ao fim da tabela do índice `.sdd/README.md` a linha:

```text
| [area-do-aluno](specs/area-do-aluno.spec.md) | 1.0 | [v1.0](tasks/area-do-aluno-v1.0/README.md) | `f13-01-area-do-aluno.md` |
```

Vamos construir a experiência do aluno. Não faça commit.

O que esta etapa entrega:

- **Meus cursos** em abas (ativos, arquivados, expirados), com arquivar e desarquivar.
- **Turma** com módulos, aulas, arquivos do módulo e o progresso (vistas e revisar).
- **Aula**: player HLS (hls.js, com o token só nas playlists da API), retomada e envio da posição,
  anterior/próxima, vista/revisar, arquivos e o fórum da aula (pergunta e resposta).
- **Certificado** (situação, emissão com o nome completo, PDF), lista de certificados e **perfil**
  (nome, foto, senha). `EscolherArquivo` e `useAcao` passam para `compartilhado`.
- **Testes** Playwright: login → turma → aula → vídeo até o fim → vista → progresso; retomada, revisar,
  navegação, download do arquivo, fórum, certificado (bloqueado → emitido → validado), perfil; sem
  matrícula, matrícula futura, arquivar.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos removidos (ou movidos para outro caminho)

- `web/adm/src/componentes/EscolherArquivo.tsx`
- `web/adm/src/componentes/useAcao.ts`

### Arquivos alterados (substitua o conteúdo inteiro)

`web/adm/src/componentes/ArquivosApoio.tsx`:

`````tsx
import {
  AcaoLinha,
  Alerta,
  CampoTexto,
  Lista,
  ItemLista,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { bytes } from '@compartilhado/ui/formato.ts';
import type { ArquivoApoio, EnvioSimples } from '../tipos.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';

/** Formatos aceitos pela API (FormatoArquivo). */
const ACEITOS = 'application/pdf,application/zip,text/plain,.pdf,.zip,.txt';
const TIPOS = ['application/pdf', 'application/zip', 'application/x-zip-compressed', 'text/plain'];

interface Props {
  dono: 'aulas' | 'modulos';
  donoId: number;
  titulo: string;
}

/** Arquivos de apoio de uma aula ou de um módulo: lista, envio direto ao armazenamento e exclusão. */
export function ArquivosApoio({ dono, donoId, titulo }: Props) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [nome, setNome] = useState('');
  const rota = `/api/adm/${dono}/${donoId}/arquivos`;
  const arquivos = useCarregar(() => api.get<ArquivoApoio[]>(rota), [rota]);

  const enviar = async (arquivo: File) => {
    // O Windows manda zip como x-zip-compressed; a API só conhece application/zip
    const tipo = arquivo.type === 'application/x-zip-compressed' ? 'application/zip' : arquivo.type;
    if (!TIPOS.includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie PDF, ZIP ou TXT.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>(`${rota}/envio`, {
        contentType: tipo,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.post<ArquivoApoio>(rota, {
        chave: envio.chave,
        titulo: nome.trim() || arquivo.name.replace(/\.[^.]+$/, ''),
        nomeArquivo: arquivo.name,
      });
    }, 'Arquivo enviado');
    if (novo) {
      setNome('');
      arquivos.recarregar();
    }
  };

  return (
    <section className="pc-arquivos" aria-label={titulo}>
      <h3 className="pc-subtitulo">{titulo}</h3>
      {arquivos.erro && <Alerta severidade="err" mensagem={arquivos.erro} />}
      {!!arquivos.dados?.length && (
        <Lista rotulo={titulo}>
          {arquivos.dados.map((a) => (
            <ItemLista
              key={a.id}
              icone="paperclip"
              titulo={a.titulo}
              fim={
                <AcaoLinha
                  icone="trash-2"
                  severidade="err"
                  rotulo={`Excluir ${a.titulo}`}
                  onClick={async () => {
                    if (!(await confirmar.remover({ mensagem: `"${a.titulo}" será removido.` })))
                      return;
                    const ok = await executar(
                      () => api.delete(`/api/adm/arquivos/${a.id}`).then(() => true),
                      'Arquivo removido',
                    );
                    if (ok) arquivos.recarregar();
                  }}
                />
              }
            >
              {a.nomeArquivo} · {bytes(a.tamanho)}
            </ItemLista>
          ))}
        </Lista>
      )}
      {arquivos.dados?.length === 0 && <p className="pc-dica">Nenhum arquivo.</p>}
      <div className="pc-linha">
        <CampoTexto
          rotulo="Título do arquivo"
          valor={nome}
          aoMudar={setNome}
          maxLength={150}
          dica="Em branco, usa o nome do arquivo."
        />
        <EscolherArquivo
          rotulo="Enviar arquivo"
          icone="upload"
          aparencia="contorno"
          aceitar={ACEITOS}
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
      </div>
    </section>
  );
}
`````

`web/adm/src/paginas/aula/AulaPagina.tsx`:

`````tsx
import {
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  CampoMarcacao,
  CampoTextarea,
  CampoTexto,
  Carregando,
  Status,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import { caminhoTurma } from '../../caminhos.ts';
import { ArquivosApoio } from '../../componentes/ArquivosApoio.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_CONTEUDO, type Aula } from '../../tipos.ts';
import { VideoAula } from './VideoAula.tsx';

/** Uma aula: dados, vídeo e arquivos de apoio. A API não tem GET da aula: vem da lista do módulo. */
export function AulaPagina() {
  const { cursoId, turmaId, moduloId, aulaId } = useParams();
  const navigate = useNavigate();
  const aula = useCarregar(async () => {
    const aulas = await api.get<Aula[]>(`/api/adm/modulos/${moduloId}/aulas`);
    const encontrada = aulas.find((a) => String(a.id) === aulaId);
    if (!encontrada) throw new ErroApi(404, { detail: 'Aula não encontrada neste módulo.' });
    return encontrada;
  }, [moduloId, aulaId]);
  const voltar = () => navigate(`${caminhoTurma(cursoId!, turmaId!)}?aba=conteudo`);

  if (aula.erro && !aula.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a aula" mensagem={aula.erro} />
      </div>
    );
  }
  if (!aula.dados) return <Carregando mensagem="Carregando a aula..." />;
  const a = aula.dados;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={a.titulo}
        icone="circle-play"
        acoes={
          <>
            <Status
              rotulo={STATUS_CONTEUDO[a.status].rotulo}
              severidade={STATUS_CONTEUDO[a.status].severidade}
            />
            <Botao
              rotulo="Voltar à turma"
              icone="chevron-left"
              aparencia="texto"
              onClick={voltar}
            />
          </>
        }
      />
      <div className="pc-pilha">
        <DadosAula key={a.id} aula={a} aoMudar={aula.definir} aoExcluir={voltar} />
        <VideoAula aulaId={a.id} />
        <Card titulo="Arquivos de apoio">
          <ArquivosApoio dono="aulas" donoId={a.id} titulo="Arquivos de apoio da aula" />
        </Card>
      </div>
    </div>
  );
}

function DadosAula({
  aula,
  aoMudar,
  aoExcluir,
}: {
  aula: Aula;
  aoMudar: (a: Aula) => void;
  aoExcluir: () => void;
}) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [titulo, setTitulo] = useState(aula.titulo);
  const [descricao, setDescricao] = useState(aula.descricao ?? '');
  const [gratuita, setGratuita] = useState(aula.gratuita);
  const [erros, setErros] = useState<{ titulo?: string; descricao?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);
  const rota = `/api/adm/aulas/${aula.id}`;

  const salvar = async () => {
    const problema = erroObrigatorio(titulo, 'Informe o título.', 150);
    setErros({ titulo: problema });
    setErroGeral(undefined);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(
        await api.put<Aula>(rota, {
          titulo: titulo.trim(),
          descricao: descricao.trim() || null,
          gratuita,
        }),
      );
      toast.ok('Aula salva');
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ titulo: erro.erroDoCampo('titulo'), descricao: erro.erroDoCampo('descricao') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card
      titulo="Dados da aula"
      ferramentas={
        <div className="pc-linha">
          <Botao
            rotulo={aula.status === 'ATIVO' ? 'Inativar aula' : 'Ativar aula'}
            icone={aula.status === 'ATIVO' ? 'eye-off' : 'eye'}
            aparencia="contorno"
            tamanho="pequeno"
            disabled={executando}
            onClick={async () => {
              const nova = await executar(
                () =>
                  api.patch<Aula>(`${rota}/status`, {
                    status: aula.status === 'ATIVO' ? 'INATIVO' : 'ATIVO',
                  }),
                aula.status === 'ATIVO' ? 'Aula inativada' : 'Aula ativada',
              );
              if (nova) aoMudar(nova);
            }}
          />
          <Botao
            rotulo="Excluir aula"
            icone="trash-2"
            aparencia="texto"
            severidade="err"
            tamanho="pequeno"
            disabled={executando}
            onClick={async () => {
              if (
                !(await confirmar.remover({ mensagem: `A aula "${aula.titulo}" será excluída.` }))
              )
                return;
              const ok = await executar(() => api.delete(rota).then(() => true), 'Aula excluída');
              if (ok) aoExcluir();
            }}
          />
        </div>
      }
    >
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={150}
        />
        <CampoTextarea
          rotulo="Descrição"
          valor={descricao}
          aoMudar={setDescricao}
          erro={erros.descricao}
          rows={5}
          maxLength={20_000}
        />
        <CampoMarcacao
          rotulo="Aula gratuita (aparece aberta no catálogo, sem matrícula)"
          valor={gratuita}
          aoMudar={setGratuita}
        />
        <div>
          <Botao type="submit" rotulo="Salvar aula" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/adm/src/paginas/aula/VideoAula.tsx`:

`````tsx
import {
  Alerta,
  Botao,
  Card,
  Detalhes,
  Imagem,
  Progresso,
  Status,
  useConfirmacao,
} from '@celke/kole-react';
import { useEffect, useRef, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarEmPartes } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { bytes, dataHora, duracao } from '@compartilhado/ui/formato.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_VIDEO, type EnvioVideo, type Video } from '../../tipos.ts';

/** Formatos aceitos pela API (FormatoVideo). */
const TIPOS = ['video/mp4', 'video/quicktime', 'video/x-matroska', 'video/webm'];
const ACEITOS = `${TIPOS.join(',')},.mp4,.mov,.mkv,.webm`;
const INTERVALO_CONSULTA = 3000;

/** O navegador às vezes não informa o tipo (MKV no Windows): deduz pela extensão. */
export function tipoDoVideo(arquivo: File): string | null {
  if (TIPOS.includes(arquivo.type)) return arquivo.type;
  const extensao = arquivo.name.split('.').pop()?.toLowerCase();
  const porExtensao: Record<string, string> = {
    mp4: 'video/mp4',
    mov: 'video/quicktime',
    mkv: 'video/x-matroska',
    webm: 'video/webm',
  };
  return (extensao && porExtensao[extensao]) || null;
}

const emAndamento = (v: Video | null | undefined) =>
  v?.status === 'PENDENTE' || v?.status === 'PROCESSANDO';

/** Vídeo da aula: envio em partes direto ao armazenamento, acompanhamento da conversão e remoção. */
export function VideoAula({ aulaId }: { aulaId: number }) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [enviado, setEnviado] = useState<number | null>(null);
  const [total, setTotal] = useState(0);
  const [erroEnvio, setErroEnvio] = useState<string>();
  const cancelar = useRef<AbortController | null>(null);
  const rota = `/api/adm/aulas/${aulaId}/video`;

  const video = useCarregar(
    () =>
      api.get<Video>(rota).catch((erro: unknown) => {
        if (erro instanceof ErroApi && erro.status === 404) return null;
        throw erro;
      }),
    [rota],
  );
  const v = video.dados;

  // Enquanto o worker converte, consulta o status de tempos em tempos
  useEffect(() => {
    if (!emAndamento(v)) return;
    const relogio = window.setTimeout(video.recarregar, INTERVALO_CONSULTA);
    return () => window.clearTimeout(relogio);
  }, [v, video.recarregar]);

  useEffect(() => () => cancelar.current?.abort(), []);

  const enviar = async (arquivo: File) => {
    const tipo = tipoDoVideo(arquivo);
    setErroEnvio(undefined);
    if (!tipo) {
      setErroEnvio('Envie um vídeo MP4, MOV, MKV ou WebM.');
      return;
    }
    cancelar.current = new AbortController();
    setTotal(arquivo.size);
    setEnviado(0);
    try {
      const envio = await api.post<EnvioVideo>(`${rota}/envio`, {
        nomeArquivo: arquivo.name,
        contentType: tipo,
        tamanho: arquivo.size,
      });
      const partes = await enviarEmPartes(
        arquivo,
        envio.tamanhoParte,
        envio.partes,
        setEnviado,
        cancelar.current.signal,
      );
      video.definir(await api.post<Video>(`${rota}/envio/conclusao`, { partes }));
    } catch (erro) {
      setErroEnvio(erro instanceof DOMException ? 'Envio cancelado.' : mensagemDeErro(erro));
    } finally {
      setEnviado(null);
      cancelar.current = null;
    }
  };

  const enviando = enviado !== null;
  return (
    <Card titulo="Vídeo da aula">
      {video.erro && <Alerta severidade="err" mensagem={video.erro} />}
      {erroEnvio && (
        <Alerta severidade="err" titulo="O vídeo não foi enviado" mensagem={erroEnvio} />
      )}

      {v && (
        <div className="pc-pilha">
          <div className="pc-linha">
            <Status
              rotulo={STATUS_VIDEO[v.status].rotulo}
              severidade={STATUS_VIDEO[v.status].severidade}
            />
            {v.publicado && v.status !== 'PRONTO' && (
              <Status rotulo="O vídeo anterior continua no ar" severidade="info" />
            )}
          </div>
          {emAndamento(v) && (
            <Progresso
              rotulo="Conversão do vídeo"
              valor={50}
              aparencia="listrado"
              animado
              mostrarValor="nenhum"
            />
          )}
          {v.status === 'ERRO' && (
            <Alerta
              severidade="err"
              titulo="A conversão falhou"
              mensagem={v.erro ?? 'Erro desconhecido.'}
            />
          )}
          {v.thumbnailUrl && (
            <Imagem src={v.thumbnailUrl} alt="Quadro do vídeo" className="pc-capa" />
          )}
          <div className="pc-grade-detalhes">
            <Detalhes rotulo="Arquivo enviado" valor={v.nomeOriginal ?? '—'} />
            <Detalhes rotulo="Duração" valor={duracao(v.duracaoSegundos)} />
            <Detalhes
              rotulo="Resolução"
              valor={v.largura && v.altura ? `${v.largura}×${v.altura}` : '—'}
            />
            <Detalhes
              rotulo="Qualidades"
              valor={v.variantes.length ? v.variantes.join(', ') : '—'}
            />
            <Detalhes rotulo="Tamanho original" valor={bytes(v.tamanhoOriginal)} />
            <Detalhes
              rotulo="Tamanho convertido"
              valor={
                v.tamanhoFinal == null
                  ? '—'
                  : `${bytes(v.tamanhoFinal)}${v.reducaoPercentual != null ? ` (−${v.reducaoPercentual.toFixed(0)}%)` : ''}`
              }
            />
            <Detalhes rotulo="Processado em" valor={dataHora(v.processadoEm)} />
          </div>
        </div>
      )}
      {v === null && (
        <p className="pc-dica">Esta aula ainda não tem vídeo. Envie MP4, MOV, MKV ou WebM.</p>
      )}

      {enviando && (
        <div className="pc-pilha">
          <Progresso rotulo="Envio do vídeo" valor={enviado} max={total || 1} mostrarValor="fim" />
          <div>
            <Botao
              rotulo="Cancelar envio"
              aparencia="texto"
              severidade="err"
              onClick={() => cancelar.current?.abort()}
            />
          </div>
        </div>
      )}

      <div className="pc-linha">
        <EscolherArquivo
          rotulo={v ? 'Enviar novo vídeo' : 'Enviar vídeo'}
          icone="upload"
          aceitar={ACEITOS}
          carregando={enviando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {v?.status === 'ERRO' && (
          <Botao
            rotulo="Reprocessar"
            icone="refresh-cw"
            aparencia="contorno"
            disabled={executando}
            onClick={async () => {
              const novo = await executar(
                () => api.post<Video>(`${rota}/reprocessamento`),
                'Vídeo de volta à fila',
              );
              if (novo) video.definir(novo);
            }}
          />
        )}
        {v && !emAndamento(v) && (
          <Botao
            rotulo="Remover vídeo"
            icone="trash-2"
            aparencia="texto"
            severidade="err"
            disabled={executando || enviando}
            onClick={async () => {
              if (
                !(await confirmar.remover({
                  mensagem: 'O vídeo e as versões convertidas serão apagados.',
                }))
              )
                return;
              const ok = await executar(() => api.delete(rota).then(() => true), 'Vídeo removido');
              if (ok) video.definir(null);
            }}
          />
        )}
      </div>
    </Card>
  );
}
`````

`web/adm/src/paginas/curso/DadosCursoAba.tsx`:

`````tsx
import {
  Botao,
  Card,
  CampoSelect,
  Detalhes,
  Imagem,
  Status,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { CAMINHOS } from '../../caminhos.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  opcoes,
  STATUS_CURSO,
  type Curso,
  type EnvioSimples,
  type StatusCurso,
} from '../../tipos.ts';
import { FormCurso } from '../cursos/FormCurso.tsx';

const CAPA_MAXIMA = 5 * 1024 * 1024;

interface Props {
  curso: Curso;
  aoMudar: (curso: Curso) => void;
}

export function DadosCursoAba({ curso, aoMudar }: Props) {
  const navigate = useNavigate();
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [editando, setEditando] = useState(false);
  const [enviandoCapa, setEnviandoCapa] = useState(false);
  const rota = `/api/adm/cursos/${curso.id}`;

  const enviarCapa = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma imagem JPG ou PNG.');
      return;
    }
    if (arquivo.size > CAPA_MAXIMA) {
      toast.err('Imagem grande demais', 'A capa pode ter até 5 MB.');
      return;
    }
    setEnviandoCapa(true);
    const atualizado = await executar(async () => {
      const envio = await api.post<EnvioSimples>(`${rota}/capa/envio`, {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Curso>(`${rota}/capa`, { chave: envio.chave });
    }, 'Capa atualizada');
    setEnviandoCapa(false);
    if (atualizado) aoMudar(atualizado);
  };

  return (
    <div className="pc-pilha">
      <Card
        titulo="Dados do curso"
        ferramentas={
          <Botao
            rotulo="Editar"
            icone="pencil"
            aparencia="contorno"
            onClick={() => setEditando(true)}
          />
        }
      >
        <div className="pc-grade-detalhes">
          <Detalhes rotulo="Título" valor={curso.titulo} />
          <Detalhes rotulo="Endereço (slug)" valor={curso.slug} />
          <Detalhes rotulo="Resumo" valor={curso.resumo} />
          <Detalhes rotulo="Atualizado em" valor={dataHora(curso.atualizadoEm)} />
        </div>
        {curso.descricao && <p className="pc-texto-longo">{curso.descricao}</p>}
      </Card>

      <Card titulo="Publicação">
        <div className="pc-linha">
          <Status
            rotulo={STATUS_CURSO[curso.status].rotulo}
            severidade={STATUS_CURSO[curso.status].severidade}
          />
          <CampoSelect<StatusCurso>
            rotulo="Alterar status"
            opcoes={opcoes(STATUS_CURSO)}
            valor={curso.status}
            disabled={executando}
            aoMudar={(status) => {
              if (!status || status === curso.status) return;
              void executar(
                () => api.patch<Curso>(`${rota}/status`, { status }),
                'Status do curso alterado',
              ).then((c) => c && aoMudar(c));
            }}
          />
        </div>
        <p className="pc-dica">Só cursos publicados aparecem no catálogo do site.</p>
      </Card>

      <Card titulo="Capa">
        {curso.capaUrl ? (
          <Imagem src={curso.capaUrl} alt={`Capa de ${curso.titulo}`} className="pc-capa" />
        ) : (
          <p className="pc-dica">Sem capa. Use uma imagem JPG ou PNG de até 5 MB.</p>
        )}
        <div className="pc-linha">
          <EscolherArquivo
            rotulo={curso.capaUrl ? 'Trocar capa' : 'Enviar capa'}
            icone="image"
            aceitar="image/jpeg,image/png"
            carregando={enviandoCapa}
            aoEscolher={(arquivo) => void enviarCapa(arquivo)}
          />
          {curso.capaUrl && (
            <Botao
              rotulo="Remover capa"
              icone="trash-2"
              aparencia="texto"
              severidade="err"
              onClick={async () => {
                if (!(await confirmar.remover({ mensagem: 'A capa do curso será removida.' })))
                  return;
                const ok = await executar(
                  () => api.delete(`${rota}/capa`).then(() => true),
                  'Capa removida',
                );
                if (ok) aoMudar({ ...curso, capaUrl: null });
              }}
            />
          )}
        </div>
      </Card>

      <Card titulo="Excluir curso">
        <p className="pc-dica">
          Só dá para excluir um curso sem turmas. Para tirar do ar sem perder nada, arquive.
        </p>
        <Botao
          rotulo="Excluir curso"
          icone="trash-2"
          severidade="err"
          aparencia="contorno"
          onClick={async () => {
            if (!(await confirmar.remover({ mensagem: `"${curso.titulo}" será excluído.` })))
              return;
            const ok = await executar(() => api.delete(rota).then(() => true), 'Curso excluído');
            if (ok) navigate(CAMINHOS.cursos);
          }}
        />
      </Card>

      {editando && (
        <FormCurso
          aberto
          curso={curso}
          aoFechar={() => setEditando(false)}
          aoSalvar={async (dados) => {
            aoMudar(await api.put<Curso>(rota, dados));
            toast.ok('Curso salvo');
            setEditando(false);
          }}
        />
      )}
    </div>
  );
}
`````

`web/adm/src/paginas/curso/TurmasAba.tsx`:

`````tsx
import { AcaoLinha, Botao, Status, Tabela, useConfirmacao, useToast } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoTurma } from '../../caminhos.ts';
import { DialogoTexto } from '../../componentes/DialogoTexto.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_TURMA, type Curso, type Turma } from '../../tipos.ts';

export function TurmasAba({ curso }: { curso: Curso }) {
  const navigate = useNavigate();
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [dialogo, setDialogo] = useState<{ turma?: Turma } | null>(null);
  const turmas = useCarregar(
    () => api.get<Turma[]>(`/api/adm/cursos/${curso.id}/turmas`),
    [curso.id],
  );

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        <Botao rotulo="Nova turma" icone="plus" onClick={() => setDialogo({})} />
      </div>
      <Tabela<Turma>
        rotulo="Turmas"
        colunas={[
          { campo: 'nome', rotulo: 'Turma', destaque: true },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (t) => (
              <Status
                rotulo={STATUS_TURMA[t.status].rotulo}
                severidade={STATUS_TURMA[t.status].severidade}
              />
            ),
          },
          {
            campo: 'percentualConclusao',
            rotulo: 'Certificado com',
            formato: (v) => `${v as number}% concluído`,
          },
        ]}
        registros={turmas.dados ?? []}
        carregando={turmas.carregando && !turmas.dados}
        erro={turmas.erro}
        aoRecarregar={turmas.recarregar}
        vazioTitulo="Nenhuma turma"
        vazioDescricao="O conteúdo (módulos e aulas) e as matrículas ficam na turma."
        vazioIcone="users"
        aoClicarLinha={(t) => navigate(caminhoTurma(curso.id, t.id))}
        acoes={(t) => (
          <>
            <AcaoLinha
              icone="pencil"
              rotulo={`Renomear ${t.nome}`}
              onClick={() => setDialogo({ turma: t })}
            />
            <AcaoLinha
              icone="trash-2"
              severidade="err"
              rotulo={`Excluir ${t.nome}`}
              onClick={async () => {
                if (!(await confirmar.remover({ mensagem: `A turma "${t.nome}" será excluída.` })))
                  return;
                const ok = await executar(
                  () => api.delete(`/api/adm/turmas/${t.id}`).then(() => true),
                  'Turma excluída',
                );
                if (ok) turmas.recarregar();
              }}
            />
          </>
        )}
      />
      {dialogo && (
        <DialogoTexto
          titulo={dialogo.turma ? 'Renomear turma' : 'Nova turma'}
          rotulo="Nome da turma"
          campo="nome"
          maximo={120}
          inicial={dialogo.turma?.nome}
          rotuloSalvar={dialogo.turma ? 'Salvar' : 'Criar turma'}
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (nome) => {
            if (dialogo.turma) {
              await api.put(`/api/adm/turmas/${dialogo.turma.id}`, { nome });
              toast.ok('Turma renomeada');
              turmas.recarregar();
            } else {
              const turma = await api.post<Turma>(`/api/adm/cursos/${curso.id}/turmas`, { nome });
              toast.ok('Turma criada', turma.nome);
              navigate(caminhoTurma(curso.id, turma.id));
            }
            setDialogo(null);
          }}
        />
      )}
    </div>
  );
}
`````

`web/adm/src/paginas/turma/ConfiguracaoTurmaAba.tsx`:

`````tsx
import { Alerta, Botao, Card, CampoInteiro, CampoSelect, useConfirmacao } from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi } from '@compartilhado/api/erros.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { opcoes, STATUS_TURMA, type StatusTurma, type Turma } from '../../tipos.ts';

interface Props {
  turma: Turma;
  aoMudar: (turma: Turma) => void;
  aoExcluir: () => void;
}

export function ConfiguracaoTurmaAba({ turma, aoMudar, aoExcluir }: Props) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const [percentual, setPercentual] = useState<number | null>(turma.percentualConclusao);
  const [carga, setCarga] = useState<number | null>(turma.cargaHorariaHoras);
  const [erros, setErros] = useState<{ percentual?: string; carga?: string }>({});
  const rota = `/api/adm/turmas/${turma.id}`;

  const salvarCertificado = async () => {
    const novos = {
      percentual:
        percentual == null || percentual < 1 || percentual > 100
          ? 'Informe um percentual de 1 a 100.'
          : undefined,
      carga: carga != null && (carga < 1 || carga > 10_000) ? 'De 1 a 10.000 horas.' : undefined,
    };
    setErros(novos);
    if (novos.percentual || novos.carga) return;
    const nova = await executar(
      () =>
        api
          .put<Turma>(`${rota}/certificado`, {
            percentualConclusao: percentual,
            cargaHorariaHoras: carga,
          })
          .catch((erro: unknown) => {
            if (erro instanceof ErroApi) {
              setErros({
                percentual: erro.erroDoCampo('percentualConclusao'),
                carga: erro.erroDoCampo('cargaHorariaHoras'),
              });
            }
            throw erro;
          }),
      'Regras do certificado salvas',
    );
    if (nova) aoMudar(nova);
  };

  return (
    <div className="pc-pilha">
      <Card titulo="Status da turma">
        <CampoSelect<StatusTurma>
          rotulo="Status"
          opcoes={opcoes(STATUS_TURMA)}
          valor={turma.status}
          disabled={executando}
          aoMudar={(status) => {
            if (!status || status === turma.status) return;
            void executar(
              () => api.patch<Turma>(`${rota}/status`, { status }),
              'Status da turma alterado',
            ).then((t) => t && aoMudar(t));
          }}
        />
        <p className="pc-dica">
          O catálogo mostra o conteúdo da turma ativa mais recente do curso. Turma encerrada
          continua acessível para quem tem matrícula vigente.
        </p>
      </Card>

      <Card titulo="Certificado">
        <form
          noValidate
          className="pc-grade-detalhes"
          onSubmit={(evento) => {
            evento.preventDefault();
            void salvarCertificado();
          }}
        >
          <CampoInteiro
            rotulo="Conclusão mínima (%)"
            valor={percentual}
            aoMudar={setPercentual}
            erro={erros.percentual}
            obrigatorio
          />
          <CampoInteiro
            rotulo="Carga horária (horas)"
            valor={carga}
            aoMudar={setCarga}
            erro={erros.carga}
            dica="Em branco, o certificado sai sem carga horária."
          />
          <div>
            <Botao type="submit" rotulo="Salvar certificado" icone="save" carregando={executando} />
          </div>
        </form>
      </Card>

      <Card titulo="Excluir turma">
        <Alerta
          severidade="warn"
          mensagem="Só dá para excluir uma turma sem matrículas e sem conteúdo. Para encerrar, mude o status."
        />
        <Botao
          rotulo="Excluir turma"
          icone="trash-2"
          severidade="err"
          aparencia="contorno"
          onClick={async () => {
            if (!(await confirmar.remover({ mensagem: `A turma "${turma.nome}" será excluída.` })))
              return;
            const ok = await executar(() => api.delete(rota).then(() => true), 'Turma excluída');
            if (ok) aoExcluir();
          }}
        />
      </Card>
    </div>
  );
}
`````

`web/adm/src/paginas/turma/ConteudoAba.tsx`:

`````tsx
import {
  AcaoLinha,
  Botao,
  Card,
  Carregando,
  Status,
  Vazio,
  Alerta,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoAula } from '../../caminhos.ts';
import { ArquivosApoio } from '../../componentes/ArquivosApoio.tsx';
import { DialogoTexto } from '../../componentes/DialogoTexto.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { STATUS_CONTEUDO, type Aula, type Curso, type Modulo, type Turma } from '../../tipos.ts';

/** Ids na nova ordem, com o item da posição `de` trocado com o vizinho. */
export function trocar<T extends { id: number }>(itens: T[], de: number, para: number): number[] {
  const ids = itens.map((item) => item.id);
  [ids[de], ids[para]] = [ids[para]!, ids[de]!];
  return ids;
}

type Dialogo =
  | { tipo: 'novoModulo' }
  | { tipo: 'renomear'; modulo: Modulo }
  | { tipo: 'novaAula'; modulo: Modulo };

export function ConteudoAba({ curso, turma }: { curso: Curso; turma: Turma }) {
  const navigate = useNavigate();
  const toast = useToast();
  const [dialogo, setDialogo] = useState<Dialogo | null>(null);
  const modulos = useCarregar(
    () => api.get<Modulo[]>(`/api/adm/turmas/${turma.id}/modulos`),
    [turma.id],
  );

  if (modulos.erro && !modulos.dados) {
    return (
      <Alerta
        severidade="err"
        titulo="Não foi possível carregar os módulos"
        mensagem={modulos.erro}
      />
    );
  }
  if (!modulos.dados) return <Carregando mensagem="Carregando o conteúdo..." />;
  const lista = modulos.dados;

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        <Botao
          rotulo="Novo módulo"
          icone="plus"
          onClick={() => setDialogo({ tipo: 'novoModulo' })}
        />
      </div>
      {lista.length === 0 && (
        <Vazio
          titulo="Nenhum módulo"
          descricao="Crie o primeiro módulo e depois as aulas dele."
          icone="layers"
        />
      )}
      {lista.map((modulo, indice) => (
        <CardModulo
          key={modulo.id}
          modulo={modulo}
          primeiro={indice === 0}
          ultimo={indice === lista.length - 1}
          aoMover={async (para) => {
            modulos.definir(
              await api.put<Modulo[]>(`/api/adm/turmas/${turma.id}/modulos/ordem`, {
                ids: trocar(lista, indice, para === 'cima' ? indice - 1 : indice + 1),
              }),
            );
          }}
          aoMudar={(novo) => modulos.definir(lista.map((m) => (m.id === novo.id ? novo : m)))}
          aoExcluir={modulos.recarregar}
          aoRenomear={() => setDialogo({ tipo: 'renomear', modulo })}
          aoNovaAula={() => setDialogo({ tipo: 'novaAula', modulo })}
          aoAbrirAula={(aula) => navigate(caminhoAula(curso.id, turma.id, modulo.id, aula.id))}
        />
      ))}
      {dialogo?.tipo === 'novoModulo' && (
        <DialogoTexto
          titulo="Novo módulo"
          rotulo="Título do módulo"
          campo="titulo"
          maximo={150}
          rotuloSalvar="Criar módulo"
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            await api.post(`/api/adm/turmas/${turma.id}/modulos`, { titulo });
            toast.ok('Módulo criado', titulo);
            modulos.recarregar();
            setDialogo(null);
          }}
        />
      )}
      {dialogo?.tipo === 'renomear' && (
        <DialogoTexto
          titulo="Renomear módulo"
          rotulo="Título do módulo"
          campo="titulo"
          maximo={150}
          inicial={dialogo.modulo.titulo}
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            const novo = await api.put<Modulo>(`/api/adm/modulos/${dialogo.modulo.id}`, { titulo });
            modulos.definir(lista.map((m) => (m.id === novo.id ? novo : m)));
            toast.ok('Módulo renomeado');
            setDialogo(null);
          }}
        />
      )}
      {dialogo?.tipo === 'novaAula' && (
        <DialogoTexto
          titulo={`Nova aula em ${dialogo.modulo.titulo}`}
          rotulo="Título da aula"
          campo="titulo"
          maximo={150}
          rotuloSalvar="Criar aula"
          aoFechar={() => setDialogo(null)}
          aoSalvar={async (titulo) => {
            const aula = await api.post<Aula>(`/api/adm/modulos/${dialogo.modulo.id}/aulas`, {
              titulo,
              gratuita: false,
            });
            toast.ok('Aula criada', aula.titulo);
            navigate(caminhoAula(curso.id, turma.id, dialogo.modulo.id, aula.id));
          }}
        />
      )}
    </div>
  );
}

interface PropsModulo {
  modulo: Modulo;
  primeiro: boolean;
  ultimo: boolean;
  aoMover: (para: 'cima' | 'baixo') => Promise<void>;
  aoMudar: (modulo: Modulo) => void;
  aoExcluir: () => void;
  aoRenomear: () => void;
  aoNovaAula: () => void;
  aoAbrirAula: (aula: Aula) => void;
}

function CardModulo({
  modulo,
  primeiro,
  ultimo,
  aoMover,
  aoMudar,
  aoExcluir,
  aoRenomear,
  aoNovaAula,
  aoAbrirAula,
}: PropsModulo) {
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();
  const aulas = useCarregar(
    () => api.get<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas`),
    [modulo.id],
  );
  const status = STATUS_CONTEUDO[modulo.status];
  const lista = aulas.dados ?? [];

  return (
    <Card
      titulo={`${modulo.ordem}. ${modulo.titulo}`}
      aria-label={`Módulo ${modulo.titulo}`}
      ferramentas={
        <div className="pc-linha">
          <Status rotulo={status.rotulo} severidade={status.severidade} />
          <AcaoLinha
            icone="chevron-up"
            rotulo={`Subir ${modulo.titulo}`}
            disabled={primeiro || executando}
            onClick={() => void executar(() => aoMover('cima'))}
          />
          <AcaoLinha
            icone="chevron-down"
            rotulo={`Descer ${modulo.titulo}`}
            disabled={ultimo || executando}
            onClick={() => void executar(() => aoMover('baixo'))}
          />
          <AcaoLinha icone="pencil" rotulo={`Renomear ${modulo.titulo}`} onClick={aoRenomear} />
          <AcaoLinha
            icone={modulo.status === 'ATIVO' ? 'eye-off' : 'eye'}
            rotulo={
              modulo.status === 'ATIVO' ? `Inativar ${modulo.titulo}` : `Ativar ${modulo.titulo}`
            }
            onClick={async () => {
              const novo = await executar(() =>
                api.patch<Modulo>(`/api/adm/modulos/${modulo.id}/status`, {
                  status: modulo.status === 'ATIVO' ? 'INATIVO' : 'ATIVO',
                }),
              );
              if (novo) aoMudar(novo);
            }}
          />
          <AcaoLinha
            icone="trash-2"
            severidade="err"
            rotulo={`Excluir ${modulo.titulo}`}
            onClick={async () => {
              if (
                !(await confirmar.remover({
                  mensagem: `O módulo "${modulo.titulo}" será excluído.`,
                }))
              )
                return;
              const ok = await executar(
                () => api.delete(`/api/adm/modulos/${modulo.id}`).then(() => true),
                'Módulo excluído',
              );
              if (ok) aoExcluir();
            }}
          />
        </div>
      }
    >
      {aulas.erro && <Alerta severidade="err" mensagem={aulas.erro} />}
      {aulas.carregando && !aulas.dados && <Carregando mensagem="Carregando as aulas..." />}
      {aulas.dados && lista.length === 0 && <p className="pc-dica">Nenhuma aula neste módulo.</p>}
      {lista.length > 0 && (
        <ol className="pc-lista-aulas" aria-label={`Aulas de ${modulo.titulo}`}>
          {lista.map((aula, indice) => (
            <li key={aula.id}>
              <button
                type="button"
                className="pc-link pc-aula-titulo"
                onClick={() => aoAbrirAula(aula)}
              >
                {aula.titulo}
              </button>
              {aula.gratuita && <Status rotulo="Gratuita" severidade="info" />}
              <Status
                rotulo={STATUS_CONTEUDO[aula.status].rotulo}
                severidade={STATUS_CONTEUDO[aula.status].severidade}
              />
              <AcaoLinha
                icone="chevron-up"
                rotulo={`Subir ${aula.titulo}`}
                disabled={indice === 0 || executando}
                onClick={async () => {
                  const nova = await executar(() =>
                    api.put<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas/ordem`, {
                      ids: trocar(lista, indice, indice - 1),
                    }),
                  );
                  if (nova) aulas.definir(nova);
                }}
              />
              <AcaoLinha
                icone="chevron-down"
                rotulo={`Descer ${aula.titulo}`}
                disabled={indice === lista.length - 1 || executando}
                onClick={async () => {
                  const nova = await executar(() =>
                    api.put<Aula[]>(`/api/adm/modulos/${modulo.id}/aulas/ordem`, {
                      ids: trocar(lista, indice, indice + 1),
                    }),
                  );
                  if (nova) aulas.definir(nova);
                }}
              />
            </li>
          ))}
        </ol>
      )}
      <div className="pc-linha">
        <Botao
          rotulo="Nova aula"
          icone="plus"
          aparencia="contorno"
          tamanho="pequeno"
          onClick={aoNovaAula}
        />
      </div>
      <ArquivosApoio dono="modulos" donoId={modulo.id} titulo="Arquivos de apoio do módulo" />
    </Card>
  );
}
`````

`web/adm/src/paginas/turma/ForumAba.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  Carregando,
  Dialogo,
  Status,
  Tabela,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Topico, TopicoResumo, Turma } from '../../tipos.ts';

const TAMANHO = 20;

/** Moderação do fórum da turma: o admin vê também o que está oculto. */
export function ForumAba({ turma }: { turma: Turma }) {
  const [pagina, setPagina] = useState(1);
  const [aberto, setAberto] = useState<number | null>(null);
  const topicos = useCarregar(
    () =>
      api.get<Pagina<TopicoResumo>>(
        `/api/adm/turmas/${turma.id}/forum?page=${pagina - 1}&size=${TAMANHO}`,
      ),
    [turma.id, pagina],
  );

  return (
    <div className="pc-pilha">
      <Tabela<TopicoResumo>
        rotulo="Tópicos do fórum"
        colunas={[
          {
            campo: 'titulo',
            rotulo: 'Tópico',
            destaque: true,
            render: (t) => (
              <span className="pc-linha">
                {t.fixado && <Badge cor="pri" icone="pin" rotulo="Fixado" aparencia="suave" />}
                {t.titulo}
              </span>
            ),
          },
          { campo: 'autorNome', rotulo: 'Autor' },
          { campo: 'respostas', rotulo: 'Respostas', alinhamento: 'direita' },
          {
            campo: 'respondidoInstrutor',
            rotulo: 'Situação',
            render: (t) =>
              t.oculto ? (
                <Status rotulo="Oculto" severidade="neutral" />
              ) : t.respondidoInstrutor ? (
                <Status rotulo="Respondido" severidade="ok" />
              ) : (
                <Status rotulo="Aguardando resposta" severidade="warn" />
              ),
          },
          { campo: 'criadoEm', rotulo: 'Criado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={topicos.dados?.itens ?? []}
        carregando={topicos.carregando && !topicos.dados}
        atualizando={topicos.carregando && !!topicos.dados}
        erro={topicos.erro}
        aoRecarregar={topicos.recarregar}
        vazioTitulo="Nenhum tópico"
        vazioDescricao="As dúvidas dos alunos aparecem aqui."
        vazioIcone="message-square"
        aoClicarLinha={(t) => setAberto(t.id)}
        paginacao={{
          total: topicos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {aberto != null && (
        <PainelTopico
          topicoId={aberto}
          aoFechar={() => setAberto(null)}
          aoMudar={topicos.recarregar}
        />
      )}
    </div>
  );
}

function PainelTopico({
  topicoId,
  aoFechar,
  aoMudar,
}: {
  topicoId: number;
  aoFechar: () => void;
  aoMudar: () => void;
}) {
  const toast = useToast();
  const { executar, executando } = useAcao();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/adm/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const alterar = async (acao: () => Promise<Topico>, sucesso: string) => {
    const novo = await executar(acao, sucesso);
    if (novo) {
      topico.definir(novo);
      aoMudar();
    }
  };

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setEnviando(true);
    setErro(undefined);
    try {
      topico.definir(
        await api.post<Topico>(`/api/adm/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
        }),
      );
      setResposta('');
      toast.ok('Resposta publicada');
      aoMudar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  const t = topico.dados;
  return (
    <Dialogo
      aberto
      aparencia="lateral"
      tamanho="grande"
      titulo={t?.titulo ?? 'Tópico'}
      subtitulo={t ? `${t.autorNome} · ${dataHora(t.criadoEm)}` : undefined}
      aoFechar={aoFechar}
    >
      {topico.erro && <Alerta severidade="err" mensagem={topico.erro} />}
      {!t && !topico.erro && <Carregando mensagem="Carregando o tópico..." />}
      {t && (
        <div className="pc-pilha">
          <div className="pc-linha">
            {t.oculto && <Status rotulo="Oculto para os alunos" severidade="neutral" />}
            <Botao
              rotulo={t.fixado ? 'Desafixar' : 'Fixar'}
              icone={t.fixado ? 'pin-off' : 'pin'}
              aparencia="contorno"
              tamanho="pequeno"
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { fixado: !t.fixado }),
                  t.fixado ? 'Tópico desafixado' : 'Tópico fixado',
                )
              }
            />
            <Botao
              rotulo={t.oculto ? 'Mostrar tópico' : 'Ocultar tópico'}
              icone={t.oculto ? 'eye' : 'eye-off'}
              aparencia="contorno"
              tamanho="pequeno"
              severidade={t.oculto ? 'pri' : 'err'}
              disabled={executando}
              onClick={() =>
                void alterar(
                  () => api.patch<Topico>(`/api/adm/forum/topicos/${t.id}`, { oculto: !t.oculto }),
                  t.oculto ? 'Tópico visível de novo' : 'Tópico ocultado',
                )
              }
            />
          </div>
          {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
          <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />

          <h3 className="pc-subtitulo">Respostas ({t.respostas.length})</h3>
          {t.respostas.map((r) => (
            <article
              key={r.id}
              className={`pc-resposta${r.oculto ? ' pc-resposta-oculta' : ''}`}
              aria-label={`Resposta de ${r.autorNome}`}
            >
              <header className="pc-linha">
                <strong>{r.autorNome}</strong>
                {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
                {r.oculto && <Status rotulo="Oculta" severidade="neutral" />}
                <span className="pc-dica">{dataHora(r.criadoEm)}</span>
                <Botao
                  rotulo={r.oculto ? 'Mostrar' : 'Ocultar'}
                  aparencia="texto"
                  tamanho="pequeno"
                  disabled={executando}
                  onClick={() =>
                    void alterar(
                      () =>
                        api.patch<Topico>(`/api/adm/forum/respostas/${r.id}`, {
                          oculto: !r.oculto,
                        }),
                      r.oculto ? 'Resposta visível de novo' : 'Resposta ocultada',
                    )
                  }
                />
              </header>
              <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
            </article>
          ))}

          <form
            noValidate
            className="pc-pilha"
            onSubmit={(evento) => {
              evento.preventDefault();
              void responder();
            }}
          >
            <CampoTextarea
              rotulo="Responder como instrutor"
              valor={resposta}
              aoMudar={setResposta}
              erro={erro}
              rows={5}
              maxLength={10_000}
              dica="Aceita Markdown."
            />
            <div>
              <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
            </div>
          </form>
        </div>
      )}
    </Dialogo>
  );
}
`````

`web/adm/src/paginas/turma/MatriculasAba.tsx`:

`````tsx
import {
  AcaoLinha,
  Alerta,
  Botao,
  CampoData,
  CampoEmail,
  CampoSelect,
  CampoTexto,
  Form,
  FormSecao,
  Status,
  Tabela,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { data, diaLocal, fimDoDia, inicioDoDia } from '@compartilhado/ui/formato.ts';
import { erroEmail } from '@compartilhado/ui/validacao.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  FORMAS_PAGAMENTO,
  opcoes,
  SITUACAO_MATRICULA,
  type FormaPagamento,
  type Matricula,
  type Turma,
} from '../../tipos.ts';

const TAMANHO = 20;

export function MatriculasAba({ turma }: { turma: Turma }) {
  const confirmar = useConfirmacao();
  const { executar } = useAcao();
  const [pagina, setPagina] = useState(1);
  const [dialogo, setDialogo] = useState<{ matricula?: Matricula } | null>(null);
  const matriculas = useCarregar(
    () =>
      api.get<Pagina<Matricula>>(
        `/api/adm/turmas/${turma.id}/matriculas?page=${pagina - 1}&size=${TAMANHO}`,
      ),
    [turma.id, pagina],
  );

  return (
    <div className="pc-pilha">
      {turma.status === 'ENCERRADA' && (
        <Alerta
          severidade="warn"
          mensagem="A turma está encerrada e não recebe novas matrículas."
        />
      )}
      <div className="pc-linha pc-linha-fim">
        <Botao
          rotulo="Matricular aluno"
          icone="user-plus"
          disabled={turma.status === 'ENCERRADA'}
          onClick={() => setDialogo({})}
        />
      </div>
      <Tabela<Matricula>
        rotulo="Matrículas"
        colunas={[
          { campo: 'nome', rotulo: 'Aluno', destaque: true },
          { campo: 'email', rotulo: 'E-mail' },
          { campo: 'inicio', rotulo: 'Início', formato: (v) => data(v as string) },
          { campo: 'fim', rotulo: 'Fim', formato: (v) => (v ? data(v as string) : 'Sem fim') },
          {
            campo: 'situacao',
            rotulo: 'Situação',
            render: (m) => (
              <Status
                rotulo={SITUACAO_MATRICULA[m.situacao].rotulo}
                severidade={SITUACAO_MATRICULA[m.situacao].severidade}
              />
            ),
          },
          {
            campo: 'formaPagamento',
            rotulo: 'Forma',
            formato: (v) => FORMAS_PAGAMENTO[v as FormaPagamento],
          },
        ]}
        registros={matriculas.dados?.itens ?? []}
        carregando={matriculas.carregando && !matriculas.dados}
        atualizando={matriculas.carregando && !!matriculas.dados}
        erro={matriculas.erro}
        aoRecarregar={matriculas.recarregar}
        vazioTitulo="Nenhuma matrícula"
        vazioDescricao="Matricule um aluno pelo e-mail da conta dele."
        vazioIcone="users"
        paginacao={{
          total: matriculas.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
        acoes={(m) =>
          m.status === 'ATIVA' && (
            <>
              <AcaoLinha
                icone="calendar"
                rotulo={`Alterar período de ${m.nome}`}
                onClick={() => setDialogo({ matricula: m })}
              />
              <AcaoLinha
                icone="x"
                severidade="err"
                rotulo={`Cancelar matrícula de ${m.nome}`}
                onClick={async () => {
                  const sim = await confirmar({
                    titulo: 'Cancelar matrícula?',
                    mensagem: `${m.nome} perde o acesso à turma na hora.`,
                    severidade: 'err',
                    rotuloAceitar: 'Cancelar matrícula',
                    rotuloRecusar: 'Voltar',
                  });
                  if (!sim) return;
                  const ok = await executar(
                    () => api.post(`/api/adm/matriculas/${m.id}/cancelamento`).then(() => true),
                    'Matrícula cancelada',
                  );
                  if (ok) matriculas.recarregar();
                }}
              />
            </>
          )
        }
      />
      {dialogo && (
        <FormMatricula
          turma={turma}
          matricula={dialogo.matricula}
          aoFechar={() => setDialogo(null)}
          aoSalvar={() => {
            setDialogo(null);
            matriculas.recarregar();
          }}
        />
      )}
    </div>
  );
}

interface PropsForm {
  turma: Turma;
  /** Com matrícula, altera só o período. */
  matricula?: Matricula;
  aoFechar: () => void;
  aoSalvar: () => void;
}

type Erros = Partial<Record<'email' | 'inicio' | 'fim' | 'formaPagamento' | 'transacao', string>>;

function FormMatricula({ turma, matricula, aoFechar, aoSalvar }: PropsForm) {
  const toast = useToast();
  const [email, setEmail] = useState('');
  const [inicio, setInicio] = useState(diaLocal(matricula?.inicio));
  const [fim, setFim] = useState(diaLocal(matricula?.fim));
  const [forma, setForma] = useState<FormaPagamento | null>('MANUAL');
  const [transacao, setTransacao] = useState('');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Erros = {
      email: matricula ? undefined : erroEmail(email),
      inicio: matricula && !inicio ? 'Informe o início.' : undefined,
      fim: inicio && fim && fim < inicio ? 'O fim deve ser depois do início.' : undefined,
      formaPagamento: !matricula && !forma ? 'Escolha a forma.' : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (Object.values(novos).some(Boolean)) return;
    setSalvando(true);
    try {
      const periodo = {
        inicio: inicio ? inicioDoDia(inicio) : null,
        fim: fim ? fimDoDia(fim) : null,
      };
      if (matricula) {
        await api.put(`/api/adm/matriculas/${matricula.id}/periodo`, periodo);
        toast.ok('Período alterado', matricula.nome);
      } else {
        const nova = await api.post<Matricula>(`/api/adm/turmas/${turma.id}/matriculas`, {
          email: email.trim(),
          ...periodo,
          formaPagamento: forma,
          transacao: transacao.trim() || null,
        });
        toast.ok('Aluno matriculado', nova.nome);
      }
      aoSalvar();
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          email: erro.erroDoCampo('email'),
          inicio: erro.erroDoCampo('inicio'),
          fim: erro.erroDoCampo('fim'),
          transacao: erro.erroDoCampo('transacao'),
        });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Form
      arranjo="modal"
      aberto
      titulo={matricula ? `Período de ${matricula.nome}` : 'Matricular aluno'}
      rotuloSalvar={matricula ? 'Salvar período' : 'Matricular'}
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="medio"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda={matricula ? 'Período' : 'Aluno e período'} colunas={2}>
        {!matricula && (
          <CampoEmail
            rotulo="E-mail do aluno"
            valor={email}
            aoMudar={setEmail}
            erro={erros.email}
            obrigatorio
            dica="O aluno precisa ter conta na plataforma."
            className="lp-col-full"
          />
        )}
        <CampoData
          rotulo="Início"
          valor={inicio}
          aoMudar={setInicio}
          erro={erros.inicio}
          obrigatorio={!!matricula}
          dica={matricula ? undefined : 'Em branco, começa agora.'}
        />
        <CampoData
          rotulo="Fim"
          valor={fim}
          aoMudar={setFim}
          erro={erros.fim}
          dica="Em branco, sem prazo."
        />
        {!matricula && (
          <>
            <CampoSelect<FormaPagamento>
              rotulo="Forma"
              opcoes={opcoes(FORMAS_PAGAMENTO)}
              valor={forma}
              aoMudar={setForma}
              erro={erros.formaPagamento}
              obrigatorio
            />
            <CampoTexto
              rotulo="Transação"
              valor={transacao}
              aoMudar={setTransacao}
              erro={erros.transacao}
              maxLength={100}
              dica="Código do pagamento, se houver."
            />
          </>
        )}
      </FormSecao>
    </Form>
  );
}
`````

`web/aluno/src/caminhos.ts`:

`````ts
/** Caminhos da área do aluno; os de conta são os mesmos dos links que a API manda por e-mail. */
export const CAMINHOS = {
  entrar: '/entrar',
  cadastro: '/cadastro',
  confirmarEmail: '/confirmar-email',
  recuperarSenha: '/recuperar-senha',
  redefinirSenha: '/redefinir-senha',
  inicio: '/',
  certificados: '/certificados',
  perfil: '/perfil',
} as const;

export const caminhoTurma = (turmaId: number | string) => `/turmas/${turmaId}`;

export const caminhoAula = (turmaId: number | string, aulaId: number | string) =>
  `/turmas/${turmaId}/aulas/${aulaId}`;

export const caminhoTopico = (turmaId: number | string, topicoId: number | string) =>
  `/turmas/${turmaId}/forum/${topicoId}`;
`````

`web/aluno/src/layout/LayoutAluno.tsx`:

`````tsx
import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';
import { GuardaTermos } from './GuardaTermos.tsx';

const NAV: SecaoNav[] = [
  {
    itens: [
      { rotulo: 'Meus cursos', icone: 'graduation-cap', rota: CAMINHOS.inicio },
      { rotulo: 'Certificados', icone: 'award', rota: CAMINHOS.certificados },
      { rotulo: 'Meu perfil', icone: 'user', rota: CAMINHOS.perfil },
    ],
  },
];

/** Moldura da área logada do aluno: menu, usuário e, antes do conteúdo, o aceite dos termos vigentes. */
export function LayoutAluno() {
  const { estado, sair } = useSessao();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';

  return (
    <Estrutura
      marca={MARCA}
      nav={NAV}
      rotaAtual={pathname}
      aoNavegar={navigate}
      renderLink={(p) => (
        <Link
          to={p.href}
          className={p.className}
          onClick={p.onClick}
          aria-current={p['aria-current']}
        >
          {p.children}
        </Link>
      )}
      usuario={{ nome, cargo: 'Aluno' }}
      menuUsuario={[
        { id: 'perfil', rotulo: 'Meu perfil', icone: 'user' },
        { id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true },
      ]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'perfil') navigate(CAMINHOS.perfil);
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <GuardaTermos>
        <Outlet />
      </GuardaTermos>
    </Estrutura>
  );
}
`````

`web/aluno/src/paginas/EntrarPagina.tsx`:

`````tsx
import { TelaLogin } from '@celke/kole-react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { destinoAposLogin } from '@compartilhado/sessao/RotaPrivada.tsx';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export function EntrarPagina() {
  const { estado, entrar } = useSessao();
  const navigate = useNavigate();
  const [parametros] = useSearchParams();
  const destino = destinoAposLogin(parametros.get('voltar'));

  if (estado.status === 'autenticado') return <Navigate to={destino} replace />;

  return (
    <TelaLogin
      marca={MARCA}
      identificador="email"
      titulo="Entre na sua conta"
      descricao={
        parametros.get('senha') === 'alterada'
          ? 'Senha alterada. Entre com a nova senha.'
          : 'Acesse seus cursos, aulas e certificados.'
      }
      apresentacao={{
        chamada: 'Plataforma de Cursos',
        titulo: 'Aprenda no seu ritmo',
        descricao:
          'Aulas em vídeo, materiais de apoio, fórum com o instrutor e certificado ao concluir.',
        destaques: [
          { icone: 'circle-play', texto: 'Aulas em vídeo de qualquer lugar' },
          { icone: 'message-square', texto: 'Dúvidas respondidas no fórum' },
          { icone: 'award', texto: 'Certificado com código de validação' },
        ],
      }}
      linkSenha={<Link to={CAMINHOS.recuperarSenha}>Esqueci minha senha</Link>}
      acoes={
        <p>
          Ainda não tem conta? <Link to={CAMINHOS.cadastro}>Criar conta</Link>
        </p>
      }
      aoEntrar={async ({ usuario, senha }) => {
        try {
          await entrar(usuario.trim(), senha);
          navigate(destino, { replace: true });
        } catch (erro) {
          return { erro: mensagemDeErro(erro) };
        }
      }}
    />
  );
}
`````

`web/aluno/src/paginas/MeusCursosPagina.tsx`:

`````tsx
import {
  Aba,
  Abas,
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  Imagem,
  Status,
  Vazio,
} from '@celke/kole-react';
import { useNavigate, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { data } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import { caminhoTurma } from '../caminhos.ts';
import type { FiltroMeusCursos, MeuCurso } from '../tipos.ts';

const ABAS: { valor: FiltroMeusCursos; rotulo: string; vazio: string }[] = [
  {
    valor: 'ATIVOS',
    rotulo: 'Ativos',
    vazio: 'Quando a sua matrícula for confirmada, o curso aparece aqui.',
  },
  {
    valor: 'ARQUIVADOS',
    rotulo: 'Arquivados',
    vazio: 'Cursos que você arquivar ficam aqui; o acesso não muda.',
  },
  {
    valor: 'EXPIRADOS',
    rotulo: 'Expirados',
    vazio: 'Cursos com o período de acesso encerrado aparecem aqui.',
  },
];

/** Cursos do aluno, por aba. A aba fica no endereço (?aba=). */
export function MeusCursosPagina() {
  const [parametros, setParametros] = useSearchParams();
  const aba = (parametros.get('aba') as FiltroMeusCursos | null) ?? 'ATIVOS';

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meus cursos" icone="graduation-cap" />
      <Abas
        ativa={aba}
        aoMudar={(a) => setParametros({ aba: a }, { replace: true })}
        rotulo="Situação dos cursos"
      >
        {ABAS.map((a) => (
          <Aba key={a.valor} valor={a.valor} rotulo={a.rotulo}>
            {a.valor === aba && <ListaCursos filtro={a.valor} vazio={a.vazio} />}
          </Aba>
        ))}
      </Abas>
    </div>
  );
}

function ListaCursos({ filtro, vazio }: { filtro: FiltroMeusCursos; vazio: string }) {
  const navigate = useNavigate();
  const { executar, executando } = useAcao();
  const cursos = useCarregar(
    () => api.get<MeuCurso[]>(`/api/aluno/cursos?situacao=${filtro}`),
    [filtro],
  );

  if (cursos.erro && !cursos.dados) {
    return (
      <Alerta
        severidade="err"
        titulo="Não foi possível carregar seus cursos"
        mensagem={cursos.erro}
      />
    );
  }
  if (!cursos.dados) return <Carregando mensagem="Carregando seus cursos..." />;
  if (cursos.dados.length === 0) {
    return (
      <Vazio
        titulo={filtro === 'ATIVOS' ? 'Você ainda não tem cursos' : 'Nenhum curso aqui'}
        descricao={vazio}
        icone="book-open"
      />
    );
  }

  return (
    <div className="pc-grade-cards">
      {cursos.dados.map((curso) => {
        const vigente = curso.situacao === 'VIGENTE';
        const aguardando = curso.situacao === 'AGUARDANDO_INICIO';
        return (
          <Card
            key={curso.matriculaId}
            titulo={curso.cursoTitulo}
            subtitulo={curso.turmaNome}
            aria-label={curso.cursoTitulo}
            midia={
              curso.capaUrl ? <Imagem src={curso.capaUrl} alt="" proporcao="16 / 9" /> : undefined
            }
            rodape={
              <div className="pc-linha">
                {vigente && (
                  <Botao
                    rotulo="Continuar"
                    icone="circle-play"
                    tamanho="pequeno"
                    onClick={() => navigate(caminhoTurma(curso.turmaId))}
                  />
                )}
                {filtro !== 'EXPIRADOS' && (
                  <Botao
                    rotulo={curso.arquivada ? 'Desarquivar' : 'Arquivar'}
                    aparencia="texto"
                    tamanho="pequeno"
                    disabled={executando}
                    onClick={async () => {
                      const rota = `/api/aluno/matriculas/${curso.matriculaId}/arquivamento`;
                      const ok = await executar(
                        () => (curso.arquivada ? api.delete(rota) : api.put(rota)).then(() => true),
                        curso.arquivada ? 'Curso de volta aos ativos' : 'Curso arquivado',
                      );
                      if (ok) cursos.recarregar();
                    }}
                  />
                )}
              </div>
            }
          >
            <div className="pc-linha">
              <Status
                rotulo={vigente ? 'Em andamento' : aguardando ? 'Começa em breve' : 'Encerrado'}
                severidade={vigente ? 'ok' : aguardando ? 'info' : 'neutral'}
              />
              <span className="pc-dica">
                {aguardando
                  ? `Começa em ${data(curso.inicio)}`
                  : curso.fim
                    ? `Acesso até ${data(curso.fim)}`
                    : 'Acesso sem prazo'}
              </span>
            </div>
          </Card>
        );
      })}
    </div>
  );
}
`````

`web/aluno/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAluno } from './layout/LayoutAluno.tsx';
import { CadastroPagina } from './paginas/CadastroPagina.tsx';
import { ConfirmarEmailPagina } from './paginas/ConfirmarEmailPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { MeusCursosPagina } from './paginas/MeusCursosPagina.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CertificadosPagina } from './paginas/CertificadosPagina.tsx';
import { NaoEncontradaPagina } from './paginas/NaoEncontradaPagina.tsx';
import { PerfilPagina } from './paginas/perfil/PerfilPagina.tsx';
import { TopicoPagina } from './paginas/forum/TopicoPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';
import { RecuperarSenhaPagina } from './paginas/RecuperarSenhaPagina.tsx';
import { RedefinirSenhaPagina } from './paginas/RedefinirSenhaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  { path: CAMINHOS.cadastro, element: <CadastroPagina /> },
  { path: CAMINHOS.confirmarEmail, element: <ConfirmarEmailPagina /> },
  { path: CAMINHOS.recuperarSenha, element: <RecuperarSenhaPagina /> },
  { path: CAMINHOS.redefinirSenha, element: <RedefinirSenhaPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <LayoutAluno />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <MeusCursosPagina /> },
      { path: 'turmas/:turmaId', element: <TurmaPagina /> },
      { path: 'turmas/:turmaId/aulas/:aulaId', element: <AulaPagina /> },
      { path: 'turmas/:turmaId/forum/:topicoId', element: <TopicoPagina /> },
      { path: CAMINHOS.certificados, element: <CertificadosPagina /> },
      { path: CAMINHOS.perfil, element: <PerfilPagina /> },
    ],
  },
  { path: '*', element: <NaoEncontradaPagina /> },
]);
`````

`web/compartilhado/src/api/cliente.ts`:

`````ts
import { ErroApi } from './erros.ts';
import type { Sessao } from './tipos.ts';

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OpcoesRequisicao {
  corpo?: unknown;
  sinal?: AbortSignal;
  /** Não tenta renovar a sessão num 401 (login, refresh e logout). */
  semRenovar?: boolean;
}

/**
 * Cliente HTTP da API, na mesma origem do front (o Caddy, ou o proxy do Vite, leva `/api` à API).
 *
 * - O access token fica **só em memória** (nunca em localStorage): some ao fechar a aba.
 * - O refresh token é o cookie `HttpOnly` que o navegador manda sozinho para `/api/auth`.
 * - Num 401, renova a sessão uma vez e repete a requisição. Várias requisições que recebem 401 ao
 *   mesmo tempo esperam **a mesma** renovação: renovar em paralelo reutilizaria o refresh token, e o
 *   backend revogaria a sessão inteira por suspeita de roubo.
 */
export class ClienteApi {
  private token: string | null = null;
  private renovacao: Promise<Sessao | null> | null = null;
  private ouvintesSessao = new Set<(sessao: Sessao | null) => void>();

  /** Avisado a cada sessão nova (login ou renovação) e quando ela termina (`null`). */
  aoMudarSessao(ouvinte: (sessao: Sessao | null) => void): () => void {
    this.ouvintesSessao.add(ouvinte);
    return () => this.ouvintesSessao.delete(ouvinte);
  }

  definirSessao(sessao: Sessao | null): void {
    this.token = sessao?.accessToken ?? null;
    this.ouvintesSessao.forEach((ouvinte) => ouvinte(sessao));
  }

  get autenticado(): boolean {
    return this.token !== null;
  }

  /** Token atual, para quem pede à API sem passar por aqui (o player, nas playlists do vídeo). */
  get tokenAcesso(): string | null {
    return this.token;
  }

  get<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('GET', caminho, opcoes);
  }

  post<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('POST', caminho, { ...opcoes, corpo });
  }

  put<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PUT', caminho, { ...opcoes, corpo });
  }

  patch<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PATCH', caminho, { ...opcoes, corpo });
  }

  delete<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('DELETE', caminho, opcoes);
  }

  /**
   * Renova a sessão pelo cookie de refresh (também usado ao abrir o app, para retomar a sessão).
   * Devolve a sessão nova, ou `null` se não há sessão válida.
   */
  renovar(): Promise<Sessao | null> {
    this.renovacao ??= this.post<Sessao>('/api/auth/refresh', undefined, { semRenovar: true })
      .then((sessao) => {
        this.definirSessao(sessao);
        return sessao;
      })
      .catch(() => {
        this.definirSessao(null);
        return null;
      })
      .finally(() => {
        this.renovacao = null;
      });
    return this.renovacao;
  }

  async requisitar<T>(metodo: Metodo, caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
    let resposta = await this.enviar(metodo, caminho, opcoes);
    if (resposta.status === 401 && !opcoes.semRenovar && this.token !== null) {
      const sessao = await this.renovar();
      if (sessao === null) throw await this.erro(resposta);
      resposta = await this.enviar(metodo, caminho, opcoes);
    }
    if (!resposta.ok) throw await this.erro(resposta);
    if (resposta.status === 204) return undefined as T;
    const tipo = resposta.headers.get('Content-Type') ?? '';
    return (tipo.includes('json') ? await resposta.json() : await resposta.text()) as T;
  }

  private async enviar(
    metodo: Metodo,
    caminho: string,
    opcoes: OpcoesRequisicao,
  ): Promise<Response> {
    const cabecalhos: Record<string, string> = { Accept: 'application/json' };
    if (opcoes.corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
    if (this.token) cabecalhos['Authorization'] = `Bearer ${this.token}`;
    try {
      return await fetch(caminho, {
        method: metodo,
        headers: cabecalhos,
        body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
        credentials: 'same-origin',
        signal: opcoes.sinal,
      });
    } catch (falha) {
      if (falha instanceof DOMException && falha.name === 'AbortError') throw falha;
      throw new ErroApi(0, null);
    }
  }

  private async erro(resposta: Response): Promise<ErroApi> {
    let corpo = null;
    try {
      corpo = await resposta.json();
    } catch {
      // corpo vazio ou não-JSON: fica a mensagem padrão do status
    }
    const tentarEm = Number(resposta.headers.get('Retry-After'));
    return new ErroApi(
      resposta.status,
      corpo,
      Number.isFinite(tentarEm) && tentarEm > 0 ? tentarEm : null,
    );
  }
}

/** Cliente único do app: uma instância só, para a renovação da sessão ser compartilhada. */
export const api = new ClienteApi();
`````

`web/compartilhado/src/ui/estilos.css`:

`````css
/* Telas fora da área logada (cadastro, confirmação, recuperação de senha): cartão centralizado
   com os tokens da @celke/kole-react, nos dois temas. */
.pc-conta {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px 16px;
  background: var(--bg);
  color: var(--ink);
  font-family: var(--font-ui);
}

.pc-conta-cartao {
  width: 100%;
  max-width: 440px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 32px 28px;
  display: grid;
  gap: 18px;
}

.pc-conta-marca {
  font-size: var(--fs-caption);
  color: var(--t2);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin: 0;
}

.pc-conta-titulo {
  font-size: var(--fs-titulo);
  margin: 0;
}

.pc-conta-descricao {
  color: var(--t2);
  margin: 0;
}

.pc-conta-form {
  display: grid;
  gap: 14px;
}

.pc-conta-rodape {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  justify-content: space-between;
  font-size: var(--fs-corpo-2);
}

.pc-conta-rodape a,
.pc-link {
  color: var(--pri);
}

.pc-pagina {
  display: grid;
  gap: 20px;
}

.pc-grade-cards {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
}

/* Telas de gestão (ADM) */
.pc-pilha {
  display: grid;
  gap: 16px;
}

.pc-linha {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

.pc-linha-fim {
  justify-content: flex-end;
}

.pc-filtros {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pc-grade-detalhes {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  align-items: start;
}

.pc-dica {
  color: var(--t2);
  font-size: var(--fs-corpo-2);
  margin: 0;
}

.pc-subtitulo {
  font-size: var(--fs-corpo);
  margin: 8px 0 0;
}

.pc-texto-longo {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 0;
}

.pc-texto-longo :where(p, ul, ol, h1, h2, h3) {
  white-space: normal;
}

.pc-capa {
  max-width: min(360px, 100%);
  border-radius: var(--r-card);
}

.pc-lista-aulas {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 20px;
}

.pc-lista-aulas li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 10px;
}

.pc-aula-titulo {
  background: none;
  border: 0;
  padding: 0;
  font: inherit;
  cursor: pointer;
  text-align: left;
}

.pc-arquivos {
  display: grid;
  gap: 10px;
  border-top: 1px solid var(--border);
  padding-top: 12px;
}

.pc-resposta {
  border: 1px solid var(--border);
  border-radius: var(--r-card);
  padding: 12px 14px;
  display: grid;
  gap: 8px;
}

.pc-resposta-oculta {
  opacity: 0.6;
}

/* Área do aluno */
.pc-player video {
  width: 100%;
  max-height: 70vh;
  background: #000;
  border-radius: var(--r-card);
}

.pc-progresso-turma {
  display: grid;
  gap: 4px;
  max-width: 520px;
}

.pc-navegacao-aulas {
  justify-content: space-between;
}

.pc-formulario-estreito {
  max-width: 420px;
}
`````

`web/compartilhado/src/ui/icones.ts`:

`````ts
import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
  Bookmark,
  CirclePlay,
  GraduationCap,
  LayoutDashboard,
  Pencil,
  Pin,
  PinOff,
  Upload,
  Users,
} from 'lucide-react';

/** Ícones usados pelo nome nas telas e que a @celke/kole-react não registra por padrão. */
registrarIcones({
  award: Award,
  'book-open': BookOpen,
  bookmark: Bookmark,
  'circle-play': CirclePlay,
  'graduation-cap': GraduationCap,
  'layout-dashboard': LayoutDashboard,
  pencil: Pencil,
  pin: Pin,
  'pin-off': PinOff,
  upload: Upload,
  users: Users,
});
`````

`web/e2e/adm/turma.spec.ts`:

`````ts
import { clienteAluno, clienteApi, montarCurso } from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { emailNovo, SENHA_NOVA, stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Matrículas', () => {
  test('matricula, altera o período e cancela', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    const email = await alunoConfirmado(request);

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(email);
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByRole('cell', { name: email })).toBeVisible();

    await page.getByRole('button', { name: 'Alterar período de Aluno do Playwright' }).click();
    await page.getByRole('textbox', { name: 'Fim' }).fill('2099-12-31');
    await page.getByRole('button', { name: 'Salvar período' }).click();
    await expect(page.getByRole('cell', { name: '31/12/2099' })).toBeVisible();

    await page.getByRole('button', { name: 'Cancelar matrícula de Aluno do Playwright' }).click();
    await page
      .getByRole('dialog', { name: 'Cancelar matrícula?' })
      .getByRole('button', { name: 'Cancelar matrícula' })
      .click();
    await expect(page.getByRole('cell', { name: 'Cancelada' })).toBeVisible();
  });

  test('e-mail sem conta é recusado pela API', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(emailNovo());
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByText('Usuário não encontrado.')).toBeVisible();
  });

  test('e-mail inválido e fim antes do início são barrados na tela', async ({
    comoAdmin: page,
    request,
  }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await page.getByRole('button', { name: 'Matricular aluno' }).click();
    await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill('sem-arroba');
    await page.getByRole('textbox', { name: 'Início' }).fill('2026-05-10');
    await page.getByRole('textbox', { name: 'Fim' }).fill('2026-05-01');
    await page.getByRole('button', { name: 'Matricular', exact: true }).click();
    await expect(page.getByText('Informe um e-mail válido.')).toBeVisible();
    await expect(page.getByText('O fim deve ser depois do início.')).toBeVisible();
  });

  test('turma encerrada não recebe matrícula', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    await adm.patch(`/api/adm/turmas/${curso.turmaId}/status`, { status: 'ENCERRADA' });
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=matriculas`);
    await expect(
      page.getByText('A turma está encerrada e não recebe novas matrículas.'),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: 'Matricular aluno' })).toBeDisabled();
  });
});

test.describe('Fórum', () => {
  test('o admin responde, fixa e oculta um tópico do aluno', async ({
    comoAdmin: page,
    request,
  }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
      // Começa ontem: o relógio da VM do Docker (Windows) às vezes volta alguns segundos
      inicio: new Date(Date.now() - 86_400_000).toISOString(),
    });
    const aluno = await clienteAluno(request, email, SENHA_NOVA);
    const titulo = `Dúvida ${stamp()}`;
    await aluno.post(`/api/aluno/turmas/${curso.turmaId}/forum`, {
      titulo,
      corpo: 'Como **funciona**?',
    });

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
    await expect(page.getByRole('cell', { name: 'Aguardando resposta' })).toBeVisible();
    await page.getByRole('cell', { name: titulo }).click();
    const painel = page.getByRole('dialog');
    await expect(painel.getByText('funciona')).toBeVisible();

    await painel.getByRole('button', { name: 'Publicar resposta' }).click();
    await expect(painel.getByText('Escreva a resposta.')).toBeVisible();
    await painel
      .getByRole('textbox', { name: 'Responder como instrutor' })
      .fill('Assim: passo a passo.');
    await painel.getByRole('button', { name: 'Publicar resposta' }).click();
    await expect(painel.getByText('Assim: passo a passo.')).toBeVisible();
    await expect(painel.getByText('Instrutor', { exact: true })).toBeVisible();

    await painel.getByRole('button', { name: 'Fixar' }).click();
    await expect(painel.getByRole('button', { name: 'Desafixar' })).toBeVisible();
    await painel.getByRole('button', { name: 'Ocultar tópico' }).click();
    await expect(painel.getByText('Oculto para os alunos')).toBeVisible();

    // O aluno não vê mais o tópico
    const lista = await aluno.get<{ itens: { titulo: string }[] }>(
      `/api/aluno/turmas/${curso.turmaId}/forum`,
    );
    expect(lista.itens.map((t) => t.titulo)).not.toContain(titulo);
  });

  test('turma sem tópicos', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=forum`);
    await expect(page.getByText('Nenhum tópico')).toBeVisible();
  });
});

test.describe('Configurações da turma', () => {
  test('certificado: percentual fora do limite é barrado e um válido é salvo', async ({
    comoAdmin: page,
    request,
  }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=configuracao`);
    const percentual = page.getByRole('textbox', { name: 'Conclusão mínima (%)' });
    await percentual.fill('150');
    await page.getByRole('button', { name: 'Salvar certificado' }).click();
    await expect(page.getByText('Informe um percentual de 1 a 100.')).toBeVisible();

    await percentual.fill('80');
    await page.getByRole('textbox', { name: 'Carga horária (horas)' }).fill('12');
    await page.getByRole('button', { name: 'Salvar certificado' }).click();
    await expect(page.getByText('Regras do certificado salvas', { exact: true })).toBeVisible();
  });

  test('turma com módulos não pode ser excluída', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}?aba=configuracao`);
    await page.getByRole('button', { name: 'Excluir turma' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('A turma tem módulos. Exclua os módulos ou encerre a turma.'),
    ).toBeVisible();
  });
});
`````

`web/e2e/support/api.ts`:

`````ts
import { expect, type APIRequestContext } from '@playwright/test';
import { ADMIN_SEED, ADM_URL, ALUNO_URL, SENHA_SEED } from './config.ts';
import { stamp } from './dados.ts';

/** Cliente da API com o token de uma conta, para montar dados de teste sem passar pelas telas. */
export interface ClienteApi {
  get<T>(rota: string): Promise<T>;
  post<T>(rota: string, corpo?: unknown): Promise<T>;
  patch<T>(rota: string, corpo: unknown): Promise<T>;
}

export async function clienteApi(
  request: APIRequestContext,
  email = ADMIN_SEED,
  senha = SENHA_SEED,
  base = ADM_URL,
): Promise<ClienteApi> {
  const login = await request.post(`${base}/api/auth/login`, { data: { email, senha } });
  if (!login.ok()) throw new Error(`Login de ${email} recusado: ${login.status()}`);
  const { accessToken } = (await login.json()) as { accessToken: string };
  const headers = { Authorization: `Bearer ${accessToken}` };

  const conferir = async <T>(
    metodo: string,
    rota: string,
    resposta: Awaited<ReturnType<APIRequestContext['get']>>,
  ) => {
    if (!resposta.ok())
      throw new Error(`${metodo} ${rota}: ${resposta.status()} ${await resposta.text()}`);
    const texto = await resposta.text();
    return (texto ? JSON.parse(texto) : undefined) as T;
  };
  return {
    get: async (rota) => conferir('GET', rota, await request.get(`${base}${rota}`, { headers })),
    post: async (rota, corpo) =>
      conferir('POST', rota, await request.post(`${base}${rota}`, { headers, data: corpo ?? {} })),
    patch: async (rota, corpo) =>
      conferir('PATCH', rota, await request.patch(`${base}${rota}`, { headers, data: corpo })),
  };
}

/** Cliente do aluno com os termos vigentes já aceitos (a área do aluno exige). */
export async function clienteAluno(request: APIRequestContext, email: string, senha: string) {
  const aluno = await clienteApi(request, email, senha, ALUNO_URL);
  for (const termo of await aluno.get<{ id: number }[]>('/api/termos/pendentes')) {
    await aluno.post(`/api/termos/${termo.id}/aceite`);
  }
  return aluno;
}

export interface CursoMontado {
  cursoId: number;
  titulo: string;
  turmaId: number;
  moduloId: number;
  aulaId: number;
}

/** Curso com turma ativa, um módulo e uma aula, criado pela API. */
export async function montarCurso(adm: ClienteApi): Promise<CursoMontado> {
  const titulo = `Curso ${stamp()}`;
  const curso = await adm.post<{ id: number }>('/api/adm/cursos', {
    titulo,
    resumo: 'Curso montado pelo teste.',
  });
  const turma = await adm.post<{ id: number }>(`/api/adm/cursos/${curso.id}/turmas`, {
    nome: 'Turma 1',
  });
  await adm.patch(`/api/adm/turmas/${turma.id}/status`, { status: 'ATIVA' });
  const modulo = await adm.post<{ id: number }>(`/api/adm/turmas/${turma.id}/modulos`, {
    titulo: 'Módulo 1',
  });
  const aula = await adm.post<{ id: number }>(`/api/adm/modulos/${modulo.id}/aulas`, {
    titulo: 'Aula 1',
  });
  return { cursoId: curso.id, titulo, turmaId: turma.id, moduloId: modulo.id, aulaId: aula.id };
}

/** Envia o vídeo da aula pelo mesmo caminho do ADM (partes direto ao armazenamento) e espera ficar pronto. */
export async function enviarVideo(
  request: APIRequestContext,
  adm: ClienteApi,
  aulaId: number,
  video: Buffer,
): Promise<void> {
  const rota = `/api/adm/aulas/${aulaId}/video`;
  const envio = await adm.post<{ tamanhoParte: number; partes: { numero: number; url: string }[] }>(
    `${rota}/envio`,
    { nomeArquivo: 'aula.mp4', contentType: 'video/mp4', tamanho: video.length },
  );
  const partes = [];
  for (const { numero, url } of envio.partes) {
    const inicio = (numero - 1) * envio.tamanhoParte;
    const resposta = await request.put(url, {
      data: video.subarray(inicio, inicio + envio.tamanhoParte),
    });
    if (!resposta.ok()) throw new Error(`Parte ${numero} recusada: ${resposta.status()}`);
    partes.push({ numero, etag: resposta.headers()['etag'] });
  }
  await adm.post(`${rota}/envio/conclusao`, { partes });
  await expect
    .poll(async () => (await adm.get<{ status: string }>(rota)).status, {
      message: 'vídeo convertido pelo worker',
      timeout: 120_000,
      intervals: [1_000],
    })
    .toBe('PRONTO');
}
`````

`web/e2e/support/sessoes.ts`:

`````ts
import { test as base, type Page } from '@playwright/test';
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from './config.ts';

/**
 * Sessão nova por teste. O refresh token é rotativo e um token reutilizado revoga a família inteira,
 * então uma sessão gravada (storageState) só serviria para um teste. O login vai pela API do próprio
 * contexto do navegador, que guarda o cookie HttpOnly; ao abrir a página, o app renova e entra.
 */
export async function entrarPelaApi(page: Page, email: string, senha = SENHA_SEED): Promise<void> {
  const resposta = await page.request.post('/api/auth/login', {
    data: { email, senha },
  });
  if (!resposta.ok()) throw new Error(`Login de ${email} recusado: ${resposta.status()}`);
}

export const test = base.extend<{ comoAluno: Page; comoAdmin: Page }>({
  comoAluno: async ({ page }, usar) => {
    await entrarPelaApi(page, ALUNO_SEED);
    await usar(page);
  },
  comoAdmin: async ({ page }, usar) => {
    await entrarPelaApi(page, ADMIN_SEED);
    await usar(page);
  },
});

export { expect } from '@playwright/test';
`````

`web/e2e/support/telas.ts`:

`````ts
import { expect, type Page } from '@playwright/test';

/** Preenche e envia o TelaLogin do kole-react. */
export async function entrarPelaTela(page: Page, email: string, senha: string): Promise<void> {
  await page.getByRole('textbox', { name: 'E-mail', exact: true }).fill(email);
  await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/**
 * Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. Sem destino,
 * espera a página "Meus cursos"; com destino (outra página da área logada), espera a moldura e o
 * fim da consulta dos termos.
 */
export async function aceitarTermosSePedir(page: Page, destino?: 'aula' | 'turma'): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const pronto = destino
    ? page.getByRole('button', { name: /^Usuário: / })
    : page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(pronto).first()).toBeVisible();
  if (destino) await expect(page.getByText('Carregando...', { exact: true })).toBeHidden();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(termos).toBeHidden();
  }
}

/**
 * Escolhe uma opção num CampoSelect da @celke/kole-react pelo rótulo visível. O rótulo é um
 * <label for> apontando para o combobox (um div), o que não dá nome acessível a ele.
 */
export async function escolher(page: Page, rotulo: string, opcao: string): Promise<void> {
  const label = page.locator('label').filter({ hasText: new RegExp(`^${rotulo}[:*]*$`) });
  const id = await label.first().getAttribute('for');
  await page.locator(`[id="${id}"]`).click();
  await page.getByRole('option', { name: opcao, exact: true }).click();
}
`````

`web/package.json`:

`````json
{
  "name": "plataforma-cursos-web",
  "private": true,
  "version": "0.1.0",
  "description": "Frontends da Plataforma de Cursos: área do aluno e ADM (React + @celke/kole-react).",
  "type": "module",
  "scripts": {
    "dev:aluno": "vite --config aluno/vite.config.ts",
    "dev:adm": "vite --config adm/vite.config.ts",
    "build": "tsc -b && vite build --config aluno/vite.config.ts && vite build --config adm/vite.config.ts",
    "typecheck": "tsc -b",
    "lint": "eslint .",
    "format": "prettier --write .",
    "format:check": "prettier --check .",
    "test": "vitest run",
    "e2e": "playwright test",
    "e2e:headed": "playwright test --headed",
    "e2e:ui": "playwright test --ui",
    "e2e:report": "playwright show-report"
  },
  "dependencies": {
    "@celke/kole-react": "github:celkecursos/kole-react#fc87d5c4409e1e65e8c571321c08d56871d2a4d3",
    "hls.js": "^1.7.3",
    "lucide-react": "^1.61.0",
    "react": "^19.3.1",
    "react-dom": "^19.3.1",
    "react-router": "^8.5.0"
  },
  "devDependencies": {
    "@eslint/js": "^10.0.1",
    "@playwright/test": "^1.64.1",
    "@testing-library/jest-dom": "^7.0.1",
    "@testing-library/react": "^16.3.3",
    "@testing-library/user-event": "^14.6.8",
    "@types/node": "^24.13.3",
    "@types/react": "^19.2.18",
    "@types/react-dom": "^19.2.7",
    "@vitejs/plugin-react": "^6.1.2",
    "eslint": "^10.13.0",
    "eslint-plugin-react-hooks": "^7.1.1",
    "globals": "^17.13.0",
    "jsdom": "^30.1.3",
    "prettier": "^3.9.10",
    "typescript": "~6.0.3",
    "typescript-eslint": "^8.72.0",
    "vite": "^8.3.5",
    "vitest": "^5.0.5"
  },
  "engines": {
    "node": ">=22"
  }
}
`````

### Arquivos novos

`web/aluno/src/aula.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { pontoDeRetomada, precisaDeToken } from './paginas/aula/PlayerVideo.tsx';
import { localizarAula, type TurmaAluno } from './tipos.ts';

describe('player', () => {
  const origem = 'https://<sub-aluno>.<dominio>';

  it('manda o token só para as playlists da API, nunca para o armazenamento', () => {
    expect(precisaDeToken('/api/aulas/7/video.m3u8', origem)).toBe(true);
    expect(precisaDeToken('https://<sub-aluno>.<dominio>/api/aulas/7/video/720p.m3u8', origem)).toBe(
      true,
    );
    expect(
      precisaDeToken('https://<sub-media>.<dominio>/midia/hls/seg-001.ts?X-Amz-Signature=x', origem),
    ).toBe(false);
    expect(precisaDeToken('https://<sub-aluno>.<dominio>/assets/app.js', origem)).toBe(false);
  });

  it('retoma de onde parou, mas recomeça se faltava pouco para o fim', () => {
    expect(pontoDeRetomada(42, 300)).toBe(42);
    expect(pontoDeRetomada(0, 300)).toBe(0);
    expect(pontoDeRetomada(297, 300)).toBe(0);
    // Vídeo curto: a margem é 10% da duração
    expect(pontoDeRetomada(1, 3)).toBe(1);
    expect(pontoDeRetomada(2.8, 3)).toBe(0);
  });
});

describe('navegação entre aulas', () => {
  const turma: TurmaAluno = {
    matriculaId: 1,
    turmaId: 2,
    turmaNome: 'T',
    cursoTitulo: 'C',
    cursoSlug: 'c',
    inicio: '2026-01-01T00:00:00Z',
    fim: null,
    modulos: [
      {
        id: 10,
        titulo: 'M1',
        aulas: [
          { id: 1, titulo: 'A1', gratuita: false },
          { id: 2, titulo: 'A2', gratuita: false },
        ],
      },
      { id: 20, titulo: 'M2', aulas: [{ id: 3, titulo: 'A3', gratuita: false }] },
    ],
  };

  it('anterior e próxima atravessam os módulos', () => {
    const meio = localizarAula(turma, 2)!;
    expect(meio.modulo.id).toBe(10);
    expect(meio.anterior?.id).toBe(1);
    expect(meio.proxima?.id).toBe(3);
  });

  it('a primeira não tem anterior e a última não tem próxima', () => {
    expect(localizarAula(turma, 1)!.anterior).toBeNull();
    expect(localizarAula(turma, 3)!.proxima).toBeNull();
  });

  it('aula fora da turma', () => {
    expect(localizarAula(turma, 99)).toBeNull();
  });
});
`````

`web/aluno/src/paginas/CertificadosPagina.tsx`:

`````tsx
import { Alerta, CabecalhoPagina, Carregando, Vazio } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import type { Certificado } from '../tipos.ts';
import { CertificadoEmitido } from './turma/CertificadoAba.tsx';

/** Todos os certificados do aluno, inclusive de matrículas já encerradas. */
export function CertificadosPagina() {
  const certificados = useCarregar(() => api.get<Certificado[]>('/api/aluno/certificados'), []);
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Certificados" icone="award" />
      {certificados.erro && <Alerta severidade="err" mensagem={certificados.erro} />}
      {!certificados.dados && !certificados.erro && (
        <Carregando mensagem="Carregando os certificados..." />
      )}
      {certificados.dados?.length === 0 && (
        <Vazio
          titulo="Nenhum certificado ainda"
          descricao="Conclua as aulas de um curso para emitir o certificado na página dele."
          icone="award"
        />
      )}
      {certificados.dados?.map((c) => (
        <CertificadoEmitido key={c.id} certificado={c} />
      ))}
    </div>
  );
}
`````

`web/aluno/src/paginas/aula/AulaPagina.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CabecalhoPagina,
  Card,
  Carregando,
  useToast,
} from '@celke/kole-react';
import { useCallback } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoAula, caminhoTurma } from '../../caminhos.ts';
import {
  localizarAula,
  type ArquivoApoio,
  type AulaAluno,
  type Progresso,
  type TurmaAluno,
} from '../../tipos.ts';
import { ForumLista } from '../forum/ForumLista.tsx';
import { ListaArquivos } from './ListaArquivos.tsx';
import { PlayerVideo } from './PlayerVideo.tsx';

/** A aula: vídeo com retomada e progresso, vista/revisar, anterior/próxima, arquivos e fórum da aula. */
export function AulaPagina() {
  const { turmaId, aulaId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const aula = useCarregar(() => api.get<AulaAluno>(`/api/aluno/aulas/${aulaId}`), [aulaId]);
  const turma = useCarregar(() => api.get<TurmaAluno>(`/api/aluno/turmas/${turmaId}`), [turmaId]);
  const progresso = useCarregar(
    () => api.get<Progresso>(`/api/aluno/aulas/${aulaId}/progresso`),
    [aulaId],
  );
  const arquivos = useCarregar(
    () => api.get<ArquivoApoio[]>(`/api/aluno/aulas/${aulaId}/arquivos`),
    [aulaId],
  );

  const { definir } = progresso;
  const atualizar = useCallback(
    (campos: Partial<Pick<Progresso, 'posicaoSegundos' | 'vista' | 'revisar'>>, aviso?: string) =>
      api
        .patch<Progresso>(`/api/aluno/aulas/${aulaId}/progresso`, campos)
        .then((novo) => {
          definir(novo);
          if (aviso) toast.ok(aviso);
        })
        .catch((erro: unknown) => {
          // A posição é enviada sozinha de tempos em tempos: só avisa falha de ação do aluno
          if (aviso) toast.err('Não foi possível salvar', mensagemDeErro(erro));
        }),
    [aulaId, definir, toast],
  );

  if (aula.erro && !aula.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a aula" mensagem={aula.erro} />
        <div>
          <Botao
            rotulo="Voltar ao curso"
            aparencia="texto"
            onClick={() => navigate(caminhoTurma(turmaId!))}
          />
        </div>
      </div>
    );
  }
  if (!aula.dados || !progresso.dados) return <Carregando mensagem="Carregando a aula..." />;
  const a = aula.dados;
  const p = progresso.dados;
  const lugar = turma.dados ? localizarAula(turma.dados, a.id) : null;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={a.titulo}
        icone="circle-play"
        categoria={lugar ? `${turma.dados!.cursoTitulo} · ${lugar.modulo.titulo}` : undefined}
        acoes={
          <Botao
            rotulo="Voltar ao curso"
            icone="chevron-left"
            aparencia="texto"
            onClick={() => navigate(caminhoTurma(a.turmaId))}
          />
        }
      />
      <PlayerVideo
        key={a.id}
        aulaId={a.id}
        posicaoInicial={p.posicaoSegundos}
        aoProgredir={(segundos) => void atualizar({ posicaoSegundos: segundos })}
        aoTerminar={() => !p.vista && void atualizar({ vista: true }, 'Aula concluída')}
      />

      <div className="pc-linha">
        {p.vista ? (
          <>
            <Badge cor="ok" icone="check" rotulo="Aula vista" aparencia="suave" />
            <Botao
              rotulo="Desmarcar como vista"
              aparencia="texto"
              tamanho="pequeno"
              onClick={() => void atualizar({ vista: false }, 'Aula desmarcada')}
            />
          </>
        ) : (
          <Botao
            rotulo="Marcar como vista"
            icone="check"
            onClick={() => void atualizar({ vista: true }, 'Aula concluída')}
          />
        )}
        <Botao
          rotulo={p.revisar ? 'Tirar de revisar' : 'Revisar depois'}
          icone="bookmark"
          aparencia="contorno"
          pressionado={p.revisar}
          onClick={() =>
            void atualizar(
              { revisar: !p.revisar },
              p.revisar ? 'Tirada da revisão' : 'Marcada para revisar',
            )
          }
        />
      </div>

      {lugar && (
        <nav className="pc-linha pc-navegacao-aulas" aria-label="Navegação entre aulas">
          <Botao
            rotulo={lugar.anterior ? `Anterior: ${lugar.anterior.titulo}` : 'Primeira aula'}
            icone="chevron-left"
            aparencia="texto"
            disabled={!lugar.anterior}
            onClick={() => lugar.anterior && navigate(caminhoAula(a.turmaId, lugar.anterior.id))}
          />
          <Botao
            rotulo={lugar.proxima ? `Próxima: ${lugar.proxima.titulo}` : 'Última aula'}
            icone="chevron-right"
            posicaoIcone="direita"
            disabled={!lugar.proxima}
            onClick={() => lugar.proxima && navigate(caminhoAula(a.turmaId, lugar.proxima.id))}
          />
        </nav>
      )}

      {a.descricao && (
        <Card titulo="Sobre a aula">
          <p className="pc-texto-longo">{a.descricao}</p>
        </Card>
      )}
      {!!arquivos.dados?.length && (
        <Card>
          <ListaArquivos titulo="Arquivos da aula" arquivos={arquivos.dados} />
        </Card>
      )}
      {turma.dados && (
        <Card titulo="Dúvidas sobre esta aula">
          <ForumLista turmaId={a.turmaId} aulaId={a.id} />
        </Card>
      )}
    </div>
  );
}
`````

`web/aluno/src/paginas/aula/ListaArquivos.tsx`:

`````tsx
import { ItemLista, Lista } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { bytes } from '@compartilhado/ui/formato.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { ArquivoApoio, Download } from '../../tipos.ts';

/**
 * Arquivos de apoio para baixar. O link de download é pedido na hora do clique (a API confere o
 * acesso e devolve uma URL que vale 5 minutos).
 */
export function ListaArquivos({ titulo, arquivos }: { titulo: string; arquivos: ArquivoApoio[] }) {
  const { executar } = useAcao();
  return (
    <section className="pc-arquivos" aria-label={titulo}>
      <h3 className="pc-subtitulo">{titulo}</h3>
      <Lista rotulo={titulo}>
        {arquivos.map((a) => (
          <ItemLista
            key={a.id}
            icone="download"
            titulo={a.titulo}
            onClick={async () => {
              const download = await executar(() =>
                api.get<Download>(`/api/aluno/arquivos/${a.id}/download`),
              );
              if (download) window.location.assign(download.url);
            }}
          >
            {a.nomeArquivo} · {bytes(a.tamanho)}
          </ItemLista>
        ))}
      </Lista>
    </section>
  );
}
`````

`web/aluno/src/paginas/aula/PlayerVideo.tsx`:

`````tsx
import { Alerta, Carregando } from '@celke/kole-react';
import Hls from 'hls.js';
import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';

/** De quantos em quantos segundos de vídeo a posição é enviada à API. */
export const INTERVALO_PROGRESSO = 10;

interface Props {
  aulaId: number;
  /** Onde o aluno parou (retoma dali, se não estiver no finzinho). */
  posicaoInicial: number;
  aoProgredir: (segundos: number) => void;
  aoTerminar: () => void;
}

type Estado =
  | { tipo: 'verificando' }
  | { tipo: 'pronto' }
  | { tipo: 'semVideo' }
  | { tipo: 'erro'; mensagem: string };

/** Só as playlists da API levam o token; os segmentos são URLs pré-assinadas do armazenamento. */
export function precisaDeToken(url: string, origem = window.location.origin): boolean {
  const destino = new URL(url, origem);
  return destino.origin === origem && destino.pathname.startsWith('/api/');
}

/** Ponto de retomada: recomeça do zero se faltava pouco para o fim (5 s, ou 10% em vídeo curto). */
export function pontoDeRetomada(posicao: number, duracao: number): number {
  const margem = Math.min(5, duracao * 0.1);
  return posicao > 0 && posicao < duracao - margem ? posicao : 0;
}

/**
 * Player HLS da aula (hls.js). Antes de criar o player, pede a master playlist pela API: confere o
 * acesso (403/404 viram mensagem) e, se o token tinha vencido, o cliente já renova.
 */
export function PlayerVideo({ aulaId, posicaoInicial, aoProgredir, aoTerminar }: Props) {
  const video = useRef<HTMLVideoElement>(null);
  const [estado, setEstado] = useState<Estado>({ tipo: 'verificando' });
  const master = `/api/aulas/${aulaId}/video.m3u8`;

  const progredir = useEffectEvent(aoProgredir);
  const terminar = useEffectEvent(aoTerminar);
  const posicao = useEffectEvent(() => posicaoInicial);

  useEffect(() => {
    let ativo = true;
    api.get<string>(master).then(
      () => ativo && setEstado({ tipo: 'pronto' }),
      (erro: unknown) => {
        if (!ativo) return;
        if (erro instanceof ErroApi && erro.status === 404) setEstado({ tipo: 'semVideo' });
        else setEstado({ tipo: 'erro', mensagem: mensagemDeErro(erro) });
      },
    );
    return () => {
      ativo = false;
    };
  }, [master]);

  useEffect(() => {
    const elemento = video.current;
    if (estado.tipo !== 'pronto' || !elemento) return;
    if (!Hls.isSupported()) {
      setTimeout(() =>
        setEstado({
          tipo: 'erro',
          mensagem: 'Este navegador não reproduz o vídeo. Use um navegador atualizado.',
        }),
      );
      return;
    }
    let renovou = false;
    const hls = new Hls({
      xhrSetup: (xhr, url) => {
        if (precisaDeToken(url) && api.tokenAcesso)
          xhr.setRequestHeader('Authorization', `Bearer ${api.tokenAcesso}`);
      },
    });
    hls.on(Hls.Events.ERROR, (_evento, dados) => {
      const status = dados.response?.code;
      // Token venceu no meio: renova uma vez e recarrega a playlist
      if (status === 401 && !renovou) {
        renovou = true;
        void api.renovar().then((sessao) => sessao && hls.loadSource(master));
        return;
      }
      if (dados.fatal) {
        setEstado({
          tipo: 'erro',
          mensagem: 'Não foi possível reproduzir o vídeo. Recarregue a página.',
        });
        hls.destroy();
      }
    });
    hls.loadSource(master);
    hls.attachMedia(elemento);

    let ultimoEnvio = -1;
    const enviar = () => {
      const segundos = Math.floor(elemento.currentTime);
      if (segundos !== ultimoEnvio) {
        ultimoEnvio = segundos;
        progredir(segundos);
      }
    };
    const aoCarregar = () => {
      elemento.currentTime = pontoDeRetomada(posicao(), elemento.duration);
    };
    const aoAtualizar = () => {
      if (Math.floor(elemento.currentTime) - ultimoEnvio >= INTERVALO_PROGRESSO) enviar();
    };
    const aoTerminarVideo = () => {
      enviar();
      terminar();
    };
    elemento.addEventListener('loadedmetadata', aoCarregar, { once: true });
    elemento.addEventListener('timeupdate', aoAtualizar);
    elemento.addEventListener('pause', enviar);
    elemento.addEventListener('ended', aoTerminarVideo);
    return () => {
      elemento.removeEventListener('timeupdate', aoAtualizar);
      elemento.removeEventListener('pause', enviar);
      elemento.removeEventListener('ended', aoTerminarVideo);
      if (elemento.currentTime > 0) enviar();
      hls.destroy();
    };
  }, [estado.tipo, master]);

  if (estado.tipo === 'semVideo')
    return <Alerta severidade="info" mensagem="Esta aula não tem vídeo." />;
  if (estado.tipo === 'erro')
    return <Alerta severidade="err" titulo="Vídeo indisponível" mensagem={estado.mensagem} />;
  return (
    <div className="pc-player">
      {estado.tipo === 'verificando' && <Carregando mensagem="Carregando o vídeo..." />}
      {/* Sem legendas: os vídeos das aulas ainda não têm faixa de texto */}
      <video
        ref={video}
        controls
        playsInline
        preload="metadata"
        aria-label="Vídeo da aula"
        hidden={estado.tipo !== 'pronto'}
      />
    </div>
  );
}
`````

`web/aluno/src/paginas/forum/ForumLista.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CampoTextarea,
  CampoTexto,
  Card,
  Carregando,
  ItemLista,
  Lista,
  Paginacao,
  Status,
  Vazio,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { erroObrigatorio, semErros } from '@compartilhado/ui/validacao.ts';
import { caminhoTopico } from '../../caminhos.ts';
import type { Topico, TopicoResumo } from '../../tipos.ts';

const TAMANHO = 10;

interface Props {
  turmaId: number;
  /** Na página da aula: só os tópicos dela, e a pergunta nova fica ligada a ela. */
  aulaId?: number;
}

/** Tópicos do fórum (fixados primeiro) e o formulário para perguntar. */
export function ForumLista({ turmaId, aulaId }: Props) {
  const navigate = useNavigate();
  const [pagina, setPagina] = useState(1);
  const [perguntando, setPerguntando] = useState(false);
  const topicos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (aulaId) filtro.set('aulaId', String(aulaId));
    return api.get<Pagina<TopicoResumo>>(`/api/aluno/turmas/${turmaId}/forum?${filtro}`);
  }, [turmaId, aulaId, pagina]);

  return (
    <div className="pc-pilha">
      <div className="pc-linha pc-linha-fim">
        {!perguntando && (
          <Botao
            rotulo="Fazer uma pergunta"
            icone="message-square"
            onClick={() => setPerguntando(true)}
          />
        )}
      </div>
      {perguntando && (
        <NovaPergunta
          turmaId={turmaId}
          aulaId={aulaId}
          aoCancelar={() => setPerguntando(false)}
          aoPublicar={(topico) => navigate(caminhoTopico(turmaId, topico.id))}
        />
      )}
      {topicos.erro && <Alerta severidade="err" mensagem={topicos.erro} />}
      {!topicos.dados && !topicos.erro && <Carregando mensagem="Carregando o fórum..." />}
      {topicos.dados?.itens.length === 0 && (
        <Vazio
          titulo="Nenhuma pergunta ainda"
          descricao={
            aulaId
              ? 'Tem dúvida sobre esta aula? Pergunte ao instrutor.'
              : 'Tem dúvida? Pergunte ao instrutor.'
          }
          icone="message-square"
        />
      )}
      {!!topicos.dados?.itens.length && (
        <>
          <Lista rotulo="Perguntas do fórum">
            {topicos.dados.itens.map((t) => (
              <ItemLista
                key={t.id}
                titulo={t.titulo}
                icone={t.fixado ? 'pin' : 'message-square'}
                onClick={() => navigate(caminhoTopico(turmaId, t.id))}
                fim={
                  <span className="pc-linha">
                    {t.respondidoInstrutor && <Status rotulo="Respondida" severidade="ok" />}
                    <Badge rotulo={`${t.respostas} resp.`} aparencia="suave" />
                  </span>
                }
              >
                {t.autorNome} · {dataHora(t.criadoEm)}
              </ItemLista>
            ))}
          </Lista>
          {topicos.dados.totalPaginas > 1 && (
            <Paginacao
              total={topicos.dados.total}
              pagina={pagina}
              tamanho={TAMANHO}
              aoMudarPagina={setPagina}
            />
          )}
        </>
      )}
    </div>
  );
}

function NovaPergunta({
  turmaId,
  aulaId,
  aoCancelar,
  aoPublicar,
}: Props & { aoCancelar: () => void; aoPublicar: (t: Topico) => void }) {
  const [titulo, setTitulo] = useState('');
  const [corpo, setCorpo] = useState('');
  const [erros, setErros] = useState<{ titulo?: string; corpo?: string }>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [enviando, setEnviando] = useState(false);

  const publicar = async () => {
    const novos = {
      titulo: erroObrigatorio(titulo, 'Dê um título à pergunta.', 150),
      corpo: erroObrigatorio(corpo, 'Descreva a sua dúvida.', 10_000),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setEnviando(true);
    try {
      aoPublicar(
        await api.post<Topico>(`/api/aluno/turmas/${turmaId}/forum`, {
          titulo: titulo.trim(),
          corpo: corpo.trim(),
          aulaId: aulaId ?? null,
        }),
      );
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ titulo: erro.erroDoCampo('titulo'), corpo: erro.erroDoCampo('corpo') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Card titulo={aulaId ? 'Pergunta sobre esta aula' : 'Nova pergunta'}>
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void publicar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoTexto
          rotulo="Título da pergunta"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={150}
        />
        <CampoTextarea
          rotulo="Sua dúvida"
          valor={corpo}
          aoMudar={setCorpo}
          erro={erros.corpo}
          obrigatorio
          rows={6}
          maxLength={10_000}
          dica="Aceita Markdown (**negrito**, listas com -, `código`)."
        />
        <div className="pc-linha">
          <Botao type="submit" rotulo="Publicar pergunta" icone="send" carregando={enviando} />
          <Botao rotulo="Cancelar" aparencia="texto" onClick={aoCancelar} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/aluno/src/paginas/forum/TopicoPagina.tsx`:

`````tsx
import {
  Alerta,
  Badge,
  Botao,
  CabecalhoPagina,
  Card,
  CampoTextarea,
  Carregando,
} from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { caminhoAula, caminhoTurma } from '../../caminhos.ts';
import type { Topico } from '../../tipos.ts';

/** Uma pergunta do fórum com as respostas; o aluno também pode responder. */
export function TopicoPagina() {
  const { turmaId, topicoId } = useParams();
  const navigate = useNavigate();
  const [resposta, setResposta] = useState('');
  const [erro, setErro] = useState<string>();
  const [enviando, setEnviando] = useState(false);
  const topico = useCarregar(
    () => api.get<Topico>(`/api/aluno/forum/topicos/${topicoId}`),
    [topicoId],
  );

  const responder = async () => {
    if (!resposta.trim()) {
      setErro('Escreva a resposta.');
      return;
    }
    setErro(undefined);
    setEnviando(true);
    try {
      topico.definir(
        await api.post<Topico>(`/api/aluno/forum/topicos/${topicoId}/respostas`, {
          corpo: resposta.trim(),
        }),
      );
      setResposta('');
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('corpo')) || mensagemDeErro(falha));
    } finally {
      setEnviando(false);
    }
  };

  if (topico.erro && !topico.dados) {
    return (
      <div className="pc-pagina">
        <Alerta
          severidade="err"
          titulo="Não foi possível abrir a pergunta"
          mensagem={topico.erro}
        />
      </div>
    );
  }
  if (!topico.dados) return <Carregando mensagem="Carregando a pergunta..." />;
  const t = topico.dados;

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo={t.titulo}
        icone="message-square"
        acoes={
          <>
            {t.aulaId && (
              <Botao
                rotulo="Ir para a aula"
                icone="circle-play"
                aparencia="contorno"
                onClick={() => navigate(caminhoAula(turmaId!, t.aulaId!))}
              />
            )}
            <Botao
              rotulo="Voltar ao fórum"
              icone="chevron-left"
              aparencia="texto"
              onClick={() => navigate(`${caminhoTurma(turmaId!)}?aba=forum`)}
            />
          </>
        }
      >
        {t.autorNome} · {dataHora(t.criadoEm)}
      </CabecalhoPagina>
      <Card>
        {/* HTML já filtrado pela API (Markdown → HTML seguro) */}
        <div className="pc-texto-longo" dangerouslySetInnerHTML={{ __html: t.corpoHtml }} />
      </Card>

      <h2 className="pc-subtitulo">
        {t.respostas.length === 0 ? 'Nenhuma resposta ainda' : `Respostas (${t.respostas.length})`}
      </h2>
      {t.respostas.map((r) => (
        <article key={r.id} className="pc-resposta" aria-label={`Resposta de ${r.autorNome}`}>
          <header className="pc-linha">
            <strong>{r.autorNome}</strong>
            {r.instrutor && <Badge cor="pri" rotulo="Instrutor" aparencia="suave" />}
            <span className="pc-dica">{dataHora(r.criadoEm)}</span>
          </header>
          <div dangerouslySetInnerHTML={{ __html: r.corpoHtml }} />
        </article>
      ))}

      <Card titulo="Responder">
        <form
          noValidate
          className="pc-pilha"
          onSubmit={(evento) => {
            evento.preventDefault();
            void responder();
          }}
        >
          <CampoTextarea
            rotulo="Sua resposta"
            valor={resposta}
            aoMudar={setResposta}
            erro={erro}
            rows={4}
            maxLength={10_000}
          />
          <div>
            <Botao type="submit" rotulo="Publicar resposta" icone="send" carregando={enviando} />
          </div>
        </form>
      </Card>
    </div>
  );
}
`````

`web/aluno/src/paginas/perfil/PerfilPagina.tsx`:

`````tsx
import {
  Alerta,
  Avatar,
  Botao,
  CabecalhoPagina,
  Card,
  CampoEmail,
  CampoSenha,
  CampoTexto,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { enviarParaUrl } from '@compartilhado/api/envio.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { EscolherArquivo } from '@compartilhado/ui/EscolherArquivo.tsx';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import {
  erroConfirmacao,
  erroObrigatorio,
  erroSenhaNova,
  semErros,
} from '@compartilhado/ui/validacao.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { EnvioSimples, Perfil } from '../../tipos.ts';

const FOTO_MAXIMA = 5 * 1024 * 1024;

/** Dados do aluno: nome, foto e troca de senha. */
export function PerfilPagina() {
  const perfil = useCarregar(() => api.get<Perfil>('/api/perfil'), []);
  if (perfil.erro && !perfil.dados) return <Alerta severidade="err" mensagem={perfil.erro} />;
  if (!perfil.dados) return <Carregando mensagem="Carregando o perfil..." />;
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Meu perfil" icone="user" />
      <Dados perfil={perfil.dados} aoMudar={perfil.definir} />
      <Foto perfil={perfil.dados} aoMudar={perfil.definir} />
      <TrocarSenha />
    </div>
  );
}

function Dados({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const [nome, setNome] = useState(perfil.nome);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(nome, 'Informe o seu nome.', 120);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      aoMudar(await api.put<Perfil>('/api/perfil', { nome: nome.trim() }));
      toast.ok('Nome atualizado');
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('nome')) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Dados">
      <form
        noValidate
        className="pc-grade-detalhes"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo="Nome"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={120}
          autoComplete="name"
        />
        <CampoEmail
          rotulo="E-mail"
          valor={perfil.email}
          disabled
          dica="O e-mail da conta não muda."
        />
        <div>
          <Botao type="submit" rotulo="Salvar nome" icone="save" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}

function Foto({ perfil, aoMudar }: { perfil: Perfil; aoMudar: (p: Perfil) => void }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const { executar, executando } = useAcao();

  const enviar = async (arquivo: File) => {
    if (!['image/jpeg', 'image/png'].includes(arquivo.type)) {
      toast.err('Formato não aceito', 'Envie uma foto JPG ou PNG.');
      return;
    }
    if (arquivo.size > FOTO_MAXIMA) {
      toast.err('Foto grande demais', 'A foto pode ter até 5 MB.');
      return;
    }
    const novo = await executar(async () => {
      const envio = await api.post<EnvioSimples>('/api/perfil/foto/envio', {
        contentType: arquivo.type,
        tamanho: arquivo.size,
      });
      await enviarParaUrl(envio.url, arquivo, envio.contentType);
      return api.put<Perfil>('/api/perfil/foto', { chave: envio.chave });
    }, 'Foto atualizada');
    if (novo) aoMudar(novo);
  };

  return (
    <Card titulo="Foto">
      <div className="pc-linha">
        <Avatar nome={perfil.nome} foto={perfil.fotoUrl ?? undefined} tamanho="4rem" />
        <EscolherArquivo
          rotulo={perfil.fotoUrl ? 'Trocar foto' : 'Enviar foto'}
          icone="image"
          aparencia="contorno"
          aceitar="image/jpeg,image/png"
          carregando={executando}
          aoEscolher={(arquivo) => void enviar(arquivo)}
        />
        {perfil.fotoUrl && (
          <Botao
            rotulo="Remover foto"
            aparencia="texto"
            severidade="err"
            onClick={async () => {
              if (!(await confirmar.remover({ mensagem: 'A sua foto será removida.' }))) return;
              const ok = await executar(
                () => api.delete('/api/perfil/foto').then(() => true),
                'Foto removida',
              );
              if (ok) aoMudar({ ...perfil, fotoUrl: null });
            }}
          />
        )}
      </div>
      <p className="pc-dica">JPG ou PNG até 5 MB. A foto é reduzida para 400 × 400.</p>
    </Card>
  );
}

type ErrosSenha = Partial<Record<'atual' | 'nova' | 'confirmacao', string>>;

function TrocarSenha() {
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmacao, setConfirmacao] = useState('');
  const [erros, setErros] = useState<ErrosSenha>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: ErrosSenha = {
      atual: atual ? undefined : 'Informe a senha atual.',
      nova: erroSenhaNova(nova),
      confirmacao: erroConfirmacao(nova, confirmacao),
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await api.put('/api/perfil/senha', { senhaAtual: atual, novaSenha: nova });
      // A API encerrou todas as sessões (o cookie de refresh já foi limpo): recarrega na entrada,
      // o que também descarta o token desta aba
      window.location.replace(`${CAMINHOS.entrar}?senha=alterada`);
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({ atual: erro.erroDoCampo('senhaAtual'), nova: erro.erroDoCampo('novaSenha') });
      }
      setErroGeral(mensagemDeErro(erro));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Card titulo="Trocar senha">
      <form
        noValidate
        className="pc-pilha pc-formulario-estreito"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
        <CampoSenha
          rotulo="Senha atual"
          valor={atual}
          aoMudar={setAtual}
          erro={erros.atual}
          obrigatorio
          autoComplete="current-password"
        />
        <CampoSenha
          rotulo="Nova senha"
          valor={nova}
          aoMudar={setNova}
          erro={erros.nova}
          obrigatorio
          dica="Pelo menos 10 caracteres."
          autoComplete="new-password"
        />
        <CampoSenha
          rotulo="Repita a nova senha"
          valor={confirmacao}
          aoMudar={setConfirmacao}
          erro={erros.confirmacao}
          obrigatorio
          autoComplete="new-password"
        />
        <Alerta
          severidade="info"
          mensagem="Ao trocar a senha, você sai de todos os dispositivos e entra de novo."
        />
        <div>
          <Botao type="submit" rotulo="Trocar senha" icone="key" carregando={salvando} />
        </div>
      </form>
    </Card>
  );
}
`````

`web/aluno/src/paginas/turma/ArquivosModulo.tsx`:

`````tsx
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import type { ArquivoApoio } from '../../tipos.ts';
import { ListaArquivos } from '../aula/ListaArquivos.tsx';

/** Arquivos de apoio do módulo; some quando não há nenhum. */
export function ArquivosModulo({ moduloId }: { moduloId: number }) {
  const arquivos = useCarregar(
    () => api.get<ArquivoApoio[]>(`/api/aluno/modulos/${moduloId}/arquivos`),
    [moduloId],
  );
  if (!arquivos.dados?.length) return null;
  return <ListaArquivos titulo="Arquivos do módulo" arquivos={arquivos.dados} />;
}
`````

`web/aluno/src/paginas/turma/CertificadoAba.tsx`:

`````tsx
import {
  Alerta,
  Botao,
  Card,
  CampoTexto,
  Carregando,
  Detalhes,
  Progresso,
} from '@celke/kole-react';
import { useState } from 'react';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';
import { useAcao } from '@compartilhado/ui/useAcao.ts';
import type { Certificado, Download, SituacaoCertificado } from '../../tipos.ts';

/** Situação do certificado na turma: falta quanto, emitir com o nome completo e baixar o PDF. */
export function CertificadoAba({ turmaId }: { turmaId: number }) {
  const situacao = useCarregar(
    () => api.get<SituacaoCertificado>(`/api/aluno/turmas/${turmaId}/certificado`),
    [turmaId],
  );

  if (situacao.erro && !situacao.dados) return <Alerta severidade="err" mensagem={situacao.erro} />;
  if (!situacao.dados) return <Carregando mensagem="Carregando o certificado..." />;
  const s = situacao.dados;

  if (s.certificado) return <CertificadoEmitido certificado={s.certificado} />;
  if (!s.concluiu) {
    return (
      <Card titulo="Certificado">
        <Progresso
          rotulo="Aulas vistas"
          valor={s.percentualVisto}
          marcos={[s.percentualExigido]}
          mostrarValor="fim"
        />
        <p className="pc-dica">
          O certificado é liberado ao ver {s.percentualExigido}% das aulas. Você já viu{' '}
          {s.percentualVisto}%.
        </p>
      </Card>
    );
  }
  return (
    <EmitirCertificado
      turmaId={turmaId}
      aoEmitir={(certificado) => situacao.definir({ ...s, certificado })}
    />
  );
}

function EmitirCertificado({
  turmaId,
  aoEmitir,
}: {
  turmaId: number;
  aoEmitir: (c: Certificado) => void;
}) {
  const { estado } = useSessao();
  const [nome, setNome] = useState(estado.status === 'autenticado' ? estado.usuario.nome : '');
  const [erro, setErro] = useState<string>();
  const [emitindo, setEmitindo] = useState(false);

  const emitir = async () => {
    const problema = erroObrigatorio(nome, 'Informe o nome completo.', 150);
    setErro(problema);
    if (problema) return;
    setEmitindo(true);
    try {
      aoEmitir(
        await api.post<Certificado>(`/api/aluno/turmas/${turmaId}/certificado`, {
          nomeCompleto: nome.trim(),
        }),
      );
    } catch (falha) {
      setErro(
        (falha instanceof ErroApi && falha.erroDoCampo('nomeCompleto')) || mensagemDeErro(falha),
      );
    } finally {
      setEmitindo(false);
    }
  };

  return (
    <Card titulo="Certificado liberado">
      <form
        noValidate
        className="pc-pilha"
        onSubmit={(evento) => {
          evento.preventDefault();
          void emitir();
        }}
      >
        <Alerta
          severidade="ok"
          mensagem="Parabéns, você concluiu o curso! Confira como o seu nome deve sair no certificado: depois de emitido, ele não muda."
        />
        <CampoTexto
          rotulo="Nome completo no certificado"
          valor={nome}
          aoMudar={setNome}
          erro={erro}
          obrigatorio
          maxLength={150}
        />
        <div>
          <Botao type="submit" rotulo="Emitir certificado" icone="award" carregando={emitindo} />
        </div>
      </form>
    </Card>
  );
}

export function CertificadoEmitido({ certificado }: { certificado: Certificado }) {
  const { executar, executando } = useAcao();
  return (
    <Card titulo="Certificado emitido" aria-label={`Certificado de ${certificado.cursoTitulo}`}>
      <div className="pc-grade-detalhes">
        <Detalhes rotulo="Curso" valor={certificado.cursoTitulo} />
        <Detalhes rotulo="Nome" valor={certificado.nomeCompleto} />
        <Detalhes rotulo="Emitido em" valor={dataHora(certificado.emitidoEm)} />
        <Detalhes rotulo="Código de validação" valor={certificado.codigo} />
        {certificado.cargaHorariaHoras != null && (
          <Detalhes rotulo="Carga horária" valor={`${certificado.cargaHorariaHoras} h`} />
        )}
      </div>
      <div className="pc-linha">
        <Botao
          rotulo="Baixar PDF"
          icone="download"
          carregando={executando}
          onClick={async () => {
            const download = await executar(() =>
              api.get<Download>(`/api/aluno/certificados/${certificado.id}/download`),
            );
            if (download) window.open(download.url, '_blank', 'noopener');
          }}
        />
      </div>
    </Card>
  );
}
`````

`web/aluno/src/paginas/turma/ConteudoTurma.tsx`:

`````tsx
import { Badge, Card, Vazio } from '@celke/kole-react';
import { Link } from 'react-router';
import { caminhoAula } from '../../caminhos.ts';
import type { Progresso, ProgressoTurma, TurmaAluno } from '../../tipos.ts';
import { ArquivosModulo } from './ArquivosModulo.tsx';

/** Módulos e aulas da turma, com o que já foi visto e o que ficou para revisar. */
export function ConteudoTurma({
  turma,
  progresso,
}: {
  turma: TurmaAluno;
  progresso?: ProgressoTurma;
}) {
  const porAula = new Map<number, Progresso>((progresso?.aulas ?? []).map((p) => [p.aulaId, p]));

  if (turma.modulos.length === 0) {
    return (
      <Vazio
        titulo="Conteúdo em preparação"
        descricao="As aulas aparecem aqui assim que forem publicadas."
        icone="layers"
      />
    );
  }

  return (
    <div className="pc-pilha">
      {turma.modulos.map((modulo, indice) => (
        <Card
          key={modulo.id}
          titulo={`${indice + 1}. ${modulo.titulo}`}
          aria-label={`Módulo ${modulo.titulo}`}
        >
          <ol className="pc-lista-aulas" aria-label={`Aulas de ${modulo.titulo}`}>
            {modulo.aulas.map((aula) => {
              const p = porAula.get(aula.id);
              return (
                <li key={aula.id}>
                  <Link className="pc-link" to={caminhoAula(turma.turmaId, aula.id)}>
                    {aula.titulo}
                  </Link>
                  {p?.vista && <Badge cor="ok" icone="check" rotulo="Vista" aparencia="suave" />}
                  {p?.revisar && <Badge cor="warn" rotulo="Revisar" aparencia="suave" />}
                </li>
              );
            })}
          </ol>
          <ArquivosModulo moduloId={modulo.id} />
        </Card>
      ))}
    </div>
  );
}
`````

`web/aluno/src/paginas/turma/ForumTurma.tsx`:

`````tsx
import type { TurmaAluno } from '../../tipos.ts';
import { ForumLista } from '../forum/ForumLista.tsx';

export function ForumTurma({ turma }: { turma: TurmaAluno }) {
  return <ForumLista turmaId={turma.turmaId} />;
}
`````

`web/aluno/src/paginas/turma/TurmaPagina.tsx`:

`````tsx
import { Aba, Abas, Alerta, Carregando, Progresso } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { ProgressoTurma, TurmaAluno } from '../../tipos.ts';
import { CertificadoAba } from './CertificadoAba.tsx';
import { ConteudoTurma } from './ConteudoTurma.tsx';
import { ForumTurma } from './ForumTurma.tsx';

/** A turma do aluno: conteúdo com o progresso, fórum e certificado. A aba fica no endereço (?aba=). */
export function TurmaPagina() {
  const { turmaId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const turma = useCarregar(() => api.get<TurmaAluno>(`/api/aluno/turmas/${turmaId}`), [turmaId]);
  const progresso = useCarregar(
    () => api.get<ProgressoTurma>(`/api/aluno/turmas/${turmaId}/progresso`),
    [turmaId],
  );

  if (turma.erro && !turma.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o curso" mensagem={turma.erro} />
      </div>
    );
  }
  if (!turma.dados) return <Carregando mensagem="Carregando o curso..." />;
  const t = turma.dados;
  const p = progresso.dados;

  return (
    <div className="pc-pagina">
      <Abas
        titulo={t.cursoTitulo}
        identidade={t.turmaNome}
        aoVoltar={() => navigate(CAMINHOS.inicio)}
        ativa={parametros.get('aba') ?? 'conteudo'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções do curso"
      >
        <Aba valor="conteudo" rotulo="Conteúdo" icone="layers">
          <div className="pc-pilha">
            {p && (
              <div className="pc-progresso-turma">
                <Progresso
                  rotulo="Progresso no curso"
                  valor={p.percentual}
                  mostrarValor="fim"
                  cor={p.percentual === 100 ? 'ok' : 'pri'}
                />
                <span className="pc-dica">
                  {p.aulasVistas} de {p.totalAulas} aulas vistas
                </span>
              </div>
            )}
            <ConteudoTurma turma={t} progresso={p} />
          </div>
        </Aba>
        <Aba valor="forum" rotulo="Fórum" icone="message-square">
          <ForumTurma turma={t} />
        </Aba>
        <Aba valor="certificado" rotulo="Certificado" icone="award">
          <CertificadoAba turmaId={t.turmaId} />
        </Aba>
      </Abas>
    </div>
  );
}
`````

`web/aluno/src/tipos.ts`:

`````ts
/** Respostas da API da área do aluno (/api/aluno, /api/perfil), espelhando os records do backend. */

export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FiltroMeusCursos = 'ATIVOS' | 'ARQUIVADOS' | 'EXPIRADOS';

export interface MeuCurso {
  matriculaId: number;
  turmaId: number;
  cursoTitulo: string;
  cursoSlug: string;
  turmaNome: string;
  capaUrl: string | null;
  inicio: string;
  fim: string | null;
  situacao: SituacaoMatricula;
  arquivada: boolean;
}

export interface AulaResumo {
  id: number;
  titulo: string;
  gratuita: boolean;
}

export interface ModuloAluno {
  id: number;
  titulo: string;
  aulas: AulaResumo[];
}

export interface TurmaAluno {
  matriculaId: number;
  turmaId: number;
  turmaNome: string;
  cursoTitulo: string;
  cursoSlug: string;
  inicio: string;
  fim: string | null;
  modulos: ModuloAluno[];
}

export interface AulaAluno {
  id: number;
  turmaId: number;
  titulo: string;
  descricao: string | null;
  gratuita: boolean;
}

export interface Progresso {
  aulaId: number;
  posicaoSegundos: number;
  vista: boolean;
  vistaEm: string | null;
  revisar: boolean;
}

export interface ProgressoTurma {
  turmaId: number;
  totalAulas: number;
  aulasVistas: number;
  percentual: number;
  aulas: Progresso[];
}

export interface ArquivoApoio {
  id: number;
  aulaId: number | null;
  moduloId: number | null;
  titulo: string;
  nomeArquivo: string;
  contentType: string;
  tamanho: number;
  criadoEm: string;
}

export interface Download {
  url: string;
  expiraEm: string;
}

export interface Certificado {
  id: number;
  turmaId: number;
  codigo: string;
  nomeCompleto: string;
  cursoTitulo: string;
  turmaNome: string;
  cargaHorariaHoras: number | null;
  emitidoEm: string;
}

export interface SituacaoCertificado {
  percentualVisto: number;
  percentualExigido: number;
  concluiu: boolean;
  certificado: Certificado | null;
}

export interface TopicoResumo {
  id: number;
  turmaId: number;
  aulaId: number | null;
  titulo: string;
  autorNome: string;
  fixado: boolean;
  respondidoInstrutor: boolean;
  respostas: number;
  criadoEm: string;
}

export interface Anexo {
  id: number;
  url: string;
}

export interface Resposta {
  id: number;
  corpoHtml: string;
  autorNome: string;
  instrutor: boolean;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Perfil {
  id: number;
  nome: string;
  email: string;
  papeis: string[];
  fotoUrl: string | null;
}

export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
}

/** Aula com o módulo e a posição dela na turma, para anterior/próxima. */
export interface AulaNaTurma {
  aula: AulaResumo;
  modulo: ModuloAluno;
  anterior: AulaResumo | null;
  proxima: AulaResumo | null;
}

export function localizarAula(turma: TurmaAluno, aulaId: number): AulaNaTurma | null {
  const todas = turma.modulos.flatMap((modulo) => modulo.aulas.map((aula) => ({ aula, modulo })));
  const indice = todas.findIndex((item) => item.aula.id === aulaId);
  if (indice < 0) return null;
  const { aula, modulo } = todas[indice]!;
  return {
    aula,
    modulo,
    anterior: todas[indice - 1]?.aula ?? null,
    proxima: todas[indice + 1]?.aula ?? null,
  };
}
`````

`web/compartilhado/src/ui/EscolherArquivo.tsx`:

`````tsx
import { Botao, type BotaoProps } from '@celke/kole-react';
import { useId, useRef } from 'react';

interface Props extends Pick<
  BotaoProps,
  'rotulo' | 'icone' | 'carregando' | 'aparencia' | 'severidade'
> {
  /** Tipos aceitos (atributo `accept`). */
  aceitar: string;
  aoEscolher: (arquivo: File) => void;
}

/** Botão que abre a escolha de arquivo do sistema. O input fica oculto, mas com rótulo acessível. */
export function EscolherArquivo({
  aceitar,
  aoEscolher,
  rotulo = 'Escolher arquivo',
  ...botao
}: Props) {
  const entrada = useRef<HTMLInputElement>(null);
  const id = useId();
  return (
    <>
      <input
        ref={entrada}
        id={id}
        type="file"
        accept={aceitar}
        aria-label={rotulo}
        hidden
        onChange={(evento) => {
          const arquivo = evento.target.files?.[0];
          evento.target.value = '';
          if (arquivo) aoEscolher(arquivo);
        }}
      />
      <Botao rotulo={rotulo} {...botao} onClick={() => entrada.current?.click()} />
    </>
  );
}
`````

`web/compartilhado/src/ui/useAcao.ts`:

`````ts
import { useToast } from '@celke/kole-react';
import { useCallback, useState } from 'react';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

/**
 * Executa uma alteração na API mostrando o resultado em aviso (toast). Devolve o retorno, ou
 * `undefined` se falhou (o erro já foi mostrado).
 */
export function useAcao() {
  const toast = useToast();
  const [executando, setExecutando] = useState(false);

  const executar = useCallback(
    async <T>(acao: () => Promise<T>, sucesso?: string): Promise<T | undefined> => {
      setExecutando(true);
      try {
        const resultado = await acao();
        if (sucesso) toast.ok(sucesso);
        return resultado;
      } catch (erro) {
        toast.err('Não foi possível concluir', mensagemDeErro(erro));
        return undefined;
      } finally {
        setExecutando(false);
      }
    },
    [toast],
  );

  return { executar, executando };
}
`````

`web/e2e/aluno/estudo.spec.ts`:

`````ts
import { readFileSync } from 'node:fs';
import { request as criarRequest, type APIRequestContext, type Page } from '@playwright/test';
import { TXT, VIDEO_3S } from '../support/amostras.ts';
import {
  clienteAluno,
  clienteApi,
  enviarVideo,
  montarCurso,
  type ClienteApi,
  type CursoMontado,
} from '../support/api.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir } from '../support/telas.ts';

/**
 * Estudo pelo aluno: um curso com duas aulas (a primeira com vídeo e arquivo de apoio), montado uma
 * vez pela API do ADM. Cada teste usa um aluno novo, matriculado, para o progresso não se misturar.
 */
let contexto: APIRequestContext;
let adm: ClienteApi;
let curso: CursoMontado;
let segundaAula: number;

test.beforeAll(async () => {
  test.setTimeout(180_000);
  contexto = await criarRequest.newContext();
  adm = await clienteApi(contexto);
  curso = await montarCurso(adm);
  segundaAula = (
    await adm.post<{ id: number }>(`/api/adm/modulos/${curso.moduloId}/aulas`, { titulo: 'Aula 2' })
  ).id;
  await enviarVideo(contexto, adm, curso.aulaId, readFileSync(VIDEO_3S));
  const envio = await adm.post<{ chave: string; url: string; contentType: string }>(
    `/api/adm/aulas/${curso.aulaId}/arquivos/envio`,
    { contentType: TXT.mimeType, tamanho: TXT.buffer.length },
  );
  await contexto.put(envio.url, {
    data: TXT.buffer,
    headers: { 'Content-Type': envio.contentType },
  });
  await adm.post(`/api/adm/aulas/${curso.aulaId}/arquivos`, {
    chave: envio.chave,
    titulo: 'Roteiro',
    nomeArquivo: TXT.name,
  });
});

test.afterAll(async () => {
  await contexto.dispose();
});

/** Aluno novo, matriculado na turma e com a sessão aberta na página. */
async function alunoMatriculado(page: Page, request: APIRequestContext) {
  const email = await alunoConfirmado(request);
  // Começa ontem: o relógio da VM do Docker (Windows) às vezes volta alguns segundos
  await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
    email,
    formaPagamento: 'CORTESIA',
    inicio: new Date(Date.now() - 86_400_000).toISOString(),
  });
  await entrarPelaApi(page, email, SENHA_NOVA);
  return email;
}

async function abrirAula(page: Page, aulaId = curso.aulaId) {
  await page.goto(`/turmas/${curso.turmaId}/aulas/${aulaId}`);
  await aceitarTermosSePedir(page, 'aula');
}

test('do curso à aula: assiste o vídeo até o fim e o progresso da turma sobe', async ({
  page,
  request,
}) => {
  await alunoMatriculado(page, request);
  await page.goto('/');
  await aceitarTermosSePedir(page);
  await page
    .getByLabel(curso.titulo, { exact: true })
    .getByRole('button', { name: 'Continuar' })
    .click();
  await expect(page.getByText('0 de 2 aulas vistas')).toBeVisible();

  await page.getByRole('link', { name: 'Aula 1' }).click();
  const video = page.getByLabel('Vídeo da aula');
  await expect(video).toBeVisible();
  const concluida = page.waitForResponse(
    (r) =>
      r.url().endsWith(`/aulas/${curso.aulaId}/progresso`) &&
      r.request().method() === 'PATCH' &&
      r.request().postData()!.includes('vista'),
  );
  await video.evaluate((v: HTMLVideoElement) => {
    v.muted = true;
    return v.play();
  });
  expect((await concluida).status()).toBe(200);
  await expect(page.getByText('Aula vista')).toBeVisible();

  await page.getByRole('button', { name: 'Voltar ao curso' }).click();
  await expect(page.getByText('1 de 2 aulas vistas')).toBeVisible();
  await expect(
    page.getByRole('list', { name: 'Aulas de Módulo 1' }).getByText('Vista'),
  ).toBeVisible();
});

test('retoma o vídeo de onde parou', async ({ page, request }) => {
  const email = await alunoMatriculado(page, request);
  const aluno = await clienteAluno(request, email, SENHA_NOVA);
  await aluno.patch(`/api/aluno/aulas/${curso.aulaId}/progresso`, { posicaoSegundos: 1 });

  await abrirAula(page);
  const video = page.getByLabel('Vídeo da aula');
  await expect
    .poll(() => video.evaluate((v: HTMLVideoElement) => v.currentTime))
    .toBeGreaterThanOrEqual(1);
});

test('marca como vista, desmarca e guarda para revisar', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page, segundaAula);
  await expect(page.getByText('Esta aula não tem vídeo.')).toBeVisible();

  await page.getByRole('button', { name: 'Marcar como vista' }).click();
  await expect(page.getByText('Aula vista')).toBeVisible();
  await page.getByRole('button', { name: 'Desmarcar como vista' }).click();
  await expect(page.getByRole('button', { name: 'Marcar como vista' })).toBeVisible();

  await page.getByRole('button', { name: 'Revisar depois' }).click();
  await expect(page.getByRole('button', { name: 'Tirar de revisar' })).toBeVisible();
  await page.getByRole('button', { name: 'Voltar ao curso' }).click();
  await expect(
    page.getByRole('list', { name: 'Aulas de Módulo 1' }).getByText('Revisar'),
  ).toBeVisible();
});

test('anterior e próxima levam pelas aulas da turma', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  await expect(page.getByRole('button', { name: 'Primeira aula' })).toBeDisabled();
  await page.getByRole('button', { name: 'Próxima: Aula 2' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 2' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Última aula' })).toBeDisabled();
  await page.getByRole('button', { name: 'Anterior: Aula 1' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 1' })).toBeVisible();
});

test('baixa o arquivo de apoio da aula', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  const arquivos = page.getByRole('region', { name: 'Arquivos da aula' });
  const link = page.waitForResponse(
    (r) => r.url().includes('/api/aluno/arquivos/') && r.url().endsWith('/download'),
  );
  // O navegador vai direto ao armazenamento pela URL assinada que a API devolveu
  const baixado = page.waitForEvent('download');
  await arquivos.getByRole('button', { name: /^Roteiro/ }).click();
  expect((await link).status()).toBe(200);
  const download = await baixado;
  expect(download.url()).toContain('media.localhost');
  expect(readFileSync(await download.path(), 'utf8')).toContain(
    'Roteiro da aula gerado pelo teste.',
  );
});

test('pergunta no fórum da aula, valida e responde', async ({ page, request }) => {
  await alunoMatriculado(page, request);
  await abrirAula(page);
  const forum = page
    .getByRole('region', { name: 'Dúvidas sobre esta aula' })
    .or(page.locator('body'));
  await forum.getByRole('button', { name: 'Fazer uma pergunta' }).click();
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByText('Dê um título à pergunta.')).toBeVisible();
  await expect(page.getByText('Descreva a sua dúvida.')).toBeVisible();

  await page.getByRole('textbox', { name: 'Título da pergunta' }).fill('Não entendi o exemplo');
  await page
    .getByRole('textbox', { name: 'Sua dúvida' })
    .fill('Pode explicar o **segundo** passo?');
  await page.getByRole('button', { name: 'Publicar pergunta' }).click();
  await expect(page.getByRole('heading', { name: 'Não entendi o exemplo' })).toBeVisible();
  await expect(page.getByText('segundo')).toBeVisible();
  await expect(page.getByText('Nenhuma resposta ainda')).toBeVisible();

  await page.getByRole('textbox', { name: 'Sua resposta' }).fill('Complementando a pergunta.');
  await page.getByRole('button', { name: 'Publicar resposta' }).click();
  await expect(page.getByText('Respostas (1)')).toBeVisible();

  await page.getByRole('button', { name: 'Ir para a aula' }).click();
  await expect(page.getByRole('heading', { name: 'Aula 1' })).toBeVisible();
  await expect(page.getByText('Não entendi o exemplo')).toBeVisible();
});

test('certificado: bloqueado até concluir, depois emitido, baixado e validado', async ({
  page,
  request,
}) => {
  const email = await alunoMatriculado(page, request);
  await page.goto(`/turmas/${curso.turmaId}?aba=certificado`);
  await aceitarTermosSePedir(page, 'turma');
  await expect(page.getByText(/O certificado é liberado ao ver \d+% das aulas/)).toBeVisible();

  const aluno = await clienteAluno(request, email, SENHA_NOVA);
  for (const aula of [curso.aulaId, segundaAula]) {
    await aluno.patch(`/api/aluno/aulas/${aula}/progresso`, { vista: true });
  }
  await page.reload();
  const nome = page.getByRole('textbox', { name: 'Nome completo no certificado' });
  await nome.fill('');
  await nome.blur();
  await expect(page.getByText('Campo obrigatório')).toBeVisible();
  await page.getByRole('button', { name: 'Emitir certificado' }).click();
  await expect(page.getByText('Informe o nome completo.')).toBeVisible();

  await nome.fill('Aluna Playwright da Silva');
  await page.getByRole('button', { name: 'Emitir certificado' }).click();
  const cartao = page
    .getByRole('region', { name: `Certificado de ${curso.titulo}` })
    .or(page.getByText('Certificado emitido'));
  await expect(cartao.first()).toBeVisible();
  const codigo = await page.getByText('Código de validação').locator('..').innerText();

  const pdf = page.waitForResponse(
    (r) => r.url().includes('/api/aluno/certificados/') && r.url().endsWith('/download'),
  );
  const novaAba = page.waitForEvent('popup');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  expect((await pdf).status()).toBe(200);
  await (await novaAba).close();

  await page.goto('/certificados');
  await expect(page.getByText('Aluna Playwright da Silva')).toBeVisible();
  const valor = codigo.replace('Código de validação', '').trim();
  const validacao = await request.get(`/api/certificados/${valor}`);
  expect(validacao.ok()).toBe(true);
  expect((await validacao.json()).nomeCompleto).toBe('Aluna Playwright da Silva');
});

test.describe('Caminhos infelizes', () => {
  test('turma sem matrícula não abre', async ({ comoAluno: page }) => {
    await page.goto(`/turmas/${curso.turmaId}`);
    await aceitarTermosSePedir(page, 'turma');
    await expect(page.getByText('Não foi possível abrir o curso')).toBeVisible();
    await expect(page.getByText(/não tem acesso a esta turma/)).toBeVisible();
  });

  test('aula paga sem matrícula não abre', async ({ comoAluno: page }) => {
    await page.goto(`/turmas/${curso.turmaId}/aulas/${curso.aulaId}`);
    await aceitarTermosSePedir(page, 'aula');
    await expect(page.getByText('Não foi possível abrir a aula')).toBeVisible();
  });

  test('matrícula que ainda não começou fica em "Começa em breve", sem acesso', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/matriculas`, {
      email,
      formaPagamento: 'CORTESIA',
      inicio: '2099-01-01T03:00:00Z',
    });
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/');
    await aceitarTermosSePedir(page);
    const card = page.getByLabel(curso.titulo, { exact: true });
    await expect(card.getByText('Começa em breve')).toBeVisible();
    await expect(card.getByRole('button', { name: 'Continuar' })).toHaveCount(0);
  });

  test('arquiva e desarquiva um curso', async ({ page, request }) => {
    await alunoMatriculado(page, request);
    await page.goto('/');
    await aceitarTermosSePedir(page);
    await page
      .getByLabel(curso.titulo, { exact: true })
      .getByRole('button', { name: 'Arquivar' })
      .click();
    await expect(page.getByText('Você ainda não tem cursos')).toBeVisible();
    await page.getByRole('tab', { name: 'Arquivados' }).click();
    await page
      .getByLabel(curso.titulo, { exact: true })
      .getByRole('button', { name: 'Desarquivar' })
      .click();
    await expect(page.getByText('Nenhum curso aqui')).toBeVisible();
    await page.getByRole('tab', { name: 'Ativos' }).click();
    await expect(page.getByLabel(curso.titulo, { exact: true })).toBeVisible();
  });
});
`````

`web/e2e/aluno/perfil.spec.ts`:

`````ts
import { PNG } from '../support/amostras.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { SENHA_NOVA } from '../support/dados.ts';
import { entrarPelaApi, expect, test } from '../support/sessoes.ts';
import { aceitarTermosSePedir, entrarPelaTela } from '../support/telas.ts';

test.describe('Meu perfil', () => {
  test('troca o nome e a foto', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');
    await expect(page.getByRole('textbox', { name: 'E-mail' })).toHaveValue(email);

    const nome = page.getByRole('textbox', { name: 'Nome', exact: true });
    await nome.fill('Nome Trocado no Perfil');
    await page.getByRole('button', { name: 'Salvar nome' }).click();
    await expect(page.getByText('Nome atualizado', { exact: true })).toBeVisible();

    await page.getByLabel('Enviar foto').setInputFiles(PNG);
    await expect(page.getByText('Foto atualizada', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Remover foto' })).toBeVisible();
  });

  test('foto em formato errado não é enviada', async ({ page, request }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');
    await page
      .getByLabel('Enviar foto')
      .setInputFiles({ ...PNG, name: 'foto.gif', mimeType: 'image/gif' });
    await expect(page.getByText('Envie uma foto JPG ou PNG.')).toBeVisible();
  });

  test('troca a senha: a atual errada é recusada; com a certa, entra de novo com a nova', async ({
    page,
    request,
  }) => {
    const email = await alunoConfirmado(request);
    await entrarPelaApi(page, email, SENHA_NOVA);
    await page.goto('/perfil');
    await aceitarTermosSePedir(page, 'turma');

    await page.getByRole('button', { name: 'Trocar senha' }).click();
    await expect(page.getByText('Informe a senha atual.')).toBeVisible();

    await page.getByRole('textbox', { name: 'Senha atual' }).fill('senha-atual-errada');
    await page
      .getByRole('textbox', { name: 'Nova senha', exact: true })
      .fill('outra-senha-forte-1');
    await page.getByRole('textbox', { name: 'Repita a nova senha' }).fill('outra-senha-forte-1');
    const recusada = page.waitForResponse('**/api/perfil/senha');
    await page.getByRole('button', { name: 'Trocar senha' }).click();
    expect((await recusada).ok()).toBe(false);
    await expect(page.getByRole('alert').first()).toBeVisible();

    await page.getByRole('textbox', { name: 'Senha atual' }).fill(SENHA_NOVA);
    await page.getByRole('button', { name: 'Trocar senha' }).click();
    await expect(page).toHaveURL(/\/entrar\?senha=alterada/);
    await expect(page.getByText('Senha alterada. Entre com a nova senha.')).toBeVisible();

    await entrarPelaTela(page, email, 'outra-senha-forte-1');
    await expect(
      page
        .getByRole('heading', { name: 'Meus cursos' })
        .or(page.getByRole('heading', { name: 'Termos atualizados' })),
    ).toBeVisible();
  });
});
`````

### Dependências do front

Depois de gravar os arquivos, dentro de `web/`, rode `npm install` para instalar as dependências do `package.json` e gerar o `package-lock.json`.

## Antes de executar

Fase 12 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/web-aluno
```

## Verificação

Dentro de `web/`:

```bash
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build
```

Na raiz do repositório, com a stack completa:

```bash
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env up -d --build --wait
(cd backend && ./mvnw verify -Pe2e)
(cd web && npm run e2e)
docker compose -f infra/compose.prod.yml -f infra/compose.e2e.yml --env-file infra/e2e.env down -v
```

- Tudo passa; confira as telas da fase em `http://aluno.localhost:8088` e `http://adm.localhost:8088` (contas do seed em `infra/e2e.env`).
- Com tudo verde, as tasks de `.sdd/tasks/area-do-aluno-v1.0/` passam para `Status: Concluída` e os critérios de aceite
  são marcados (`- [x]`).

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md .sdd/
git commit -m "feat(aluno): adicionar a área do aluno"
git push -u origin feature/web-aluno
```

Abra o Pull Request de `feature/web-aluno` para `homolog`.
