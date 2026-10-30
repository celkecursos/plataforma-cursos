# ADM-001 — Cursos, turmas, módulos e aulas

- **Spec:** .sdd/specs/painel-administrativo.spec.md (v1.0) · atende RF-01, RF-02, RF-08
- **Nível:** Pleno
- **Agente:** dev-pleno
- **Status:** Concluída
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

- [x] `web/e2e/adm/conteudo.spec.ts` verde

## Armadilhas

Mostrar erro da API só no console.
