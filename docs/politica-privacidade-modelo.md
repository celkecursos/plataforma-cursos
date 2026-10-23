# Modelo de política de privacidade

Texto de partida para publicar em **ADM → Termos → Política de privacidade**. Troque o que está entre
`[colchetes]` pelos dados da empresa e revise com quem cuida do jurídico antes de publicar: o modelo
descreve o que a plataforma faz de fato (dados, finalidades, prazos e direitos), não substitui a
análise do caso.

Os prazos abaixo são os que o sistema aplica. Se mudar um prazo na configuração
(`application.yaml`), mude também o texto publicado.

---

```markdown
# Política de privacidade

Última versão publicada nesta página. A [Razão social], CNPJ [00.000.000/0001-00], com sede em
[endereço] ("nós"), é a controladora dos dados pessoais tratados na Plataforma de Cursos. Encarregado
pelo tratamento de dados (DPO): [nome], [e-mail do encarregado].

## 1. Quais dados tratamos e para quê

| Dados | Para quê | Base legal (LGPD, art. 7º) |
|---|---|---|
| Nome, e-mail e senha (guardada só como hash) | Criar e manter a sua conta, entrar, recuperar a senha | Execução de contrato |
| Foto de perfil (opcional) | Mostrar no fórum e no seu perfil | Consentimento (você envia e remove quando quiser) |
| Matrículas, progresso nas aulas, perguntas e respostas no fórum, certificados | Prestar o curso: liberar o conteúdo, retomar de onde parou, responder dúvidas, emitir e validar o certificado | Execução de contrato |
| Pedidos (curso, valor, forma de pagamento, situação, e-mail e nome do comprador) | Vender o curso e liberar o acesso depois do pagamento | Execução de contrato e obrigação legal |
| Notificações do meio de pagamento (Hotmart) | Confirmar pagamentos, reembolsos e disputas | Execução de contrato |
| Endereço IP, navegador, data e hora das entradas na conta e dos acessos às aulas em vídeo | Segurança da conta (bloqueio de tentativas) e guarda dos registros de acesso | Obrigação legal (Marco Civil da Internet, art. 15) e legítimo interesse |
| Aceites dos termos de uso e desta política (com IP e navegador) | Comprovar o seu consentimento | Obrigação legal (LGPD, art. 8º) |
| Registro das alterações feitas na plataforma (quem, o quê, quando, de onde) | Segurança, prevenção a fraudes e defesa em processos | Legítimo interesse e exercício regular de direitos |

Não vendemos dados pessoais e não usamos os seus dados para publicidade.

## 2. Com quem compartilhamos

- **Meio de pagamento** ([Hotmart]): recebe o seu nome e e-mail para preencher o checkout e nos avisa
  do resultado do pagamento. O tratamento dos dados de pagamento segue a política deles.
- **Envio de e-mails** ([provedor de e-mail]): recebe o seu e-mail e o conteúdo das mensagens que a
  plataforma envia (confirmação, recuperação de senha, matrícula).
- **Hospedagem** ([provedor da VPS]): os servidores onde ficam o banco, os vídeos e os backups, no
  [Brasil / país].
- Autoridades, quando a lei ou uma ordem judicial exigir.

## 3. Por quanto tempo guardamos

| Dados | Prazo |
|---|---|
| Conta (nome, e-mail, senha, foto), progresso e certificados | Enquanto a conta existir. Na exclusão da conta, apagados |
| Perguntas e respostas no fórum | Enquanto a turma existir; na exclusão da conta, ficam sem o seu nome ("Conta excluída") |
| Matrículas e pedidos (valor, situação, datas) | 5 anos depois da compra (prazo de prescrição do Código de Defesa do Consumidor, art. 27, e guarda fiscal). Na exclusão da conta, o e-mail e o nome saem do pedido |
| Corpo das notificações do meio de pagamento (inclui e-mail e nome do comprador) | 180 dias |
| Entradas na conta (e-mail, IP, navegador, resultado) e acessos às aulas em vídeo (IP, navegador) | 6 meses (Marco Civil da Internet, art. 15) |
| Registro das alterações na plataforma (inclui e-mail, IP e os valores antes e depois de cada alteração, sem senhas nem segredos) | 5 anos |
| Aceites dos termos e desta política | Pelo tempo necessário para comprovar o consentimento |
| Logs técnicos dos servidores (IP, endereço acessado, data e hora) | 180 dias |
| Cópias de segurança (backups) | Até 6 meses (diárias por 7 dias, semanais por 4 semanas, mensais por 6 meses); um dado apagado da plataforma some dos backups nesse prazo |

## 4. Seus direitos (LGPD, art. 18)

No seu perfil, em **Privacidade**:

- **Baixar meus dados**: uma cópia, em arquivo JSON, de tudo o que guardamos sobre você.
- **Excluir minha conta**: apaga o seu nome, e-mail, senha, foto, progresso e certificados e encerra
  todas as sessões. O que a lei manda guardar (item 3) fica pelo prazo indicado, sem o seu nome nem o
  seu e-mail na conta. Você recebe a confirmação por e-mail.

Para corrigir dados, use o seu perfil. Para os demais direitos (confirmação do tratamento, informação
sobre compartilhamento, revogação do consentimento, revisão de decisões, petição à ANPD), escreva para
[e-mail do encarregado]. Respondemos em até 15 dias.

## 5. Segurança

Conexões sempre em HTTPS; senhas guardadas só como hash (Argon2); verificação em duas etapas para os
administradores; segredos de terceiros cifrados; acesso aos vídeos e arquivos só por links temporários;
registro de todas as alterações; backups diários cifrados. Em caso de incidente que possa causar risco
ou dano relevante, avisamos você e a ANPD.

## 6. Cookies

Usamos um único cookie, necessário para manter você conectado (sessão), `HttpOnly` e restrito à
plataforma. Não usamos cookies de publicidade nem de rastreamento.

## 7. Mudanças nesta política

Quando esta política mudar, você será avisado ao entrar e precisará aceitar a nova versão para
continuar usando a plataforma.
```
