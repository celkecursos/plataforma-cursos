---
feature: envio-de-video
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Envio de vídeo

## 1. Resumo Executivo

O administrador envia o vídeo de uma aula direto ao armazenamento, sem passar o arquivo pela API, e o
sistema o converte em segundo plano para streaming adaptativo em 720p e 1080p, registrando o tamanho
antes e depois. O vídeo publicado só muda quando a nova conversão termina.

Entra: envio em partes, conferência do arquivo, conversão, status, reprocessar e remover. Fica de fora:
reprodução pelo aluno (spec `reproducao-e-progresso`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Administrador | Enviar, acompanhar, reprocessar e remover o vídeo de uma aula |
| Worker | Converter o vídeo e publicar o resultado |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE aceitar MP4, MOV, MKV ou WebM de até 5 GB, conferindo formato e tamanho
  antes de autorizar o envio.
- **RF-02** — O sistema DEVE dividir o envio em partes de 16 MiB (maiores quando passaria de 10.000
  partes), com um endereço temporário por parte, e abortar o envio anterior ainda aberto.
- **RF-03** — O sistema DEVE, na conclusão, juntar as partes, conferir o tamanho real e a assinatura do
  início do arquivo; arquivo recusado é apagado e o vídeo fica com erro.
- **RF-04** — O sistema DEVE converter o vídeo aceito em 720p e 1080p sem aumentar a resolução de origem
  (abaixo de 720p, uma variante na altura original), com segmentos de 6 segundos e miniatura.
- **RF-05** — O sistema DEVE publicar a versão nova e só então apagar o original e a versão anterior.
- **RF-06** — O sistema DEVE retomar a conversão de um worker que caiu, refazendo-a do zero.
- **RF-07** — O sistema DEVE mostrar o status com tamanho antes e depois, redução percentual,
  variantes, segundos de processamento por minuto de vídeo e miniatura.
- **RF-08** — O sistema DEVE permitir reprocessar só vídeo com erro e com o original disponível, e
  remover o vídeo fora do processamento.
- **RF-09** — O sistema DEVE impedir a exclusão de aula com vídeo.

## 4. Modelo de Domínio

- **Vídeo:** aula (um por aula), status, chave do original, chave publicada, tamanhos, duração,
  resolução, variantes, tempo de processamento, erro.

## 5. Estados e Fluxos

- Vídeo: `AGUARDANDO_ENVIO` → `PENDENTE` → `PROCESSANDO` → `PRONTO` | `ERRO`; `ERRO` → `PENDENTE`
  (reprocessar).
- **Fluxo:** pedir envio → enviar partes direto ao armazenamento → concluir → fila → conversão →
  publicado.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Pedir envio | `nomeArquivo`, `contentType`, `tamanho` | endereços temporários por parte |
| Concluir envio | lista de partes com a etiqueta de cada uma | vídeo `PENDENTE` |
| Status | aula | status, tamanhos, redução, variantes, miniatura |
| Reprocessar / remover | aula | vídeo atualizado |

As partes vão direto para `media.celke.uno`.

## 7. Casos de Borda

- Extensão falsa (texto renomeado para `.mp4`): recusado na conclusão.
- Vídeo corrompido: erro permanente, sem novas tentativas.
- Envio abandonado: as partes expiram no armazenamento.
- Troca de vídeo durante uma conversão: o publicado continua até a nova terminar.

## 8. Premissas Validadas

- A conversão roda no mesmo servidor, com prioridade baixa, sem serviço externo.

## 9. Riscos

- **Conversão lenta na VPS:** medida exposta no status para acompanhar.
- **Disco cheio:** original apagado após publicar; alerta de disco (spec `operacao`).
