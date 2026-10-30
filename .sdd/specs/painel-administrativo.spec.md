---
feature: painel-administrativo
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Painel administrativo

## 1. Resumo Executivo

O administrador gerencia toda a plataforma pelas telas de `adm.celke.uno`: cursos, turmas, módulos e
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
