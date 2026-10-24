---
feature: area-do-aluno
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Área do aluno

## 1. Resumo Executivo

O aluno estuda pelas telas de `aluno.celke.uno`: vê os cursos, abre a turma, assiste às aulas
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
