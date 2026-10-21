#!/usr/bin/env bash
# Prepara uma VPS Ubuntu 24.04 nova para a plataforma. Rode UMA vez, como root, e mantenha esta
# sessão SSH aberta até confirmar o login do administrador num SEGUNDO terminal.
#
#   ADMIN_USUARIO=cesar \
#   ADMIN_CHAVE="ssh-ed25519 AAAA... seu@computador" \
#   DEPLOY_CHAVE="ssh-ed25519 AAAA... github-deploy" \
#   bash preparar-vps.sh
#
# O que faz: atualiza o sistema, instala o Docker oficial (com rotação de logs), cria o usuário
# administrador (sudo) e o usuário deploy (sem sudo, no grupo docker, com a chave presa ao script de
# deploy), deixa o SSH só por chave e sem root, liga o ufw (22, 80 e 443), o fail2ban, as atualizações
# automáticas e um swap de 2 GB, e cria /opt/plataforma-cursos.
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Rode como root." >&2; exit 1; }
: "${ADMIN_USUARIO:?defina ADMIN_USUARIO}" "${ADMIN_CHAVE:?defina ADMIN_CHAVE}" "${DEPLOY_CHAVE:?defina DEPLOY_CHAVE}"

export DEBIAN_FRONTEND=noninteractive
BASE=/opt/plataforma-cursos
# Script de deploy (instalado depois, pelo roteiro): fora de $BASE, numa pasta que o deploy não altera
SCRIPT_DEPLOY=/usr/local/bin/plataforma-cursos-deploy

echo "==> Atualizando o sistema"
apt-get update -q
apt-get upgrade -y -q
apt-get install -y -q ca-certificates curl ufw fail2ban unattended-upgrades

echo "==> Docker (repositório oficial)"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
# shellcheck disable=SC1091
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update -q
apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
# Logs dos containers com rotação: sem isso eles crescem até encher o disco
cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
systemctl restart docker

criar_usuario() {
  local usuario=$1 chave=$2
  id "$usuario" &>/dev/null || adduser --disabled-password --gecos "" "$usuario"
  install -d -m 700 -o "$usuario" -g "$usuario" "/home/$usuario/.ssh"
  echo "$chave" > "/home/$usuario/.ssh/authorized_keys"
  chown "$usuario:$usuario" "/home/$usuario/.ssh/authorized_keys"
  chmod 600 "/home/$usuario/.ssh/authorized_keys"
}

echo "==> Usuário administrador ($ADMIN_USUARIO): sudo e docker"
criar_usuario "$ADMIN_USUARIO" "$ADMIN_CHAVE"
usermod -aG sudo,docker "$ADMIN_USUARIO"
if [[ -t 0 ]]; then
  echo "Defina a senha do $ADMIN_USUARIO (pedida pelo sudo; o login continua só por chave):"
  passwd "$ADMIN_USUARIO"
else
  echo "AVISO: sem terminal para definir a senha do sudo; rode depois: passwd $ADMIN_USUARIO" >&2
fi

echo "==> Usuário deploy: sem sudo, no grupo docker, chave presa ao script de deploy"
# O grupo docker equivale a root. Por isso a chave do deploy só executa o script de deploy: sem shell,
# sem túnel e sem terminal, mesmo se ela vazar.
criar_usuario deploy \
  "command=\"$SCRIPT_DEPLOY\",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty $DEPLOY_CHAVE"
usermod -aG docker deploy

echo "==> SSH só por chave e sem root"
# O nome começa com 01 para valer antes dos arquivos da imagem (ex.: 50-cloud-init.conf)
cat > /etc/ssh/sshd_config.d/01-plataforma.conf <<SSH
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AllowUsers $ADMIN_USUARIO deploy
SSH
sshd -t
systemctl reload ssh

echo "==> Firewall (ufw): só 22, 80 e 443"
# O Docker contorna o ufw nas portas que publica: por isso só o Caddy publica portas
ufw default deny incoming
ufw default allow outgoing
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> fail2ban e atualizações automáticas de segurança"
systemctl enable --now fail2ban
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Swap de 2 GB"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/90-swappiness.conf
sysctl -q --system

echo "==> Pastas da plataforma em $BASE"
install -d -m 750 -o deploy -g deploy "$BASE" "$BASE/caddy" "$BASE/producao" "$BASE/homolog"

cat <<FIM

Pronto. ANTES de fechar esta sessão, abra outro terminal e confirme:
  ssh $ADMIN_USUARIO@<ip-da-vps>        (deve entrar sem senha)
  sudo -v                               (deve aceitar a senha definida)
Depois disso, root e senha deixam de entrar por SSH.
FIM
