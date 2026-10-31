---
feature: operacao
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Operação

## 1. Resumo Executivo

Antes de entrarem dados reais em produção, a plataforma tem backup diário fora da VPS com restauração
testada, alertas por e-mail quando algo falha e uma revisão de segurança que barra versões com
vulnerabilidade crítica conhecida.

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Operador | Agendar o backup, restaurar, receber alertas |
| Worker | Verificar falhas e disco e enviar alertas |
| Pipeline | Barrar imagens com vulnerabilidade crítica corrigível |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE copiar para fora da VPS o banco e os arquivos do armazenamento, de forma
  consistente, com retenção de 7 diárias, 4 semanais e 6 mensais.
- **RF-02** — O sistema DEVE restaurar banco e arquivos a partir de uma cópia, com o procedimento
  testado e documentado.
- **RF-03** — O sistema DEVE enviar alerta por e-mail, a cada 15 minutos, quando houver tarefas em erro
  ou o disco tiver menos de 15% livre.
- **RF-04** — O sistema DEVE limitar o tamanho dos registros de cada serviço e expirar envios
  abandonados no armazenamento.
- **RF-05** — O sistema DEVE barrar no pipeline as imagens com vulnerabilidade crítica que tenha
  correção.
- **RF-06** — O sistema DEVE documentar a revisão de segurança (ASVS nível 1) e a varredura passiva da
  aplicação.
- **RF-07** — O sistema DEVE documentar quantas horas de vídeo cabem no disco e o plano de saída para um
  armazenamento externo.

## 4. Modelo de Domínio

- **Cópia de segurança:** data, banco, arquivos, retenção.
- **Alerta:** tipo (tarefa em erro, disco), destinatário (`ALERTA_EMAIL`), data.

## 5. Estados e Fluxos

- **Backup:** agendado → cópia do banco → cópia dos arquivos → retenção aplicada.
- **Alerta:** verificação → condição atingida → e-mail.

## 6. Contratos de Interface

| Entrada | Significado |
|---|---|
| `ALERTA_EMAIL` | destinatário dos alertas (em branco: desligados) |
| Destino e senha do backup | só no servidor, nunca no repositório |

## 7. Casos de Borda

- Destino do backup fora do ar: falha registrada e alerta.
- Alerta em branco: nenhuma verificação envia e-mail.

## 8. Premissas Validadas

- O snapshot do painel da Hostinger não substitui o backup fora da VPS: vídeo e banco estão no mesmo
  disco.

## 9. Riscos

- **Perda do servidor:** cópia fora da VPS com restauração testada.
- **Falha silenciosa:** alertas por e-mail.
