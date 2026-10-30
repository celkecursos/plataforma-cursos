---
feature: autenticacao
versao: 1.0
status: aprovada
autor: pm-spec-architect
---

# Autenticação

## 1. Resumo Executivo

Alunos e administradores entram na plataforma com e-mail e senha e mantêm a sessão sem digitar a senha
de novo a cada acesso. A sessão é curta para quem a usa (credencial de acesso de 10 minutos) e renovada
em segundo plano por uma credencial de renovação guardada só no navegador, presa ao host
`aluno.celke.uno` (ou `adm.celke.uno`) e invisível para as outras aplicações de `celke.uno`.

Entra: login, renovação da sessão, saída, saída de todos os dispositivos, consulta do usuário logado e
criação do primeiro administrador pelo terminal do servidor. Fica de fora: cadastro e recuperação de
senha (spec `cadastro-e-recuperacao-de-senha`) e limites contra abuso (spec `protecao-de-acesso`).

## 2. Atores e Papéis

| Ator | Pode |
|---|---|
| Visitante | Entrar com e-mail e senha |
| Aluno (`ALUNO`) | Renovar e encerrar a própria sessão, sair de todos os dispositivos, ver os próprios dados |
| Administrador (`ADMIN`) | O mesmo do aluno, nas rotas de administração |
| Operador do servidor | Criar um administrador pelo terminal, sem expor a senha |

## 3. Requisitos Funcionais

- **RF-01** — O sistema DEVE autenticar por e-mail (sem diferenciar maiúsculas e espaços nas pontas) e
  senha, devolvendo uma credencial de acesso de 10 minutos com o identificador e os papéis do usuário.
- **RF-02** — O sistema DEVE responder igual, no conteúdo e no tempo, a e-mail inexistente e a senha
  errada.
- **RF-03** — O sistema DEVE emitir uma credencial de renovação de 14 dias, guardada pelo navegador num
  cookie só do host, inacessível a scripts, enviado só às rotas de sessão e nunca a outros subdomínios.
- **RF-04** — O sistema DEVE trocar a credencial de renovação a cada uso; a reutilização de uma já
  trocada DEVE encerrar toda a família de sessões daquele login.
- **RF-05** — O sistema DEVE encerrar a sessão atual (sair) e todas as sessões do usuário (sair de
  todos).
- **RF-06** — O sistema DEVE informar ao usuário logado os próprios dados (nome, e-mail, papéis), sem
  senha nem hash.
- **RF-07** — O sistema DEVE guardar senhas apenas com um algoritmo de hash resistente a força bruta e
  aceitar senhas de sistemas anteriores, regravando-as no formato atual no primeiro login.
- **RF-08** — O sistema DEVE recusar credenciais de acesso adulteradas, vencidas, assinadas por outra
  chave, com outro algoritmo, sem algoritmo ou de outro emissor.
- **RF-09** — O sistema DEVE permitir criar um administrador pelo terminal do servidor, com a senha
  digitada duas vezes e nunca exibida.
- **RF-10** — O sistema DEVE exigir para administradores senha de 12 a 128 caracteres.

## 4. Modelo de Domínio

- **Usuário:** nome, e-mail (único, minúsculo), hash da senha, papéis (`ALUNO`, `ADMIN`), ativo,
  criado em.
- **Sessão (credencial de renovação):** usuário, família (o login que a originou), hash da credencial,
  validade, trocada em, revogada em.

## 5. Estados e Fluxos

- Credencial de renovação: `ativa` → `trocada` (uso normal) ou `revogada` (saída, saída de todos,
  reutilização detectada). Vencida não renova.
- **Login:** e-mail e senha → confere → emite acesso + renovação (cookie).
- **Renovação:** cookie → confere a família → emite um par novo e marca o anterior como trocado.
- **Reutilização:** cookie já trocado → revoga a família inteira → 401.

## 6. Contratos de Interface

| Operação | Entrada | Saída |
|---|---|---|
| Entrar | `email`, `senha` | credencial de acesso, validade, usuário; cookie de renovação |
| Renovar | cookie de renovação | novo par (acesso no corpo, renovação no cookie) |
| Sair | cookie | cookie apagado |
| Sair de todos | credencial de acesso | todas as sessões revogadas |
| Quem sou eu | credencial de acesso | `id`, `nome`, `email`, `papeis` |

Hosts que recebem o cookie: `aluno.celke.uno` e `adm.celke.uno` (homologação:
`homolog-aluno.celke.uno` e `homolog-adm.celke.uno`), cada um só o seu.

## 7. Casos de Borda

- Duas renovações simultâneas com o mesmo cookie: só uma vence; a outra é tratada como reutilização.
- Usuário desativado com sessão aberta: a próxima renovação é recusada.
- Segredo de assinatura ausente ou curto: o sistema não sobe.
- Senha do sistema anterior: aceita uma vez e regravada; nunca guardada de novo no formato antigo.

## 8. Premissas Validadas

- O domínio `celke.uno` tem outras aplicações: por isso o cookie não usa o atributo de domínio.
- Não há login social nem "lembrar de mim" além dos 14 dias da renovação.

## 9. Riscos

- **Roubo do cookie de renovação:** mitigado pela troca a cada uso e pela revogação da família.
- **Enumeração de contas:** mitigada pela resposta igual em conteúdo e tempo (RF-02).
