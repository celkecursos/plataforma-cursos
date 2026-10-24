---
feature: certificado
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Certificado

## 1. Resumo Executivo

O aluno que concluiu a turma emite o certificado em PDF com o próprio nome, e qualquer pessoa confere a
autenticidade pelo código impresso, na página de validação do site `cursos.celke.uno`, sem login.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Definir a regra de conclusão e a carga horária da turma |
| Aluno com matrícula vigente | Ver a situação, emitir, baixar e listar os próprios certificados |
| Qualquer pessoa | Validar um certificado pelo código |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE definir por turma o percentual de conclusão (1 a 100, padrão 100) e a carga
  horária opcional.
- **RF-02** — O sistema DEVE mostrar ao aluno o percentual visto, o exigido, se concluiu e o certificado
  já emitido.
- **RF-03** — O sistema DEVE emitir o certificado só para quem concluiu, com o nome completo confirmado
  pelo aluno; turma sem aulas nunca é concluída.
- **RF-04** — O sistema DEVE congelar na emissão o nome, o curso, a turma e a carga horária; emitir de
  novo devolve o mesmo certificado.
- **RF-05** — O sistema DEVE gerar o PDF uma vez, guardá-lo e entregá-lo ao próprio aluno por endereço
  válido por 5 minutos.
- **RF-06** — O sistema DEVE gerar um código de validação de 12 caracteres sem letras ambíguas, com 60
  bits aleatórios.
- **RF-07** — O sistema DEVE validar publicamente pelo código, sem expor e-mail nem identificadores
  internos.

## 4. Modelo de Domínio

- **Regra de conclusão:** turma, percentual exigido, carga horária.
- **Certificado:** aluno, turma, código, nome impresso, curso, turma, carga horária, emitido em,
  chave do PDF.

## 5. Estados e Fluxos

- Para o aluno: `em andamento` → `concluído` → `emitido`.
- **Fluxo:** assistir até o percentual → confirmar o nome → emitir → baixar → validar pelo código.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Definir regra | `percentualConclusao`, `cargaHorariaHoras` | regra |
| Situação | turma | percentuais, concluiu, certificado |
| Emitir | `nomeCompleto` | certificado |
| Baixar | certificado | endereço temporário do PDF |
| Validar | código | nome, curso, turma, carga horária, data |

## 7. Casos de Borda

- Certificado de outro aluno: "não encontrado".
- Código inexistente ou malformado: "não encontrado".
- Mudança da regra depois da emissão: o certificado emitido não muda.

## 8. Premissas Validadas

- O certificado é por turma, não por curso.

## 9. Riscos

- **Falsificação:** código aleatório de 60 bits e validação pública.
