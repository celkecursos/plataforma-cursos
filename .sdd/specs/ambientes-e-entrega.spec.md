---
feature: ambientes-e-entrega
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Ambientes e entrega

## 1. Resumo Executivo

A plataforma roda em dois ambientes na mesma VPS — produção e homologação — e cada versão chega a eles
pelo pipeline, testada de ponta a ponta antes, sem ninguém copiar arquivos à mão. Uma versão que não
fica saudável volta sozinha para a anterior.

Endereços:

| Produção | Homologação | Serve |
|---|---|---|
| `cursos.celke.uno` | `homolog-cursos.celke.uno` | site público |
| `aluno.celke.uno` | `homolog-aluno.celke.uno` | área do aluno e API |
| `adm.celke.uno` | `homolog-adm.celke.uno` | painel administrativo e API |
| `media.celke.uno` | `homolog-media.celke.uno` | vídeos e arquivos |

Entra: testes de ponta a ponta da stack completa, pipeline, preparação da VPS, publicação das versões e
deploy com volta automática. Fica de fora: backup e alertas (spec `operacao`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Desenvolvedor | Abrir Pull Request; o pipeline testa |
| Pipeline | Testar, publicar a versão testada e implantar no ambiente do branch |
| Operador da VPS | Preparar o servidor uma vez, preencher os `.env`, criar os administradores |
| Visitante | Acessar só os endereços públicos; homologação e painel restritos por IP |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE rodar, em todo Pull Request e push para `homolog` e `main`, os testes e as
  jornadas de ponta a ponta contra a stack completa atrás do proxy.
- **RF-02** — O sistema DEVE publicar as imagens testadas, sem reconstruir, com a versão igual ao
  commit, no registro `ghcr.io/celkecursos`.
- **RF-03** — O sistema DEVE implantar a homologação a cada push em `homolog` e a produção a cada push em
  `main`, um deploy por ambiente de cada vez.
- **RF-04** — O sistema DEVE, antes de cada deploy, guardar uma cópia do banco e, se algum serviço não
  ficar saudável, voltar à versão anterior (arquivos e imagens).
- **RF-05** — O sistema DEVE aceitar a chave de deploy apenas para executar o script de deploy, com
  ambiente e versão validados.
- **RF-06** — O sistema DEVE servir os dois ambientes por um único proxy com HTTPS automático, com
  redes separadas por ambiente e nenhuma porta publicada além de 80 e 443.
- **RF-07** — O sistema DEVE restringir por IP a homologação inteira e o painel de produção, e manter a
  homologação fora dos buscadores.
- **RF-08** — O sistema DEVE gerar o `.env` de cada ambiente com segredos aleatórios e diferentes entre
  os ambientes, deixando para o operador apenas SMTP e registro de imagens.
- **RF-09** — O sistema DEVE preparar a VPS com acesso só por chave, sem login de root, firewall com 22,
  80 e 443, atualizações automáticas e proteção contra força bruta.

## 4. Modelo de Domínio

- **Ambiente:** nome (`producao`, `homolog`), hosts, versão no ar, versões guardadas (5), cópias do
  banco (10), histórico de deploys.
- **Versão:** commit, imagens publicadas, resultado do deploy.

## 5. Estados e Fluxos

- Deploy: `iniciado` → `cópia do banco` → `subindo` → `saudável` | `falhou` → `versão anterior
  restaurada`.
- **Fluxo:** PR → testes + ponta a ponta → merge em `homolog` → publica → deploy na homologação →
  validação humana → PR `homolog` → `main` → deploy na produção.

## 6. Contratos de Interface

| Entrada | Significado |
|---|---|
| `<producao|homolog> <commit>` | única forma aceita pelo script de deploy |
| `REGISTRO` | origem das imagens (`ghcr.io/celkecursos`) |
| `ADM_IPS`, `HOMOLOG_IPS` | IPs liberados (preenchidos pelo operador; não versionados) |
| Secrets do repositório | `VPS_HOST`, `VPS_SSH_KEY`, `VPS_KNOWN_HOSTS` (só no GitHub) |

## 7. Casos de Borda

- Dois deploys simultâneos no mesmo ambiente: o segundo é recusado.
- Argumento com injeção de comando: recusado antes de qualquer ação.
- Queda da conexão no meio do deploy: o deploy continua no servidor.
- Variável obrigatória ausente: o serviço não sobe e o deploy volta à versão anterior.

## 8. Premissas Validadas

- Uma VPS Hostinger KVM 4 (Ubuntu 24.04) comporta os dois ambientes.
- O repositório `celkecursos/plataforma-cursos` é o dono das imagens.

## 9. Riscos

- **Deploy quebrado em produção:** volta automática e cópia do banco antes de cada deploy.
- **Chave de deploy vazada:** só executa o script, sem shell, túnel ou terminal.
