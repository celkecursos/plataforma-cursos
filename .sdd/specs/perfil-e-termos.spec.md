---
feature: perfil-e-termos
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Perfil e termos

## 1. Resumo Executivo

Cada usuário cuida do próprio perfil — nome, senha e foto — e o aluno só usa a área do aluno depois de
aceitar a versão vigente dos termos de uso e da política de privacidade, a cada versão nova publicada
pelo administrador.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Aluno e administrador | Ver e alterar o próprio perfil |
| Aluno | Ver e aceitar os termos vigentes |
| Administrador | Publicar uma nova versão dos termos e listar as versões |
| Visitante | Ler os termos vigentes |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE mostrar o perfil do próprio usuário sem senha nem hash e permitir trocar o
  nome.
- **RF-02** — O sistema DEVE trocar a senha só com a senha atual, pela política do papel (aluno 10,
  administrador 12 caracteres), diferente da atual, encerrando todas as sessões.
- **RF-03** — O sistema DEVE receber a foto direto no armazenamento, conferir que a chave é do próprio
  usuário, regravá-la sem metadados em até 400 × 400 e apagar a anterior.
- **RF-04** — O sistema DEVE manter termos de uso e política de privacidade versionados; a vigente de
  cada tipo é a maior versão.
- **RF-05** — O sistema DEVE bloquear as rotas do aluno enquanto houver termo vigente sem aceite,
  informando quais estão pendentes; perfil, sessão e termos continuam liberados.
- **RF-06** — O sistema DEVE registrar o aceite com IP, navegador e data, aceitando só a versão vigente.
- **RF-07** — O sistema DEVE permitir ao administrador publicar a próxima versão em Markdown, guardada
  também como HTML filtrado.

## 4. Modelo de Domínio

- **Termo:** tipo (`TERMOS_USO`, `POLITICA_PRIVACIDADE`), versão, texto, publicado em.
- **Aceite:** usuário, termo, IP, navegador, data.
- **Usuário:** foto.

## 5. Estados e Fluxos

- Aluno: `com pendência` → aceita → `liberado`; nova versão publicada → `com pendência`.
- **Fluxo:** primeiro acesso → 403 com termos pendentes → aceite → área do aluno.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Perfil | — | `nome`, `email`, `papeis`, foto |
| Trocar senha | `senhaAtual`, `novaSenha` | sessões encerradas |
| Termos vigentes | — | um texto por tipo |
| Pendentes | — | termos sem aceite |
| Aceitar | termo | aceite registrado |
| Publicar versão | `tipo`, `texto` | termo |

## 7. Casos de Borda

- Aceite de versão antiga: 409.
- Foto com chave de outro usuário: recusada.
- Nova senha igual à atual: recusada.

## 8. Premissas Validadas

- O bloqueio vale para o aluno; o administrador não aceita termos.

## 9. Riscos

- **Aluno sem aceite usando a plataforma:** bloqueio no servidor, não só na tela.
