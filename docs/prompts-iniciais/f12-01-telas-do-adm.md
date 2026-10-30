# Telas do ADM

**Fase:** 12
**Branch:** `feature/web-adm`

> **Configuração do projeto:** troque os marcadores `<dominio>`, `<sub-site>`, `<sub-aluno>`,
> `<sub-adm>`, `<sub-media>` e `<dono-github>` pelos valores de `docs/configuracao.md` (criado no
> primeiro prompt) em todo o conteúdo deste prompt, inclusive nos testes. Se o arquivo não existir,
> pergunte esses valores ao usuário antes de começar.

## Prompt

### Especificação e tasks (SDD)

Antes do código, registre a funcionalidade no SDD: crie a spec e as tasks abaixo com **exatamente** este conteúdo (troque os marcadores pelos valores de `docs/configuracao.md`). As tasks nascem com `Status: Pronta`; ao fim da **Verificação**, passam para `Concluída`.

`.sdd/specs/painel-administrativo.spec.md`:

`````markdown
---
feature: painel-administrativo
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Painel administrativo

## 1. Resumo Executivo

O administrador gerencia toda a plataforma pelas telas de `<sub-adm>.<dominio>`: cursos, turmas, módulos e
aulas, vídeos, capas, arquivos, matrículas, fórum, certificados e termos, sem precisar chamar a API à
mão.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Todas as telas do painel |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE listar, criar, alterar e mudar o status de cursos, turmas, módulos e aulas,
  com paginação e abas.
- **RF-02** — O sistema DEVE reordenar módulos e aulas com botões subir e descer, acessíveis por teclado,
  uma troca por clique.
- **RF-03** — O sistema DEVE enviar o vídeo em partes direto ao armazenamento, com progresso e
  cancelamento, e acompanhar o processamento a cada 3 segundos, com reprocessar e remover.
- **RF-04** — O sistema DEVE enviar a capa do curso e os arquivos de apoio de aulas e módulos.
- **RF-05** — O sistema DEVE gerenciar as matrículas da turma: matricular por e-mail, alterar o período e
  cancelar.
- **RF-06** — O sistema DEVE moderar o fórum: responder como instrutor, fixar e ocultar.
- **RF-07** — O sistema DEVE definir a regra do certificado da turma e publicar novas versões dos termos.
- **RF-08** — O sistema DEVE mostrar os erros da API na tela (endereço repetido, exclusão com filhos,
  formato recusado, matrícula sem conta ou em turma encerrada).

## 4. Modelo de Domínio

Usa as entidades das specs `catalogo-de-cursos`, `envio-de-video`, `arquivos-de-apoio`,
`matricula-e-acesso`, `forum`, `certificado` e `perfil-e-termos`.

## 5. Estados e Fluxos

- **Curso completo:** criar curso → turma → módulos → aulas → vídeo até "pronto" → capa → publicar.

## 6. Contratos de Interface

| Tela | Faz |
|---|---|
| Cursos | lista, filtro por status, formulário, capa |
| Curso → turmas → módulos → aulas | conteúdo, ordem, status |
| Aula | vídeo, arquivos |
| Turma | matrículas, certificado, fórum |
| Termos | versões e publicação |

## 7. Casos de Borda

- Envio de vídeo cancelado: as partes são descartadas.
- Navegar para fora durante o envio: o envio é interrompido.
- Erro de validação: mostrado junto ao campo.

## 8. Premissas Validadas

- O painel é usado por poucas pessoas e fica restrito por IP em produção.

## 9. Riscos

- **Envio grande interrompido:** progresso visível e possibilidade de reenviar.
`````

`.sdd/tasks/painel-administrativo-v1.0/README.md`:

`````markdown
# Tasks — Painel administrativo (v1.0)

Spec: [`.sdd/specs/painel-administrativo.spec.md`](../../specs/painel-administrativo.spec.md) · versão 1.0 · prompt do tutorial `docs/prompts-iniciais/f12-01-telas-do-adm.md`.

| Ordem | ID | Título | Nível | Depende de |
|---|---|---|---|---|
| 1 | [ADM-001](ADM-001-conteudo.md) | Cursos, turmas, módulos e aulas | Pleno | — |
| 2 | [ADM-002](ADM-002-video-e-arquivos.md) | Vídeo, capa e arquivos | Sênior | — |
| 3 | [ADM-003](ADM-003-turma.md) | Matrículas, fórum, certificado e termos | Pleno | — |
| 4 | [ADM-004](ADM-004-fiacao.md) | Fiação do painel | Pleno | ADM-001, ADM-002, ADM-003 |

## Grafo

```
ADM-001 (início)
ADM-002 (início)
ADM-003 (início)
ADM-001 + ADM-002 + ADM-003 → ADM-004
```

## RF → tasks

| RF | Tasks |
|---|---|
| RF-01 | ADM-001, ADM-004 |
| RF-02 | ADM-001 |
| RF-03 | ADM-002 |
| RF-04 | ADM-002 |
| RF-05 | ADM-003 |
| RF-06 | ADM-003 |
| RF-07 | ADM-003 |
| RF-08 | ADM-001 |

## Resumo por nível

- Pleno: 3
- Sênior: 1
`````

`.sdd/tasks/painel-administrativo-v1.0/ADM-001-conteudo.md`:

`````markdown
# ADM-001 — Cursos, turmas, módulos e aulas

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-01, RF-02, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/adm/src/paginas/cursos/CursosPagina.tsx`
- `web/adm/src/paginas/cursos/FormCurso.tsx`
- `web/adm/src/paginas/curso/CursoPagina.tsx`
- `web/adm/src/paginas/curso/DadosCursoAba.tsx`
- `web/adm/src/paginas/curso/TurmasAba.tsx`
- `web/adm/src/paginas/turma/ConteudoAba.tsx`

## Descrição técnica

`Tabela`, `Form`, `Paginacao`, `Abas`; subir/descer acessível por teclado.

## Critérios de aceite

- [ ] `web/e2e/adm/conteudo.spec.ts` verde

## Armadilhas

Mostrar erro da API só no console.
`````

`.sdd/tasks/painel-administrativo-v1.0/ADM-002-video-e-arquivos.md`:

`````markdown
# ADM-002 — Vídeo, capa e arquivos

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-03, RF-04
- **Nível:** Sênior
- **Agente:** dev-senior
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/adm/src/paginas/aula/AulaPagina.tsx`
- `web/adm/src/paginas/aula/VideoAula.tsx`
- `web/compartilhado/src/api/envio.ts` — envio multipart com progresso e cancelamento
- `web/compartilhado/src/ui/EscolherArquivo.tsx`

## Descrição técnica

Partes direto ao Garage com progresso; consulta do status a cada 3 s.

## Critérios de aceite

- [ ] `web/e2e/adm/curso-completo.spec.ts` (vídeo curto até pronto) verde

## Armadilhas

Não cancelar o envio ao sair da página.
`````

`.sdd/tasks/painel-administrativo-v1.0/ADM-003-turma.md`:

`````markdown
# ADM-003 — Matrículas, fórum, certificado e termos

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-05, RF-06, RF-07
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** —

## Arquivos a criar/alterar

- `web/adm/src/paginas/turma/TurmaPagina.tsx`
- `web/adm/src/paginas/turma/MatriculasAba.tsx`
- `web/adm/src/paginas/turma/ForumAba.tsx`
- `web/adm/src/paginas/turma/ConfiguracaoTurmaAba.tsx`
- `web/adm/src/paginas/termos/TermosPagina.tsx`

## Critérios de aceite

- [ ] `web/e2e/adm/turma.spec.ts` e `termos.spec.ts` verdes
`````

`.sdd/tasks/painel-administrativo-v1.0/ADM-004-fiacao.md`:

`````markdown
# ADM-004 — Fiação do painel

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-01
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Pronta
- **Depende de:** ADM-001, ADM-002, ADM-003

## Arquivos a criar/alterar

- `web/adm/src/rotas.tsx`

## Descrição técnica

Rotas `cursos`, `cursos/:cursoId`, turmas, aulas e `termos`.

## Critérios de aceite

- [ ] `npm test` e Playwright verdes
- [ ] Task só de fiação: não tem teste próprio; a verificação é a suíte inteira passando.
`````

Acrescente ao fim da tabela do índice `.sdd/README.md` a linha:

```text
| [painel-administrativo](specs/painel-administrativo.spec.md) | 1.0 | [v1.0](tasks/painel-administrativo-v1.0/README.md) | `f12-01-telas-do-adm.md` |
```

Vamos construir as telas de gestão do ADM sobre a base dos fronts. Não faça commit.

O que esta etapa entrega:

- **Cursos, turmas, módulos e aulas** com `Tabela`, `Form`, `Paginacao` e `Abas`; reordenação com os
  botões subir/descer (acessível por teclado, uma troca por clique).
- **Vídeo da aula**: envio em partes direto ao Garage com progresso e cancelamento, status do
  processamento (consulta a cada 3 s), reprocessar e remover.
- **Capa do curso, arquivos de apoio** (aula e módulo), **matrículas** (período e cancelamento),
  **moderação do fórum** (responder, fixar, ocultar), **certificado da turma** e **termos**.
- **Testes** Playwright (caminho feliz e infeliz): o admin cria um curso completo e envia um vídeo curto
  até "pronto"; validações na tela, conflitos da API (slug repetido, exclusão com filhos), formatos
  recusados, matrícula sem conta e em turma encerrada, moderação do fórum.

Crie ou substitua os arquivos abaixo com **exatamente** o conteúdo indicado.

### Arquivos alterados (substitua o conteúdo inteiro)

`web/adm/src/caminhos.ts`:

`````ts
export const CAMINHOS = {
  entrar: '/entrar',
  inicio: '/',
  cursos: '/cursos',
  termos: '/termos',
} as const;

export const caminhoCurso = (cursoId: number | string) => `/cursos/${cursoId}`;

export const caminhoTurma = (cursoId: number | string, turmaId: number | string) =>
  `/cursos/${cursoId}/turmas/${turmaId}`;

export const caminhoAula = (
  cursoId: number | string,
  turmaId: number | string,
  moduloId: number | string,
  aulaId: number | string,
) => `/cursos/${cursoId}/turmas/${turmaId}/modulos/${moduloId}/aulas/${aulaId}`;
`````

`web/adm/src/layout/LayoutAdm.tsx`:

`````tsx
import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export const NAV: SecaoNav[] = [
  { itens: [{ rotulo: 'Painel', icone: 'house', rota: CAMINHOS.inicio }] },
  {
    rotulo: 'Conteúdo',
    itens: [
      { rotulo: 'Cursos', icone: 'graduation-cap', rota: CAMINHOS.cursos },
      { rotulo: 'Termos', icone: 'file-text', rota: CAMINHOS.termos },
    ],
  },
];

export function LayoutAdm() {
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
      usuario={{ nome, cargo: 'Administrador' }}
      menuUsuario={[{ id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true }]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <Outlet />
    </Estrutura>
  );
}
`````

`web/adm/src/paginas/PainelPagina.tsx`:

`````tsx
import { BoasVindas, Botao } from '@celke/kole-react';
import { useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';

export function PainelPagina() {
  const { estado } = useSessao();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';
  return (
    <BoasVindas nome={nome} cargo="Administrador">
      <Botao rotulo="Cursos" icone="graduation-cap" onClick={() => navigate(CAMINHOS.cursos)} />
      <Botao
        rotulo="Termos"
        icone="file-text"
        aparencia="contorno"
        onClick={() => navigate(CAMINHOS.termos)}
      />
    </BoasVindas>
  );
}
`````

`web/adm/src/rotas.tsx`:

`````tsx
import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAdm } from './layout/LayoutAdm.tsx';
import { SemPermissao } from './layout/SemPermissao.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CursoPagina } from './paginas/curso/CursoPagina.tsx';
import { CursosPagina } from './paginas/cursos/CursosPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { PainelPagina } from './paginas/PainelPagina.tsx';
import { TermosPagina } from './paginas/termos/TermosPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar} papel="ADMIN" semPermissao={<SemPermissao />}>
        <LayoutAdm />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <PainelPagina /> },
      { path: 'cursos', element: <CursosPagina /> },
      { path: 'cursos/:cursoId', element: <CursoPagina /> },
      { path: 'cursos/:cursoId/turmas/:turmaId', element: <TurmaPagina /> },
      {
        path: 'cursos/:cursoId/turmas/:turmaId/modulos/:moduloId/aulas/:aulaId',
        element: <AulaPagina />,
      },
      { path: 'termos', element: <TermosPagina /> },
    ],
  },
  { path: '*', element: <SemPermissao titulo="Página não encontrada" /> },
]);
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
`````

`web/compartilhado/src/ui/icones.ts`:

`````ts
import { registrarIcones } from '@celke/kole-react';
import {
  Award,
  BookOpen,
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

`web/e2e/adm/acesso.spec.ts`:

`````ts
import { ADMIN_SEED, ALUNO_SEED, SENHA_SEED } from '../support/config.ts';
import { emailNovo } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';
import { entrarPelaTela } from '../support/telas.ts';

test.describe('ADM', () => {
  test('com a sessão do admin, abre o painel com o menu da administração', async ({
    comoAdmin: page,
  }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Usuário: Admin do e2e' })).toContainText(
      'Administrador',
    );
  });

  test('o admin entra pela tela e volta para onde ia', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
    await entrarPelaTela(page, ADMIN_SEED, SENHA_SEED);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('link', { name: 'Painel' })).toBeVisible();
  });

  test('um aluno é barrado na entrada e a sessão dele é encerrada', async ({ page }) => {
    await page.goto('/entrar');
    const sair = page.waitForResponse('**/api/auth/logout');
    await entrarPelaTela(page, ALUNO_SEED, SENHA_SEED);
    await sair;

    await expect(page.getByText('Acesso restrito a administradores.')).toBeVisible();
    await page.goto('/');
    await expect(page).toHaveURL(/\/entrar\?voltar=/);
  });

  test('um aluno com sessão aberta vê o acesso restrito, não o painel', async ({
    comoAluno: page,
  }) => {
    await page.goto('/');
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito a administradores' }),
    ).toBeVisible();
    await expect(page.getByRole('link', { name: 'Painel' })).toBeHidden();

    await page.getByRole('button', { name: 'Entrar com outra conta' }).click();
    await expect(page).toHaveURL(/\/entrar(\?|$)/);
  });

  test('senha errada mostra o erro', async ({ page }) => {
    await page.goto('/entrar');
    await entrarPelaTela(page, emailNovo(), 'senha-errada-123');
    await expect(page.getByText('E-mail ou senha inválidos')).toBeVisible();
  });

  test('a API do ADM recusa o aluno mesmo sem passar pela tela', async ({ request }) => {
    const login = await request.post('/api/auth/login', {
      data: { email: ALUNO_SEED, senha: SENHA_SEED },
    });
    const { accessToken } = (await login.json()) as { accessToken: string };
    const resposta = await request.get('/api/adm/cursos', {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(resposta.status()).toBe(403);
  });
});
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

/** Se houver termos vigentes sem aceite (outra jornada pode ter publicado), aceita. */
export async function aceitarTermosSePedir(page: Page): Promise<void> {
  const termos = page.getByRole('heading', { name: 'Termos atualizados' });
  const cursos = page.getByRole('heading', { name: 'Meus cursos' });
  await expect(termos.or(cursos)).toBeVisible();
  if (await termos.isVisible()) {
    await page.getByRole('checkbox', { name: 'Li e concordo com os termos acima' }).check();
    await page.getByRole('button', { name: 'Aceitar e continuar' }).click();
    await expect(cursos).toBeVisible();
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

### Arquivos novos

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
import { EscolherArquivo } from './EscolherArquivo.tsx';
import { useAcao } from './useAcao.ts';

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

`web/adm/src/componentes/DialogoTexto.tsx`:

`````tsx
import { Botao, CampoTexto, Dialogo } from '@celke/kole-react';
import { useState } from 'react';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio } from '@compartilhado/ui/validacao.ts';

interface Props {
  titulo: string;
  rotulo: string;
  inicial?: string;
  maximo: number;
  /** Campo da API cujo erro aparece no campo (ex.: `nome`, `titulo`). */
  campo: string;
  rotuloSalvar?: string;
  aoSalvar: (valor: string) => Promise<void>;
  aoFechar: () => void;
}

/** Diálogo com um campo de texto só: nome de turma, título de módulo. */
export function DialogoTexto({
  titulo,
  rotulo,
  inicial = '',
  maximo,
  campo,
  rotuloSalvar = 'Salvar',
  aoSalvar,
  aoFechar,
}: Props) {
  const [valor, setValor] = useState(inicial);
  const [erro, setErro] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const problema = erroObrigatorio(valor, `Informe ${rotulo.toLowerCase()}.`, maximo);
    setErro(problema);
    if (problema) return;
    setSalvando(true);
    try {
      await aoSalvar(valor.trim());
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo(campo)) || mensagemDeErro(falha));
    } finally {
      setSalvando(false);
    }
  };

  return (
    <Dialogo
      aberto
      titulo={titulo}
      tamanho="pequeno"
      aoFechar={aoFechar}
      rodape={
        <>
          <Botao rotulo="Cancelar" aparencia="texto" onClick={aoFechar} />
          <Botao
            rotulo={rotuloSalvar}
            icone="save"
            carregando={salvando}
            type="submit"
            form="pc-dialogo-texto"
          />
        </>
      }
    >
      <form
        noValidate
        id="pc-dialogo-texto"
        onSubmit={(evento) => {
          evento.preventDefault();
          void salvar();
        }}
      >
        <CampoTexto
          rotulo={rotulo}
          valor={valor}
          aoMudar={setValor}
          erro={erro}
          obrigatorio
          maxLength={maximo}
          autoFocus
        />
      </form>
    </Dialogo>
  );
}
`````

`web/adm/src/componentes/EscolherArquivo.tsx`:

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

`web/adm/src/componentes/useAcao.ts`:

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
import { useAcao } from '../../componentes/useAcao.ts';
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
import { EscolherArquivo } from '../../componentes/EscolherArquivo.tsx';
import { useAcao } from '../../componentes/useAcao.ts';
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

`web/adm/src/paginas/curso/CursoPagina.tsx`:

`````tsx
import { Aba, Abas, Alerta, Carregando } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { CAMINHOS } from '../../caminhos.ts';
import type { Curso } from '../../tipos.ts';
import { DadosCursoAba } from './DadosCursoAba.tsx';
import { TurmasAba } from './TurmasAba.tsx';

/** Um curso: dados (com capa e status) e as turmas. A aba ativa fica no endereço (?aba=). */
export function CursoPagina() {
  const { cursoId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const curso = useCarregar(() => api.get<Curso>(`/api/adm/cursos/${cursoId}`), [cursoId]);

  if (curso.erro && !curso.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir o curso" mensagem={curso.erro} />
      </div>
    );
  }
  if (!curso.dados) return <Carregando mensagem="Carregando o curso..." />;

  return (
    <div className="pc-pagina">
      <Abas
        titulo={curso.dados.titulo}
        aoVoltar={() => navigate(CAMINHOS.cursos)}
        ativa={parametros.get('aba') ?? 'dados'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções do curso"
      >
        <Aba valor="dados" rotulo="Dados" icone="file-text">
          <DadosCursoAba curso={curso.dados} aoMudar={curso.definir} />
        </Aba>
        <Aba valor="turmas" rotulo="Turmas" icone="users">
          <TurmasAba curso={curso.dados} />
        </Aba>
      </Abas>
    </div>
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
import { EscolherArquivo } from '../../componentes/EscolherArquivo.tsx';
import { useAcao } from '../../componentes/useAcao.ts';
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
import { useAcao } from '../../componentes/useAcao.ts';
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

`web/adm/src/paginas/cursos/CursosPagina.tsx`:

`````tsx
import { Botao, CabecalhoPagina, CampoSelect, Status, Tabela, useToast } from '@celke/kole-react';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import type { Pagina } from '@compartilhado/api/tipos.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { caminhoCurso } from '../../caminhos.ts';
import { opcoes, STATUS_CURSO, type Curso, type StatusCurso } from '../../tipos.ts';
import { FormCurso } from './FormCurso.tsx';

const TAMANHO = 20;

export function CursosPagina() {
  const navigate = useNavigate();
  const toast = useToast();
  const [pagina, setPagina] = useState(1);
  const [status, setStatus] = useState<StatusCurso | null>(null);
  const [novo, setNovo] = useState(false);

  const cursos = useCarregar(() => {
    const filtro = new URLSearchParams({ page: String(pagina - 1), size: String(TAMANHO) });
    if (status) filtro.set('status', status);
    return api.get<Pagina<Curso>>(`/api/adm/cursos?${filtro}`);
  }, [pagina, status]);

  return (
    <div className="pc-pagina">
      <CabecalhoPagina
        titulo="Cursos"
        icone="graduation-cap"
        acoes={<Botao rotulo="Novo curso" icone="plus" onClick={() => setNovo(true)} />}
      />
      <div className="pc-filtros">
        <CampoSelect
          rotulo="Status"
          variante="inline"
          placeholder="Todos"
          botaoLimpar
          opcoes={opcoes(STATUS_CURSO)}
          valor={status}
          aoMudar={(valor) => {
            setStatus(valor);
            setPagina(1);
          }}
        />
      </div>
      <Tabela<Curso>
        rotulo="Cursos"
        colunas={[
          { campo: 'titulo', rotulo: 'Título', destaque: true },
          { campo: 'slug', rotulo: 'Endereço' },
          {
            campo: 'status',
            rotulo: 'Status',
            render: (c) => (
              <Status
                rotulo={STATUS_CURSO[c.status].rotulo}
                severidade={STATUS_CURSO[c.status].severidade}
              />
            ),
          },
          { campo: 'atualizadoEm', rotulo: 'Atualizado em', formato: (v) => dataHora(v as string) },
        ]}
        registros={cursos.dados?.itens ?? []}
        carregando={cursos.carregando && !cursos.dados}
        atualizando={cursos.carregando && !!cursos.dados}
        erro={cursos.erro}
        aoRecarregar={cursos.recarregar}
        vazioTitulo="Nenhum curso"
        vazioDescricao={status ? 'Nenhum curso com este status.' : 'Cadastre o primeiro curso.'}
        vazioIcone="graduation-cap"
        aoClicarLinha={(c) => navigate(caminhoCurso(c.id))}
        paginacao={{
          total: cursos.dados?.total ?? 0,
          pagina,
          tamanho: TAMANHO,
          aoMudarPagina: setPagina,
        }}
      />
      {novo && (
        <FormCurso
          aberto
          aoFechar={() => setNovo(false)}
          aoSalvar={async (dados) => {
            const curso = await api.post<Curso>('/api/adm/cursos', dados);
            toast.ok('Curso cadastrado', curso.titulo);
            navigate(caminhoCurso(curso.id));
          }}
        />
      )}
    </div>
  );
}
`````

`web/adm/src/paginas/cursos/FormCurso.tsx`:

`````tsx
import { Alerta, CampoTextarea, CampoTexto, Form, FormSecao } from '@celke/kole-react';
import { useState } from 'react';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { erroObrigatorio, semErros } from '@compartilhado/ui/validacao.ts';
import type { Curso } from '../../tipos.ts';

export interface DadosCurso {
  titulo: string;
  slug: string | null;
  resumo: string;
  descricao: string | null;
}

type Erros = Partial<Record<keyof DadosCurso, string>>;

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

interface Props {
  aberto: boolean;
  /** Sem curso, é cadastro novo. */
  curso?: Curso;
  aoSalvar: (dados: DadosCurso) => Promise<void>;
  aoFechar: () => void;
}

/** Cadastro e edição dos dados de um curso, em diálogo. */
export function FormCurso({ aberto, curso, aoSalvar, aoFechar }: Props) {
  const [titulo, setTitulo] = useState(curso?.titulo ?? '');
  const [slug, setSlug] = useState(curso?.slug ?? '');
  const [resumo, setResumo] = useState(curso?.resumo ?? '');
  const [descricao, setDescricao] = useState(curso?.descricao ?? '');
  const [erros, setErros] = useState<Erros>({});
  const [erroGeral, setErroGeral] = useState<string>();
  const [salvando, setSalvando] = useState(false);

  const salvar = async () => {
    const novos: Erros = {
      titulo: erroObrigatorio(titulo, 'Informe o título.', 120),
      resumo: erroObrigatorio(resumo, 'Informe o resumo.', 300),
      slug:
        slug.trim() && !SLUG.test(slug.trim())
          ? 'Use só letras minúsculas, números e hífens.'
          : undefined,
    };
    setErros(novos);
    setErroGeral(undefined);
    if (!semErros(novos)) return;
    setSalvando(true);
    try {
      await aoSalvar({
        titulo: titulo.trim(),
        slug: slug.trim() || null,
        resumo: resumo.trim(),
        descricao: descricao.trim() || null,
      });
    } catch (erro) {
      if (erro instanceof ErroApi) {
        setErros({
          titulo: erro.erroDoCampo('titulo'),
          slug: erro.erroDoCampo('slug'),
          resumo: erro.erroDoCampo('resumo'),
          descricao: erro.erroDoCampo('descricao'),
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
      aberto={aberto}
      entidade="curso"
      modo={curso ? 'editar' : 'novo'}
      aoSalvar={() => void salvar()}
      aoCancelar={aoFechar}
      salvando={salvando}
      tamanho="grande"
    >
      {erroGeral && <Alerta severidade="err" mensagem={erroGeral} />}
      <FormSecao legenda="Dados do curso" colunas={2}>
        <CampoTexto
          rotulo="Título"
          valor={titulo}
          aoMudar={setTitulo}
          erro={erros.titulo}
          obrigatorio
          maxLength={120}
        />
        <CampoTexto
          rotulo="Endereço (slug)"
          valor={slug}
          aoMudar={setSlug}
          erro={erros.slug}
          dica="Em branco, é gerado a partir do título."
        />
        <CampoTextarea
          rotulo="Resumo"
          valor={resumo}
          aoMudar={setResumo}
          erro={erros.resumo}
          obrigatorio
          maxLength={300}
          contador
          className="lp-col-full"
        />
        <CampoTextarea
          rotulo="Descrição"
          valor={descricao}
          aoMudar={setDescricao}
          erro={erros.descricao}
          rows={6}
          dica="Aparece na página do curso."
          className="lp-col-full"
        />
      </FormSecao>
    </Form>
  );
}
`````

`web/adm/src/paginas/termos/TermosPagina.tsx`:

`````tsx
import {
  Aba,
  Abas,
  Alerta,
  Botao,
  CabecalhoPagina,
  Card,
  CampoTextarea,
  Carregando,
  useConfirmacao,
  useToast,
} from '@celke/kole-react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi, mensagemDeErro } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { dataHora } from '@compartilhado/ui/formato.ts';
import { TIPOS_TERMO, type Termo, type TipoTermo } from '../../tipos.ts';

/** Termos de uso e política de privacidade: histórico de versões e publicação de uma nova. */
export function TermosPagina() {
  const [parametros, setParametros] = useSearchParams();
  const tipo = (parametros.get('tipo') as TipoTermo | null) ?? 'TERMOS_USO';

  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Termos" icone="file-text">
        Ao publicar uma versão nova, todos os usuários precisam aceitá-la para continuar usando a
        área do aluno.
      </CabecalhoPagina>
      <Abas
        ativa={tipo}
        aoMudar={(t) => setParametros({ tipo: t }, { replace: true })}
        rotulo="Tipo de termo"
      >
        {(Object.keys(TIPOS_TERMO) as TipoTermo[]).map((t) => (
          <Aba key={t} valor={t} rotulo={TIPOS_TERMO[t]}>
            <VersoesTermo tipo={t} />
          </Aba>
        ))}
      </Abas>
    </div>
  );
}

function VersoesTermo({ tipo }: { tipo: TipoTermo }) {
  const toast = useToast();
  const confirmar = useConfirmacao();
  const [conteudo, setConteudo] = useState('');
  const [erro, setErro] = useState<string>();
  const [publicando, setPublicando] = useState(false);
  const versoes = useCarregar(() => api.get<Termo[]>(`/api/adm/termos?tipo=${tipo}`), [tipo]);

  const publicar = async () => {
    if (!conteudo.trim()) {
      setErro('Escreva o texto da nova versão.');
      return;
    }
    setErro(undefined);
    const sim = await confirmar({
      titulo: `Publicar nova versão de ${TIPOS_TERMO[tipo]}?`,
      mensagem: 'Todos os usuários vão precisar aceitar o texto novo para continuar.',
      rotuloAceitar: 'Publicar',
    });
    if (!sim) return;
    setPublicando(true);
    try {
      const termo = await api.post<Termo>('/api/adm/termos', { tipo, conteudo: conteudo.trim() });
      toast.ok('Versão publicada', `${TIPOS_TERMO[tipo]} — versão ${termo.versao}`);
      setConteudo('');
      versoes.recarregar();
    } catch (falha) {
      setErro((falha instanceof ErroApi && falha.erroDoCampo('conteudo')) || mensagemDeErro(falha));
    } finally {
      setPublicando(false);
    }
  };

  const [atual, ...anteriores] = versoes.dados ?? [];
  return (
    <div className="pc-pilha">
      {versoes.erro && <Alerta severidade="err" mensagem={versoes.erro} />}
      {versoes.carregando && !versoes.dados && <Carregando mensagem="Carregando as versões..." />}
      {versoes.dados && !atual && (
        <Alerta severidade="info" mensagem="Nenhuma versão publicada ainda." />
      )}
      {atual && (
        <Card
          titulo={`Versão vigente: ${atual.versao}`}
          subtitulo={`Publicada em ${dataHora(atual.publicadoEm)}`}
        >
          <div
            className="pc-texto-longo"
            dangerouslySetInnerHTML={{ __html: atual.conteudoHtml }}
          />
        </Card>
      )}
      {anteriores.length > 0 && (
        <Card titulo={`Versões anteriores (${anteriores.length})`} recolhivel recolhidoInicial>
          <div className="pc-pilha">
            {anteriores.map((t) => (
              <Card key={t.id} titulo={`Versão ${t.versao}`} subtitulo={dataHora(t.publicadoEm)}>
                <div
                  className="pc-texto-longo"
                  dangerouslySetInnerHTML={{ __html: t.conteudoHtml }}
                />
              </Card>
            ))}
          </div>
        </Card>
      )}
      <Card titulo="Nova versão">
        <form
          noValidate
          className="pc-pilha"
          onSubmit={(evento) => {
            evento.preventDefault();
            void publicar();
          }}
        >
          <CampoTextarea
            rotulo="Texto da nova versão"
            valor={conteudo}
            aoMudar={setConteudo}
            erro={erro}
            rows={12}
            maxLength={100_000}
            dica="Aceita Markdown (títulos com #, listas com -, **negrito**)."
          />
          <div>
            <Botao type="submit" rotulo="Publicar versão" icone="send" carregando={publicando} />
          </div>
        </form>
      </Card>
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
import { useAcao } from '../../componentes/useAcao.ts';
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
import { useAcao } from '../../componentes/useAcao.ts';
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
import { useAcao } from '../../componentes/useAcao.ts';
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
import { useAcao } from '../../componentes/useAcao.ts';
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

`web/adm/src/paginas/turma/TurmaPagina.tsx`:

`````tsx
import { Aba, Abas, Alerta, Carregando, Status } from '@celke/kole-react';
import { useNavigate, useParams, useSearchParams } from 'react-router';
import { api } from '@compartilhado/api/cliente.ts';
import { ErroApi } from '@compartilhado/api/erros.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import { caminhoCurso } from '../../caminhos.ts';
import { STATUS_TURMA, type Curso, type Turma } from '../../tipos.ts';
import { ConfiguracaoTurmaAba } from './ConfiguracaoTurmaAba.tsx';
import { ConteudoAba } from './ConteudoAba.tsx';
import { ForumAba } from './ForumAba.tsx';
import { MatriculasAba } from './MatriculasAba.tsx';

export interface ContextoTurma {
  curso: Curso;
  turma: Turma;
}

/** Uma turma: conteúdo, matrículas, fórum e configurações. A API não tem GET da turma: vem da lista do curso. */
export function TurmaPagina() {
  const { cursoId, turmaId } = useParams();
  const navigate = useNavigate();
  const [parametros, setParametros] = useSearchParams();
  const contexto = useCarregar(async (): Promise<ContextoTurma> => {
    const [curso, turmas] = await Promise.all([
      api.get<Curso>(`/api/adm/cursos/${cursoId}`),
      api.get<Turma[]>(`/api/adm/cursos/${cursoId}/turmas`),
    ]);
    const turma = turmas.find((t) => String(t.id) === turmaId);
    if (!turma) throw new ErroApi(404, { detail: 'Turma não encontrada neste curso.' });
    return { curso, turma };
  }, [cursoId, turmaId]);

  if (contexto.erro && !contexto.dados) {
    return (
      <div className="pc-pagina">
        <Alerta severidade="err" titulo="Não foi possível abrir a turma" mensagem={contexto.erro} />
      </div>
    );
  }
  if (!contexto.dados) return <Carregando mensagem="Carregando a turma..." />;
  const { curso, turma } = contexto.dados;
  const voltar = () => navigate(`${caminhoCurso(curso.id)}?aba=turmas`);

  return (
    <div className="pc-pagina">
      <Abas
        titulo={turma.nome}
        identidade={curso.titulo}
        aoVoltar={voltar}
        acoes={
          <Status
            rotulo={STATUS_TURMA[turma.status].rotulo}
            severidade={STATUS_TURMA[turma.status].severidade}
          />
        }
        ativa={parametros.get('aba') ?? 'conteudo'}
        aoMudar={(aba) => setParametros({ aba }, { replace: true })}
        rotulo="Seções da turma"
      >
        <Aba valor="conteudo" rotulo="Conteúdo" icone="layers">
          <ConteudoAba curso={curso} turma={turma} />
        </Aba>
        <Aba valor="matriculas" rotulo="Matrículas" icone="users">
          <MatriculasAba turma={turma} />
        </Aba>
        <Aba valor="forum" rotulo="Fórum" icone="message-square">
          <ForumAba turma={turma} />
        </Aba>
        <Aba valor="configuracao" rotulo="Configurações" icone="settings">
          <ConfiguracaoTurmaAba
            turma={turma}
            aoMudar={(nova) => contexto.definir({ curso, turma: nova })}
            aoExcluir={voltar}
          />
        </Aba>
      </Abas>
    </div>
  );
}
`````

`web/adm/src/tipos.ts`:

`````ts
import type { Severidade } from '@celke/kole-react';

/** Respostas da API de gestão (/api/adm), espelhando os records do backend. */

export type StatusCurso = 'RASCUNHO' | 'PUBLICADO' | 'ARQUIVADO';
export type StatusTurma = 'PREPARACAO' | 'ATIVA' | 'ENCERRADA';
export type StatusConteudo = 'ATIVO' | 'INATIVO';
export type StatusVideo = 'AGUARDANDO_ENVIO' | 'PENDENTE' | 'PROCESSANDO' | 'PRONTO' | 'ERRO';
export type SituacaoMatricula = 'AGUARDANDO_INICIO' | 'VIGENTE' | 'VENCIDA' | 'CANCELADA';
export type FormaPagamento = 'MANUAL' | 'CORTESIA' | 'PIX' | 'CARTAO' | 'BOLETO';
export type TipoTermo = 'TERMOS_USO' | 'POLITICA_PRIVACIDADE';

export interface Curso {
  id: number;
  titulo: string;
  slug: string;
  resumo: string;
  descricao: string | null;
  status: StatusCurso;
  capaUrl: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Turma {
  id: number;
  cursoId: number;
  nome: string;
  status: StatusTurma;
  percentualConclusao: number;
  cargaHorariaHoras: number | null;
}

export interface Modulo {
  id: number;
  turmaId: number;
  titulo: string;
  ordem: number;
  status: StatusConteudo;
}

export interface Aula {
  id: number;
  moduloId: number;
  titulo: string;
  descricao: string | null;
  ordem: number;
  gratuita: boolean;
  status: StatusConteudo;
}

export interface Video {
  aulaId: number;
  status: StatusVideo;
  publicado: boolean;
  nomeOriginal: string | null;
  tamanhoOriginal: number | null;
  tamanhoFinal: number | null;
  reducaoPercentual: number | null;
  duracaoSegundos: number | null;
  largura: number | null;
  altura: number | null;
  variantes: string[];
  segundosProcessamentoPorMinuto: number | null;
  thumbnailUrl: string | null;
  erro: string | null;
  processadoEm: string | null;
  atualizadoEm: string;
}

export interface EnvioVideo {
  tamanhoParte: number;
  partes: { numero: number; url: string }[];
  expiraEm: string;
}

/** Envio de um arquivo só (capa, arquivo de apoio): PUT na url com o Content-Type informado. */
export interface EnvioSimples {
  chave: string;
  url: string;
  contentType: string;
  expiraEm: string;
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

export interface Matricula {
  id: number;
  turmaId: number;
  usuarioId: number;
  nome: string;
  email: string;
  inicio: string;
  fim: string | null;
  status: 'ATIVA' | 'CANCELADA';
  situacao: SituacaoMatricula;
  formaPagamento: FormaPagamento;
  transacao: string | null;
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
  oculto: boolean | null;
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
  oculto: boolean | null;
  criadoEm: string;
  anexos: Anexo[];
}

export interface Topico extends Omit<TopicoResumo, 'respostas'> {
  corpoHtml: string;
  anexos: Anexo[];
  respostas: Resposta[];
}

export interface Termo {
  id: number;
  tipo: TipoTermo;
  versao: number;
  conteudoHtml: string;
  publicadoEm: string;
}

interface Rotulo {
  rotulo: string;
  severidade: Severidade;
}

export const STATUS_CURSO: Record<StatusCurso, Rotulo> = {
  RASCUNHO: { rotulo: 'Rascunho', severidade: 'neutral' },
  PUBLICADO: { rotulo: 'Publicado', severidade: 'ok' },
  ARQUIVADO: { rotulo: 'Arquivado', severidade: 'warn' },
};

export const STATUS_TURMA: Record<StatusTurma, Rotulo> = {
  PREPARACAO: { rotulo: 'Em preparação', severidade: 'neutral' },
  ATIVA: { rotulo: 'Ativa', severidade: 'ok' },
  ENCERRADA: { rotulo: 'Encerrada', severidade: 'warn' },
};

export const STATUS_CONTEUDO: Record<StatusConteudo, Rotulo> = {
  ATIVO: { rotulo: 'Ativo', severidade: 'ok' },
  INATIVO: { rotulo: 'Inativo', severidade: 'neutral' },
};

export const STATUS_VIDEO: Record<StatusVideo, Rotulo> = {
  AGUARDANDO_ENVIO: { rotulo: 'Aguardando envio', severidade: 'neutral' },
  PENDENTE: { rotulo: 'Na fila', severidade: 'info' },
  PROCESSANDO: { rotulo: 'Processando', severidade: 'info' },
  PRONTO: { rotulo: 'Pronto', severidade: 'ok' },
  ERRO: { rotulo: 'Erro', severidade: 'err' },
};

export const SITUACAO_MATRICULA: Record<SituacaoMatricula, Rotulo> = {
  AGUARDANDO_INICIO: { rotulo: 'Aguardando início', severidade: 'info' },
  VIGENTE: { rotulo: 'Vigente', severidade: 'ok' },
  VENCIDA: { rotulo: 'Vencida', severidade: 'warn' },
  CANCELADA: { rotulo: 'Cancelada', severidade: 'err' },
};

export const FORMAS_PAGAMENTO: Record<FormaPagamento, string> = {
  MANUAL: 'Manual',
  CORTESIA: 'Cortesia',
  PIX: 'Pix',
  CARTAO: 'Cartão',
  BOLETO: 'Boleto',
};

export const TIPOS_TERMO: Record<TipoTermo, string> = {
  TERMOS_USO: 'Termos de uso',
  POLITICA_PRIVACIDADE: 'Política de privacidade',
};

export function opcoes<V extends string>(mapa: Record<V, Rotulo | string>) {
  return (Object.keys(mapa) as V[]).map((valor) => {
    const item = mapa[valor];
    return { valor, rotulo: typeof item === 'string' ? item : item.rotulo };
  });
}
`````

`web/adm/src/utilitarios.test.ts`:

`````ts
import { describe, expect, it } from 'vitest';
import { bytes, diaLocal, duracao, fimDoDia, inicioDoDia } from '@compartilhado/ui/formato.ts';
import { tipoDoVideo } from './paginas/aula/VideoAula.tsx';
import { trocar } from './paginas/turma/ConteudoAba.tsx';
import { opcoes, STATUS_CURSO } from './tipos.ts';

describe('reordenação', () => {
  const itens = [{ id: 10 }, { id: 20 }, { id: 30 }];

  it('troca o item com o vizinho de cima e de baixo', () => {
    expect(trocar(itens, 1, 0)).toEqual([20, 10, 30]);
    expect(trocar(itens, 1, 2)).toEqual([10, 30, 20]);
  });

  it('não altera a lista original', () => {
    trocar(itens, 0, 1);
    expect(itens.map((i) => i.id)).toEqual([10, 20, 30]);
  });
});

describe('tipo do vídeo', () => {
  it('usa o tipo informado pelo navegador quando é aceito', () => {
    expect(tipoDoVideo(new File([''], 'a.bin', { type: 'video/webm' }))).toBe('video/webm');
  });

  it('deduz pela extensão quando o navegador não informa', () => {
    expect(tipoDoVideo(new File([''], 'Aula 1.MKV'))).toBe('video/x-matroska');
    expect(tipoDoVideo(new File([''], 'aula.mov'))).toBe('video/quicktime');
  });

  it('recusa o que não é vídeo aceito', () => {
    expect(tipoDoVideo(new File([''], 'aula.avi', { type: 'video/x-msvideo' }))).toBeNull();
    expect(tipoDoVideo(new File([''], 'notas.pdf', { type: 'application/pdf' }))).toBeNull();
  });
});

describe('formatação', () => {
  it('tamanho em bytes', () => {
    expect(bytes(512)).toBe('512 B');
    expect(bytes(1536)).toBe('1,5 KB');
    expect(bytes(5 * 1024 * 1024)).toBe('5 MB');
    expect(bytes(null)).toBe('—');
  });

  it('duração em minutos e segundos', () => {
    expect(duracao(3)).toBe('0:03');
    expect(duracao(125)).toBe('2:05');
  });

  it('dia do campo de data vira o dia inteiro no fuso local, e volta', () => {
    const inicio = new Date(inicioDoDia('2026-03-10'));
    const fim = new Date(fimDoDia('2026-03-10'));
    expect([inicio.getHours(), inicio.getMinutes()]).toEqual([0, 0]);
    expect([fim.getHours(), fim.getMinutes()]).toEqual([23, 59]);
    expect(diaLocal(inicioDoDia('2026-03-10'))).toBe('2026-03-10');
    expect(diaLocal(null)).toBe('');
  });
});

describe('opções de seleção', () => {
  it('saem dos rótulos dos status', () => {
    expect(opcoes(STATUS_CURSO)).toEqual([
      { valor: 'RASCUNHO', rotulo: 'Rascunho' },
      { valor: 'PUBLICADO', rotulo: 'Publicado' },
      { valor: 'ARQUIVADO', rotulo: 'Arquivado' },
    ]);
  });
});
`````

`web/compartilhado/src/api/envio.test.ts`:

`````ts
import { afterEach, describe, expect, it, vi } from 'vitest';
import { enviarEmPartes, enviarParaUrl } from './envio.ts';
import { ErroApi } from './erros.ts';

/** XHR falso: guarda cada envio e responde com o status e o ETag configurados. */
class XhrFalso {
  static enviados: { url: string; corpo: Blob; cabecalhos: Record<string, string> }[] = [];
  static status = 200;
  url = '';
  status = 0;
  cabecalhos: Record<string, string> = {};
  upload: { onprogress?: (e: { loaded: number }) => void } = {};
  onload?: () => void;
  onerror?: () => void;
  onabort?: () => void;

  open(_metodo: string, url: string) {
    this.url = url;
  }
  setRequestHeader(nome: string, valor: string) {
    this.cabecalhos[nome] = valor;
  }
  getResponseHeader(nome: string) {
    return nome === 'ETag' ? `"etag-${XhrFalso.enviados.length}"` : null;
  }
  abort() {
    this.onabort?.();
  }
  send(corpo: Blob) {
    XhrFalso.enviados.push({ url: this.url, corpo, cabecalhos: this.cabecalhos });
    this.upload.onprogress?.({ loaded: corpo.size });
    this.status = XhrFalso.status;
    this.onload?.();
  }
}

afterEach(() => {
  XhrFalso.enviados = [];
  XhrFalso.status = 200;
  vi.unstubAllGlobals();
});

describe('envio direto ao armazenamento', () => {
  it('manda o Content-Type informado e devolve o ETag', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    const etag = await enviarParaUrl('https://midia/x', new Blob(['abc']), 'image/png');
    expect(etag).toBe('"etag-1"');
    expect(XhrFalso.enviados[0]!.cabecalhos).toEqual({ 'Content-Type': 'image/png' });
  });

  it('status de erro vira ErroApi', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    XhrFalso.status = 403;
    await expect(enviarParaUrl('https://midia/x', new Blob(['a']), null)).rejects.toBeInstanceOf(
      ErroApi,
    );
  });

  it('divide o arquivo pelas partes, em ordem, e soma o progresso', async () => {
    vi.stubGlobal('XMLHttpRequest', XhrFalso);
    const progresso: number[] = [];
    const partes = await enviarEmPartes(
      new Blob(['0123456789']),
      4,
      [
        { numero: 3, url: 'u3' },
        { numero: 1, url: 'u1' },
        { numero: 2, url: 'u2' },
      ],
      (n) => progresso.push(n),
    );

    expect(XhrFalso.enviados.map((e) => [e.url, e.corpo.size])).toEqual([
      ['u1', 4],
      ['u2', 4],
      ['u3', 2],
    ]);
    expect(partes).toEqual([
      { numero: 1, etag: '"etag-1"' },
      { numero: 2, etag: '"etag-2"' },
      { numero: 3, etag: '"etag-3"' },
    ]);
    expect(progresso).toEqual([4, 8, 10, 10]);
    // Parte de vídeo vai sem Content-Type: a URL assinada não o inclui
    expect(XhrFalso.enviados[0]!.cabecalhos).toEqual({});
  });
});
`````

`web/compartilhado/src/api/envio.ts`:

`````ts
import { ErroApi } from './erros.ts';

/** Progresso de um envio, em bytes já enviados. */
export type AoProgredir = (enviados: number) => void;

export interface ParteEnviada {
  numero: number;
  etag: string;
}

/**
 * PUT do corpo direto no Garage, numa URL pré-assinada pela API (o arquivo não passa pela API).
 * XHR em vez de fetch: só ele informa o progresso do envio. Devolve o ETag da resposta (o bucket o
 * expõe por CORS).
 */
export function enviarParaUrl(
  url: string,
  corpo: Blob,
  contentType: string | null,
  aoProgredir?: AoProgredir,
  sinal?: AbortSignal,
): Promise<string> {
  return new Promise((resolver, rejeitar) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    if (contentType) xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (evento) => aoProgredir?.(evento.loaded);
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolver(xhr.getResponseHeader('ETag') ?? '');
      } else {
        rejeitar(
          new ErroApi(xhr.status, { detail: 'O armazenamento recusou o arquivo. Tente de novo.' }),
        );
      }
    };
    xhr.onerror = () => rejeitar(new ErroApi(0, null));
    xhr.onabort = () => rejeitar(new DOMException('Envio cancelado.', 'AbortError'));
    sinal?.addEventListener('abort', () => xhr.abort(), { once: true });
    xhr.send(corpo);
  });
}

/**
 * Envio em partes (vídeo): cada parte vai para a sua URL, em ordem, e o progresso soma o que já
 * foi enviado. Devolve número e ETag de cada parte para a API concluir o envio.
 */
export async function enviarEmPartes(
  arquivo: Blob,
  tamanhoParte: number,
  urls: { numero: number; url: string }[],
  aoProgredir?: AoProgredir,
  sinal?: AbortSignal,
): Promise<ParteEnviada[]> {
  const enviadas: ParteEnviada[] = [];
  for (const { numero, url } of [...urls].sort((a, b) => a.numero - b.numero)) {
    const inicio = (numero - 1) * tamanhoParte;
    const parte = arquivo.slice(inicio, Math.min(arquivo.size, inicio + tamanhoParte));
    const etag = await enviarParaUrl(url, parte, null, (n) => aoProgredir?.(inicio + n), sinal);
    enviadas.push({ numero, etag });
  }
  aoProgredir?.(arquivo.size);
  return enviadas;
}
`````

`web/compartilhado/src/api/useCarregar.ts`:

`````ts
import { useCallback, useEffect, useEffectEvent, useState } from 'react';
import { mensagemDeErro } from './erros.ts';

export interface Carga<T> {
  dados: T | undefined;
  erro: string | undefined;
  /** Buscando (a primeira vez ou de novo); os dados anteriores continuam em `dados`. */
  carregando: boolean;
  /** Busca de novo mantendo os dados atuais na tela. */
  recarregar: () => void;
  /** Troca os dados na tela sem ir à API (resposta de uma alteração). */
  definir: (dados: T) => void;
}

interface Estado<T> {
  /** Pedido (dependências + versão) a que os dados respondem. */
  pedido: string;
  dados?: T;
  erro?: string;
}

/**
 * Busca dados ao montar e quando as dependências mudam (comparadas pelo valor). Resposta de um
 * pedido antigo é descartada.
 */
export function useCarregar<T>(
  buscar: () => Promise<T>,
  dependencias: readonly unknown[],
): Carga<T> {
  const [versao, setVersao] = useState(0);
  const [estado, setEstado] = useState<Estado<T>>({ pedido: '' });
  const pedido = `${JSON.stringify(dependencias)}#${versao}`;
  const executar = useEffectEvent(buscar);

  useEffect(() => {
    let valido = true;
    executar().then(
      (dados) => valido && setEstado({ pedido, dados }),
      (falha: unknown) =>
        valido &&
        setEstado((atual) => ({ pedido, dados: atual.dados, erro: mensagemDeErro(falha) })),
    );
    return () => {
      valido = false;
    };
  }, [pedido]);

  const recarregar = useCallback(() => setVersao((v) => v + 1), []);
  const definir = useCallback((dados: T) => setEstado((atual) => ({ ...atual, dados })), []);
  return {
    dados: estado.dados,
    erro: estado.erro,
    carregando: estado.pedido !== pedido,
    recarregar,
    definir,
  };
}
`````

`web/compartilhado/src/ui/formato.ts`:

`````ts
/** Formatação para exibição, em pt-BR e no fuso de quem está usando. */

const DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function data(valor: string | null | undefined): string {
  return valor ? DATA.format(new Date(valor)) : '—';
}

export function dataHora(valor: string | null | undefined): string {
  return valor ? DATA_HORA.format(new Date(valor)) : '—';
}

export function bytes(valor: number | null | undefined): string {
  if (valor == null) return '—';
  const unidades = ['B', 'KB', 'MB', 'GB'];
  let n = valor;
  let i = 0;
  while (n >= 1024 && i < unidades.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toLocaleString('pt-BR', { maximumFractionDigits: i === 0 ? 0 : 1 })} ${unidades[i]}`;
}

export function duracao(segundos: number | null | undefined): string {
  if (segundos == null) return '—';
  const m = Math.floor(segundos / 60);
  const s = Math.round(segundos % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

/** `AAAA-MM-DD` (campo de data) → instante do início do dia no fuso local. */
export function inicioDoDia(dia: string): string {
  return new Date(`${dia}T00:00:00`).toISOString();
}

/** `AAAA-MM-DD` → instante do fim do dia no fuso local (a matrícula vale o dia inteiro). */
export function fimDoDia(dia: string): string {
  return new Date(`${dia}T23:59:59`).toISOString();
}

/** Instante → `AAAA-MM-DD` no fuso local (para preencher campo de data). */
export function diaLocal(valor: string | null | undefined): string {
  if (!valor) return '';
  const d = new Date(valor);
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}
`````

`web/e2e/adm/conteudo.spec.ts`:

`````ts
import { escolher } from '../support/telas.ts';
import { clienteApi, montarCurso } from '../support/api.ts';
import { PDF_FALSO, PNG } from '../support/amostras.ts';
import { stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Cursos', () => {
  test('formulário vazio mostra os erros e não chama a API', async ({ comoAdmin: page }) => {
    let chamou = false;
    page.on('request', (r) => {
      if (r.url().endsWith('/api/adm/cursos') && r.method() === 'POST') chamou = true;
    });
    await page.goto('/cursos');
    await page.getByRole('button', { name: 'Novo curso' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Salvar' }).click();

    await expect(page.getByText('Informe o título.')).toBeVisible();
    await expect(page.getByText('Informe o resumo.')).toBeVisible();
    expect(chamou).toBe(false);
  });

  test('slug inválido é barrado na tela e slug repetido é recusado pela API', async ({
    comoAdmin: page,
    request,
  }) => {
    const adm = await clienteApi(request);
    const existente = await adm.post<{ slug: string }>('/api/adm/cursos', {
      titulo: `Curso ${stamp()}`,
      resumo: 'Já existe.',
    });

    await page.goto('/cursos');
    await page.getByRole('button', { name: 'Novo curso' }).click();
    const form = page.getByRole('dialog');
    await form.getByRole('textbox', { name: 'Título', exact: true }).fill('Outro curso');
    await form.getByRole('textbox', { name: 'Resumo', exact: true }).fill('Resumo.');
    await form.getByRole('textbox', { name: 'Endereço (slug)' }).fill('Com Espaço');
    await form.getByRole('button', { name: 'Salvar' }).click();
    await expect(form.getByText('Use só letras minúsculas, números e hífens.')).toBeVisible();

    await form.getByRole('textbox', { name: 'Endereço (slug)' }).fill(existente.slug);
    const resposta = page.waitForResponse(
      (r) => r.url().endsWith('/api/adm/cursos') && r.request().method() === 'POST',
    );
    await form.getByRole('button', { name: 'Salvar' }).click();
    expect((await resposta).status()).toBe(409);
    await expect(
      form.getByText(`Já existe um curso com o slug "${existente.slug}".`),
    ).toBeVisible();
  });

  test('capa em formato errado não é enviada', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}`);
    await page
      .getByLabel('Enviar capa')
      .setInputFiles({ ...PNG, name: 'capa.gif', mimeType: 'image/gif' });
    await expect(page.getByText('Envie uma imagem JPG ou PNG.')).toBeVisible();
  });

  test('curso com turmas não pode ser excluído', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}`);
    await page.getByRole('button', { name: 'Excluir curso' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('O curso tem turmas. Exclua as turmas vazias ou arquive o curso.'),
    ).toBeVisible();
    await expect(page.getByRole('heading', { name: curso.titulo })).toBeVisible();
  });

  test('a lista filtra pelo status', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const titulo = `Arquivado ${stamp()}`;
    const curso = await adm.post<{ id: number }>('/api/adm/cursos', { titulo, resumo: 'r' });
    await adm.patch(`/api/adm/cursos/${curso.id}/status`, { status: 'ARQUIVADO' });

    await page.goto('/cursos');
    await escolher(page, 'Status', 'Arquivado');
    await expect(page.getByRole('cell', { name: titulo })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Rascunho' })).toHaveCount(0);
  });

  test('curso que não existe mostra o erro', async ({ comoAdmin: page }) => {
    await page.goto('/cursos/999999999');
    await expect(page.getByText('Não foi possível abrir o curso')).toBeVisible();
  });
});

test.describe('Conteúdo da turma', () => {
  test('reordena módulos e aulas', async ({ comoAdmin: page, request }) => {
    const adm = await clienteApi(request);
    const curso = await montarCurso(adm);
    await adm.post(`/api/adm/turmas/${curso.turmaId}/modulos`, { titulo: 'Módulo 2' });
    await adm.post(`/api/adm/modulos/${curso.moduloId}/aulas`, { titulo: 'Aula 2' });

    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await expect(page.getByText('1. Módulo 1')).toBeVisible();
    await page.getByRole('button', { name: 'Descer Módulo 1' }).click();
    await expect(page.getByText('1. Módulo 2')).toBeVisible();
    await expect(page.getByText('2. Módulo 1')).toBeVisible();

    const aulas = page.getByRole('list', { name: 'Aulas de Módulo 1' });
    await expect(aulas.getByRole('listitem').first()).toContainText('Aula 1');
    await page.getByRole('button', { name: 'Subir Aula 2' }).click();
    await expect(aulas.getByRole('listitem').first()).toContainText('Aula 2');
  });

  test('módulo com aulas não pode ser excluído', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await page.getByRole('button', { name: 'Excluir Módulo 1' }).click();
    await page
      .getByRole('dialog', { name: 'Remover registro?' })
      .getByRole('button', { name: 'Remover' })
      .click();
    await expect(
      page.getByText('O módulo tem aulas. Exclua as aulas ou inative o módulo.'),
    ).toBeVisible();
  });

  test('inativa e ativa um módulo', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    const modulo = page.getByLabel('Módulo Módulo 1');
    await page.getByRole('button', { name: 'Inativar Módulo 1' }).click();
    await expect(modulo.getByText('Inativo', { exact: true }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Ativar Módulo 1' }).click();
    await expect(page.getByRole('button', { name: 'Inativar Módulo 1' })).toBeVisible();
  });

  test('título vazio no novo módulo é barrado', async ({ comoAdmin: page, request }) => {
    const curso = await montarCurso(await clienteApi(request));
    await page.goto(`/cursos/${curso.cursoId}/turmas/${curso.turmaId}`);
    await page.getByRole('button', { name: 'Novo módulo' }).click();
    await page.getByRole('button', { name: 'Criar módulo' }).click();
    await expect(page.getByText('Informe título do módulo.')).toBeVisible();
  });
});

test.describe('Aula', () => {
  test('arquivo que não é vídeo é recusado na tela', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    await expect(page.getByText('Esta aula ainda não tem vídeo.')).toBeVisible();
    await page.getByLabel('Enviar vídeo').setInputFiles(PDF_FALSO);
    await expect(page.getByText('Envie um vídeo MP4, MOV, MKV ou WebM.')).toBeVisible();
  });

  test('vídeo falso (extensão certa, conteúdo errado) é recusado pela API', async ({
    comoAdmin: page,
    request,
  }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    await page
      .getByLabel('Enviar vídeo')
      .setInputFiles({ ...PDF_FALSO, name: 'aula.mp4', mimeType: 'video/mp4' });
    await expect(page.getByText('O vídeo não foi enviado')).toBeVisible({ timeout: 30_000 });
  });

  test('edita os dados da aula e valida o título', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    const titulo = page.getByRole('textbox', { name: 'Título', exact: true });
    await titulo.fill('');
    await titulo.blur();
    // Ao sair do campo vazio, a biblioteca avisa; o botão desce com o aviso
    await expect(page.getByText('Campo obrigatório')).toBeVisible();
    await page.getByRole('button', { name: 'Salvar aula' }).click();
    await expect(page.getByText('Informe o título.')).toBeVisible();

    await titulo.fill('Aula renomeada');
    await page.getByRole('checkbox', { name: /Aula gratuita/ }).check();
    await page.getByRole('button', { name: 'Salvar aula' }).click();
    await expect(page.getByRole('heading', { name: 'Aula renomeada' })).toBeVisible();
  });

  test('arquivo de apoio em formato não aceito', async ({ comoAdmin: page, request }) => {
    const c = await montarCurso(await clienteApi(request));
    await page.goto(
      `/cursos/${c.cursoId}/turmas/${c.turmaId}/modulos/${c.moduloId}/aulas/${c.aulaId}`,
    );
    const arquivos = page.getByRole('region', { name: 'Arquivos de apoio da aula' });
    await arquivos.getByLabel('Enviar arquivo').setInputFiles(PNG);
    await expect(page.getByText('Envie PDF, ZIP ou TXT.')).toBeVisible();
  });
});
`````

`web/e2e/adm/curso-completo.spec.ts`:

`````ts
import { escolher } from '../support/telas.ts';
import { alunoConfirmado } from '../support/contas.ts';
import { stamp } from '../support/dados.ts';
import { PNG, TXT, VIDEO_3S } from '../support/amostras.ts';
import { expect, test } from '../support/sessoes.ts';

test('admin cria um curso completo e envia o vídeo da aula até ficar pronto', async ({
  comoAdmin: page,
  request,
}) => {
  // A conversão do vídeo pelo worker leva alguns segundos
  test.slow();
  const titulo = `Curso Playwright ${stamp()}`;
  const aluno = await alunoConfirmado(request);

  await page.goto('/cursos');
  await page.getByRole('button', { name: 'Novo curso' }).click();
  const formCurso = page.getByRole('dialog');
  await formCurso.getByRole('textbox', { name: 'Título', exact: true }).fill(titulo);
  await formCurso
    .getByRole('textbox', { name: 'Resumo', exact: true })
    .fill('Curso criado pela jornada do ADM.');
  const criar = page.waitForResponse(
    (r) => r.url().endsWith('/api/adm/cursos') && r.request().method() === 'POST',
  );
  await formCurso.getByRole('button', { name: 'Salvar' }).click();
  expect((await criar).status()).toBe(201);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  // Capa: o navegador envia direto ao armazenamento pela URL pré-assinada
  await page.getByLabel('Enviar capa').setInputFiles(PNG);
  await expect(page.getByRole('img', { name: `Capa de ${titulo}` })).toBeVisible();

  // Turma, módulo e aula
  await page.getByRole('tab', { name: 'Turmas' }).click();
  await page.getByRole('button', { name: 'Nova turma' }).click();
  await page.getByRole('textbox', { name: 'Nome da turma' }).fill('Turma de outubro');
  await page.getByRole('button', { name: 'Criar turma' }).click();
  await expect(page.getByRole('heading', { name: 'Turma de outubro' })).toBeVisible();

  await page.getByRole('button', { name: 'Novo módulo' }).click();
  await page.getByRole('textbox', { name: 'Título do módulo' }).fill('Primeiros passos');
  await page.getByRole('button', { name: 'Criar módulo' }).click();
  await expect(page.getByText('1. Primeiros passos')).toBeVisible();

  await page.getByRole('button', { name: 'Nova aula' }).click();
  await page.getByRole('textbox', { name: 'Título da aula' }).fill('Boas-vindas');
  await page.getByRole('button', { name: 'Criar aula' }).click();
  await expect(page.getByRole('heading', { name: 'Boas-vindas' })).toBeVisible();

  // Vídeo em partes e conversão
  await page.getByLabel('Enviar vídeo').setInputFiles(VIDEO_3S);
  await expect(page.getByText('Na fila').or(page.getByText('Processando'))).toBeVisible();
  await expect(page.getByText('Pronto', { exact: true })).toBeVisible({ timeout: 150_000 });
  await expect(page.getByRole('img', { name: 'Quadro do vídeo' })).toBeVisible();
  await expect(page.getByText('0:03', { exact: true })).toBeVisible();

  // Arquivo de apoio da aula
  const arquivosAula = page.getByRole('region', { name: 'Arquivos de apoio da aula' });
  await arquivosAula.getByRole('textbox', { name: 'Título do arquivo' }).fill('Roteiro');
  await arquivosAula.getByLabel('Enviar arquivo').setInputFiles(TXT);
  await expect(arquivosAula.getByText('roteiro.txt')).toBeVisible();

  // Matrícula de um aluno
  await page.getByRole('button', { name: 'Voltar à turma' }).click();
  await page.getByRole('tab', { name: 'Matrículas' }).click();
  await page.getByRole('button', { name: 'Matricular aluno' }).click();
  await page.getByRole('textbox', { name: 'E-mail do aluno' }).fill(aluno);
  await page.getByRole('button', { name: 'Matricular', exact: true }).click();
  await expect(page.getByRole('cell', { name: aluno })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Vigente' })).toBeVisible();

  // Publica: turma ativa e curso publicado
  await page.getByRole('tab', { name: 'Configurações' }).click();
  await escolher(page, 'Status', 'Ativa');
  await expect(page.getByText('Status da turma alterado', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Voltar' }).click();
  await page.getByRole('tab', { name: 'Dados' }).click();
  await escolher(page, 'Alterar status', 'Publicado');
  await expect(page.getByText('Status do curso alterado', { exact: true })).toBeVisible();

  // O curso aparece no catálogo público
  const catalogo = await page.request.get('/api/cursos');
  expect(JSON.stringify(await catalogo.json())).toContain(titulo);
});
`````

`web/e2e/adm/termos.spec.ts`:

`````ts
import { stamp } from '../support/dados.ts';
import { expect, test } from '../support/sessoes.ts';

test.describe('Termos', () => {
  test('texto vazio não é publicado', async ({ comoAdmin: page }) => {
    await page.goto('/termos');
    await page.getByRole('button', { name: 'Publicar versão' }).click();
    await expect(page.getByText('Escreva o texto da nova versão.')).toBeVisible();
  });

  test('publica uma nova versão da política de privacidade', async ({ comoAdmin: page }) => {
    const marca = `Política revisada ${stamp()}`;
    await page.goto('/termos');
    await page.getByRole('tab', { name: 'Política de privacidade' }).click();
    const painel = page.getByRole('tabpanel', { name: 'Política de privacidade' });
    await painel
      .getByRole('textbox', { name: 'Texto da nova versão' })
      .fill(`# ${marca}\n\nTratamos os seus dados com cuidado.`);
    await page.getByRole('button', { name: 'Publicar versão' }).click();
    await page
      .getByRole('dialog', { name: /Publicar nova versão/ })
      .getByRole('button', { name: 'Publicar', exact: true })
      .click();

    await expect(painel.getByRole('heading', { name: marca })).toBeVisible();
    await expect(
      page
        .getByRole('tabpanel', { name: 'Política de privacidade' })
        .getByText(/Versão vigente: \d+/),
    ).toBeVisible();
  });
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

`web/e2e/support/amostras.ts`:

`````ts
import path from 'node:path';

/** Vídeo de 3 s usado também nos testes do backend (não duplicamos o binário). */
export const VIDEO_3S = path.resolve(
  process.cwd(),
  '../backend/src/test/resources/amostras/video-3s.mp4',
);

/** PNG 1×1 transparente. */
export const PNG = {
  name: 'capa.png',
  mimeType: 'image/png',
  buffer: Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
    'base64',
  ),
};

export const TXT = {
  name: 'roteiro.txt',
  mimeType: 'text/plain',
  buffer: Buffer.from('Roteiro da aula gerado pelo teste.\n'),
};

export const PDF_FALSO = {
  name: 'aula.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n%%EOF\n'),
};
`````

`web/e2e/support/api.ts`:

`````ts
import type { APIRequestContext } from '@playwright/test';
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
`````

## Antes de executar

Fase 11 concluída e integrada na `homolog`. Crie a branch a partir dela:

```bash
git switch homolog
git pull
git switch -c feature/web-adm
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
- Com tudo verde, as tasks de `.sdd/tasks/painel-administrativo-v1.0/` passam para `Status: Concluída` e os critérios de aceite
  são marcados (`- [x]`).

## Depois de executar

```bash
git add backend/ web/ infra/ docs/ .github/ README.md .sdd/
git commit -m "feat(adm): adicionar as telas de gestão do ADM"
git push -u origin feature/web-adm
```

Abra o Pull Request de `feature/web-adm` para `homolog`.
