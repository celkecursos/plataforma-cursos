---
feature: arquivos-de-apoio
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Arquivos de apoio

## 1. Resumo Executivo

O administrador anexa materiais (ZIP, PDF ou TXT) a uma aula ou a um módulo, e o aluno com acesso os
baixa por um endereço curto e temporário. O arquivo é conferido pelo conteúdo, não só pela extensão.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Enviar e remover arquivos de aulas e módulos |
| Aluno com acesso | Listar e baixar os arquivos da aula (gratuita inclusive) e do módulo (matrícula vigente) |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE aceitar ZIP, PDF ou TXT de até 200 MB, de uma aula ou de um módulo.
- **RF-02** — O sistema DEVE receber o arquivo direto no armazenamento e, na confirmação, conferir a
  chave gerada para o mesmo dono, o tamanho real e o conteúdo (assinatura do ZIP e do PDF; texto sem
  byte nulo); recusado é apagado.
- **RF-03** — O sistema DEVE guardar um nome de download limpo (sem caminho, acento ou aspas) com a
  extensão do formato real.
- **RF-04** — O sistema DEVE listar ao aluno os arquivos da aula com o mesmo acesso da aula e os do
  módulo com matrícula vigente na turma.
- **RF-05** — O sistema DEVE entregar o download por um endereço válido por 5 minutos, como anexo e com
  o nome do arquivo, sem revelar a chave interna.
- **RF-06** — O sistema DEVE impedir a exclusão de aula ou módulo com arquivos.

## 4. Modelo de Domínio

- **Arquivo de apoio:** aula **ou** módulo, título, nome de download, chave, formato, tamanho.

## 5. Estados e Fluxos

- **Envio:** pedir envio → enviar direto ao armazenamento → confirmar → arquivo publicado.
- **Download:** aluno pede → acesso conferido → endereço de 5 minutos.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Pedir envio | `nomeArquivo`, `contentType`, `tamanho` | endereço de envio e chave |
| Confirmar | `chave`, `titulo` | arquivo |
| Listar | aula ou módulo | arquivos (sem chave) |
| Baixar | arquivo | endereço temporário |

## 7. Casos de Borda

- PDF falso renomeado: recusado.
- Chave de outra aula: recusada.
- Módulo inativo: responde "não encontrado".
- Envio não confirmado: expira no armazenamento.

## 8. Premissas Validadas

- Um arquivo pertence a uma aula ou a um módulo, nunca aos dois.

## 9. Riscos

- **Arquivo malicioso:** só três formatos, conferidos pelo conteúdo, baixados como anexo.
