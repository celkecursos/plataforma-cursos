# Operação

Rotina da plataforma depois que a VPS está no ar ([roteiro da VPS](vps.md)): backup e restauração,
monitoramento e alertas, logs, espaço em disco, saída para um S3 externo e a revisão de segurança.

## 1. Backup diário

`infra/scripts/backup.sh <ambiente>` guarda, com o [restic](https://restic.net), num repositório S3
**fora da VPS** (Cloudflare R2 ou Backblaze B2):

- o dump do PostgreSQL (`pg_dump --format=custom`), por stdin, sem arquivo temporário no disco;
- os arquivos do Garage, com o snapshot consistente dos metadados (`garage meta snapshot`); o SQLite em
  uso fica de fora.

Retenção: 7 diários, 4 semanais e 6 mensais por ambiente. O restic cifra tudo com a `RESTIC_PASSWORD`.

### Configuração (uma vez, na VPS)

1. Crie um bucket privado no R2 (ou B2) só para os backups e uma chave com acesso apenas a ele.
2. Na VPS, como `deploy`:

   ```bash
   sudo install -o root -g root -m 755 /tmp/backup.sh /usr/local/bin/plataforma-cursos-backup
   install -m 600 /dev/null /opt/plataforma-cursos/backup.env
   nano /opt/plataforma-cursos/backup.env
   ```

   ```bash
   RESTIC_REPOSITORY=s3:https://<conta>.r2.cloudflarestorage.com/plataforma-cursos-backup
   RESTIC_PASSWORD=          # openssl rand -base64 48 — guarde também no gerenciador de senhas
   AWS_ACCESS_KEY_ID=
   AWS_SECRET_ACCESS_KEY=
   ```

3. Agendamento (`crontab -e` do `deploy`), fora do horário de aula:

   ```cron
   30 3 * * * /usr/local/bin/plataforma-cursos-backup producao >> /opt/plataforma-cursos/backup.log 2>&1
   45 3 * * * /usr/local/bin/plataforma-cursos-backup homolog  >> /opt/plataforma-cursos/backup.log 2>&1
   ```

> Sem a `RESTIC_PASSWORD` o backup não abre. Ela fica em dois lugares: no `backup.env` da VPS e no
> gerenciador de senhas do responsável.

## 2. Restauração

`infra/scripts/restaurar.sh <ambiente> [snapshot-postgres] [snapshot-garage]` para a API e o worker,
recria o banco a partir do dump, troca os arquivos do Garage pelos do backup (com os metadados do
snapshot) e sobe tudo de novo. Pede o nome do ambiente para confirmar (ou `CONFIRMAR=sim`).

```bash
docker run --rm --env-file /opt/plataforma-cursos/backup.env restic/restic:0.19.1 snapshots   # escolher
restaurar.sh homolog                                                                         # o mais recente
```

Se algo falhar no meio, a API e o worker **continuam parados de propósito** (subir com o banco pela
metade é pior): corrija e rode de novo.

O dump traz as tabelas, não os papéis do PostgreSQL: a subida depois da restauração passa pelo serviço
`migracao`, que recria o usuário da aplicação e as permissões dele.

### Teste de restauração (feito e repetido a cada trimestre)

Testado na stack de e2e, com um repositório restic local:

```bash
export MSYS_NO_PATHCONV=1 ENV_BACKUP=<backup.env com RESTIC_REPOSITORY=/repo> \
       PROJETO=plataforma-cursos-e2e RESTIC_EXTRA="-v restic-teste:/repo"
infra/scripts/backup.sh e2e
# estrago: UPDATE cursos SET titulo = 'ESTRAGADO'; e apagar /garage/data do volume
CONFIRMAR=sim infra/scripts/restaurar.sh e2e
```

Resultado: os títulos dos 7 cursos voltaram, os 11 objetos do bucket baixaram pela API S3 e a API e o
worker subiram saudáveis. O procedimento trimestral repete o teste na **homologação** com o backup da
produção mais recente.

## 3. Monitoramento e alertas

| O quê | Como |
|---|---|
| Site fora do ar | Monitor externo (UptimeRobot ou Better Stack, plano gratuito) a cada 5 minutos em `https://aluno.celke.uno/actuator/health` e `https://cursos.celke.uno`, com aviso por e-mail e celular |
| Job que desistiu (conversão de vídeo, por exemplo) | O worker verifica a cada 15 minutos os jobs que foram para `ERRO` e envia um e-mail para `ALERTA_EMAIL` com o resumo |
| Pagamento contestado ou notificação com erro | Chargeback, disputa aberta e notificação de pagamento que foi para `ERRO` geram um e-mail para `ALERTA_EMAIL` na hora (veja `docs/pagamentos.md`) |
| Disco quase cheio | O mesmo verificador do worker mede o disco da VPS (o sistema de arquivos do container é o do host) e alerta abaixo de 15% livre, repetindo a cada 6 horas enquanto não melhorar |
| Container parado | `restart: unless-stopped` e o healthcheck de cada serviço; o deploy só conclui com tudo saudável |

`ALERTA_EMAIL` fica no `.env` de cada ambiente; em branco, os alertas do worker ficam desligados.
Falha no envio de e-mail não gera alerta por e-mail (iria pela mesma fila): aparece no log do worker.

## 4. Logs e limpeza

- **Logs do Docker** com rotação em todos os serviços (`json-file`, 5 arquivos de 10 MB por container):
  no máximo ~250 MB por ambiente, poucos dias de histórico. Os logs da API e do worker são JSON (ECS) com o id de correlação e o `@timestamp` no horário de Brasília (`-03:00`); o site e o `deploy.log` usam o mesmo fuso.
- **Logs persistentes da API e do worker**: além do console, cada um grava o mesmo JSON em
  `/var/log/plataforma-cursos/api.log` e `worker.log`, no volume `logs` do ambiente (sobrevive a deploy e
  recriação do container). Um arquivo por dia de Brasília, comprimido (`api.log.2026-10-22.0.gz`), novo
  arquivo a cada 50 MB, guardado por **180 dias** ou até **2 GB por serviço**, o que vier primeiro. Para
  ler, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api ls -lh /var/log/plataforma-cursos
  docker compose -p plataforma-cursos-producao -f compose.prod.yml exec api tail -n 100 /var/log/plataforma-cursos/api.log
  # Um dia antigo, procurando um id de requisição (funciona com o container parado)
  docker run --rm -v plataforma-cursos-producao_logs:/logs:ro alpine \
    sh -c 'zcat /logs/api.log.2026-10-22.*.gz | grep 7f1c2a90'
  ```

  O volume não entra no backup diário (o que importa guardar por anos está no banco: a auditoria).
- **Uploads abandonados** (`uploads/` do bucket): a regra de expiração do bucket apaga em 2 dias, e os
  uploads em partes não concluídos em 1 dia. Os envios aceitos saem de `uploads/` na confirmação.
- **Log de acesso aos vídeos**: o worker apaga todo dia o que passou de 180 dias.
- **Auditoria de login** (e-mail, IP, navegador de cada tentativa): o worker apaga todo dia o que passou
  de 180 dias (`app.auth.auditoria-login.retencao`).
- **Dados da homologação**: quando acumular lixo de testes,
  `docker compose -p plataforma-cursos-homolog -f compose.prod.yml -f compose.homolog.yml down -v`
  e o próximo deploy da `homolog` recria tudo vazio (crie o admin de novo com `criar-admin`).

## 4.1 Auditoria

A tabela `auditoria` guarda cada inclusão, alteração e exclusão feita pela aplicação nos dados (ADM, área
do aluno, cadastro e worker): **quem** (usuário do token; vazio para visitante e sistema), **o quê**
(entidade e id), a **operação**, o **antes e o depois** dos campos (na alteração, só os que mudaram),
**de onde** (rota da requisição, como `PUT /api/adm/cursos/{id}`, ou o job da fila, como
`job pagamento.processar-evento`), **IP**, navegador, id da requisição e a data.

- Consulta no ADM: **Segurança → Auditoria**, com filtro por entidade, id, operação e e-mail do usuário.
- Dados sensíveis (hash de senha, segredos do meio de pagamento, token do webhook) aparecem como `***`:
  a trilha mostra que mudaram, nunca o valor.
- Ficam fora: progresso das aulas (muda a cada poucos segundos), log de acesso a vídeo, auditoria de
  login, notificações de pagamento (já são registros próprios) e tokens de sessão e de e-mail. Mudanças
  feitas direto em SQL (fila, atualizações em lote) também não passam pela trilha.
- O registro entra na mesma transação da mudança: o que foi desfeito não aparece.
- **Só inclusão**: o banco recusa `UPDATE` e `DELETE` na tabela (gatilho). A única exclusão é a retenção,
  feita pelo worker aos domingos, que apaga o que passou de **5 anos** (`app.auditoria.retencao`).
- Login e tentativas de login continuam na `auditoria_login`; acesso a vídeo na `acessos_video`.

## 4.2 LGPD: direitos do titular e retenção

- **Baixar meus dados** (`GET /api/perfil/dados`, perfil do aluno → Privacidade): JSON com a conta e uma
  seção por módulo (`matriculas`, `progresso`, `forum`, `certificados`, `pedidos`, `aceitesTermos`,
  `entradas`, `acessosVideo`). Cada módulo responde pelas próprias tabelas (`shared/lgpd/DadosPessoais`):
  módulo novo com dado pessoal implementa a interface.
- **Excluir minha conta** (`POST /api/perfil/exclusao`, senha + "EXCLUIR"; administradores não se
  excluem por aqui): aviso por e-mail para o endereço da conta; apagados progresso, certificados (com o
  PDF), foto, sessões, links de e-mail e segundo fator; conta anonimizada (`Conta excluída`,
  `excluida-<id>@conta-excluida.invalid`, sem senha, inativa); pedidos sem e-mail e nome. Ficam, pelo
  prazo legal: matrículas e pedidos (5 anos), aceites dos termos, entradas e acessos aos vídeos (180
  dias), a trilha de auditoria (5 anos, com o antes da anonimização) e os backups (até 6 meses).
- **Política de privacidade**: o modelo em [`politica-privacidade-modelo.md`](politica-privacidade-modelo.md)
  traz os dados, as finalidades, as bases legais e os prazos acima; publique pelo ADM → Termos depois da
  revisão jurídica. Mudou um prazo na configuração? Mude o texto publicado.

| Dado | Retenção | Onde |
|---|---|---|
| Trilha de auditoria | 5 anos | `app.auditoria.retencao` (worker, semanal) |
| Auditoria de login | 180 dias | `app.auth.auditoria-login.retencao` (worker, diária) |
| Acessos aos vídeos | 180 dias | `app.video.retencao-log-acesso` (worker, diária) |
| Corpo das notificações de pagamento | 180 dias | `app.pagamentos.retencao-payload` (worker) |
| Logs da API e do worker | 180 dias ou 2 GB | volume `logs` |
| Backups | 7 diários, 4 semanais, 6 mensais | `backup.sh` (restic) |

## 5. Quanto vídeo cabe no disco

VPS de 100 GB NVMe. Reservas: sistema e imagens Docker (~15 GB), PostgreSQL e logs (~5 GB), uploads em
andamento (~10 GB) e os 15% livres do alerta (15 GB). Sobram **~55 GB para o HLS** (produção e
homologação juntas).

Cada hora de aula guarda as duas variantes (720p e 1080p) mais o áudio de cada uma:

| Conteúdo | Taxa média (720p + 1080p + áudio) | GB por hora | Horas em 55 GB |
|---|---|---|---|
| Tela de código, slides (o típico do curso) | ~1,1 Mbps | ~0,5 | ~110 h |
| Câmera e tela misturadas | ~3,9 Mbps | ~1,8 | ~30 h |
| Pior caso (sempre no teto: 2,8 + 5 Mbps) | ~8,1 Mbps | ~3,6 | ~15 h |

A conta do capítulo de limites da VPS usa o `segundosProcessamentoPorMinuto` e o `tamanhoFinal` que o
ADM mostra para cada vídeo: medir com os vídeos reais e atualizar esta tabela.

## 6. Saída para um S3 externo

Quando o HLS passar de ~40 GB, ou se a banda da VPS virar gargalo, os vídeos vão para o Cloudflare R2
(sem taxa de saída) ou o Backblaze B2. Como a aplicação só fala S3 pelo AWS SDK:

1. Criar o bucket no R2, a chave da aplicação e o CORS para `aluno.celke.uno` e `adm.celke.uno`.
2. Copiar o conteúdo: `rclone sync garage:midia r2:midia` (com as duas origens no `rclone.conf`).
3. No `.env` da produção, trocar `S3_ENDPOINT` e `S3_ENDPOINT_PUBLICO` (endpoint do R2 ou domínio
   próprio `media.celke.uno` apontado para o bucket), `S3_BUCKET`, `S3_ACCESS_KEY` e `S3_SECRET_KEY`.
4. Rodar o `rclone sync` de novo (o que entrou durante a cópia) e reiniciar a API e o worker.
5. Depois de uma semana sem problema, tirar o Garage da produção e o host de mídia do Caddy.

## 7. Segurança

### OWASP ASVS nível 1 — revisão da API

| Capítulo | Situação |
|---|---|
| V2 Autenticação | Senha mínima de 10 (aluno) / 12 (admin), máximo 128, Argon2; recusa as senhas mais comuns e vazadas (lista de ~117 mil), as que contêm o nome ou o e-mail e as sequências e repetições; medidor de força nas telas; **segundo fator (TOTP) obrigatório para administradores**, com códigos de recuperação de uso único; bloqueio após falhas por e-mail e por e-mail + IP (o código errado do segundo fator conta como falha); limite por IP nas rotas de conta; recuperação com token de uso único e resposta igual para e-mail existente ou não |
| V3 Sessão | Access token de 10 minutos; refresh opaco em cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`, sem `Domain`; rotação com detecção de reuso (revoga a família); logout e "sair de todos"; troca de senha encerra todas as sessões; o ADM bloqueia a conta ou encerra as sessões e o access token já emitido deixa de valer na hora (conferido a cada requisição, 30 s em memória); token de link de e-mail sai da barra de endereço depois de lido |
| V4 Controle de acesso | Tudo negado por padrão; rotas públicas declaradas com método e conferidas por um teste que varre todas as rotas; papel no filtro e no `@PreAuthorize`; acesso ao conteúdo só pela `AcessoAulaPolicy`; recursos de outro usuário respondem 404; chaves de upload com o id do dono (IDOR); limite por usuário nas gravações (120 por minuto) e no checkout (10 por hora); a compra só abre o pedido com a confirmação do aluno; checkout só em HTTPS nos domínios da Hotmart |
| V5 Entrada e saída | Bean Validation em todo corpo; Markdown com HTML escapado e sanitizado (lista fechada); conteúdo dos arquivos conferido pela assinatura real; imagens regravadas sem metadados; ffmpeg por lista de argumentos |
| V7 Erros e logs | ProblemDetail sem stack trace nem causa; id de correlação; auditoria de login; log de acesso aos vídeos com retenção |
| V8 Dados | Bucket privado, só URLs pré-assinadas curtas; certificado validado sem e-mail nem ids; segredos só nos `.env` e nos secrets |
| V9 Comunicação | HTTPS automático pelo Caddy, HSTS; CORS do bucket com as origens exatas |
| V10 Código malicioso / V14 Configuração | Trivy no pipeline (críticos com correção quebram o build); `npm audit` das dependências do navegador (alta ou crítica quebra o build); Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker; versões fixas de imagens e dependências; usuário não-root nos containers; Swagger e Actuator fora da produção; a API e o worker entram no banco com um usuário que só lê e grava dados (o dono do schema fica para as migrations) |

### Segundo fator dos administradores

- Todo usuário com o papel `ADMIN` entra em duas etapas: a senha certa devolve um desafio (válido por
  5 minutos, sem sessão nem cookie) e a sessão só abre com o código de 6 dígitos do aplicativo
  autenticador (Google Authenticator, Microsoft Authenticator, 1Password...).
- No primeiro acesso, a tela mostra o QR code; o primeiro código certo ativa o segundo fator e mostra
  **uma vez** os 10 códigos de recuperação (cada um vale uma entrada sem o celular).
- O segredo fica cifrado no banco (AES-256-GCM, chave `TOTP_CHAVE` do `.env`); o mesmo código não vale
  duas vezes; cada etapa fica na auditoria de login (`SEGUNDO_FATOR_PENDENTE`, `SEGUNDO_FATOR_INVALIDO`,
  `SEGUNDO_FATOR_CADASTRADO`, `CODIGO_RECUPERACAO`).
- Perdeu o celular e os códigos? Na VPS, como `deploy`:

  ```bash
  cd /opt/plataforma-cursos/producao
  docker compose -p plataforma-cursos-producao -f compose.prod.yml run --rm api redefinir-2fa admin@celke.com.br
  ```

  O comando apaga o segundo fator e encerra as sessões; o próximo login pede o cadastro de novo.
- Trocar a `TOTP_CHAVE` invalida todos os segredos: rode o `redefinir-2fa` para cada administrador.

### Usuário do banco da aplicação

- O serviço `migracao` (mesma imagem da API, comando `migrar`) roda a cada deploy com o dono do schema
  (`POSTGRES_USER`), aplica as migrations e, no `afterMigrate.sql`, cria (na primeira vez) e libera o
  usuário da aplicação (`POSTGRES_APP_USER`): `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas, uso
  das sequências, sem criar, alterar ou apagar tabelas e sem acesso ao histórico do Flyway.
- A API e o worker sobem só depois dele, com `POSTGRES_APP_USER`/`POSTGRES_APP_PASSWORD`. Uma injeção
  de SQL ou uma conta roubada da aplicação não derruba tabelas nem apaga a trilha de auditoria (o
  gatilho continua barrando `UPDATE` e `DELETE` nela).
- O `deploy.sh` acrescenta ao `.env` as variáveis novas (`POSTGRES_APP_USER`, `POSTGRES_APP_PASSWORD`,
  `TOTP_CHAVE`) que ainda não existirem, com valores aleatórios.

### Varreduras

- **Trivy** (a cada PR, imagens da API e do worker): a primeira varredura apontou o Tomcat 11.0.24
  (3 CVEs críticos) e o Jackson 3.1.5 / 2.21.5; o `pom.xml` fixa as versões corrigidas até o Spring Boot
  trazê-las.
- **OWASP ZAP baseline** na stack local de e2e (rodar também na homologação a cada versão):
  0 falhas, 65 regras sem achado; o aviso de `Cross-Origin-Resource-Policy` foi resolvido no Caddy
  (`same-site`). "Non-Storable Content" é esperado: a API responde sem cache.

  ```bash
  docker run --rm ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t https://homolog-aluno.celke.uno/api/cursos -I
  ```
