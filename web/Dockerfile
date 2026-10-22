# Duas imagens a partir do mesmo build:
#   site (alvo `site`): gera o site público a partir da API do ambiente e grava num volume
#   web  (padrão):      Caddy interno com a área do aluno (8081), o ADM (8082) e o site gerado (8083)
# O Caddy da VPS manda /api para a API e o resto para o container web.

FROM node:24.11-bookworm-slim AS build
RUN apt-get update -q && apt-get install -y -q --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# O lock guarda o kole-react como git+ssh; o repositório é público e é baixado por HTTPS
RUN git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Gerador do site: só Node, sem dependências (o Node 24 roda o TypeScript direto)
FROM node:24.11-alpine AS site
WORKDIR /app
COPY site/html.ts site/paginas.ts site/gerar.ts ./site/
COPY site/assets ./site/assets
COPY --from=build /web/node_modules/@celke/kole-react/dist/tokens.css ./site/assets/tokens.css
# O volume /site nasce com o dono desta pasta
RUN mkdir /site && chown node:node /site
USER node
ENV SITE_DESTINO=/site
HEALTHCHECK --interval=5s --timeout=3s --start-period=60s --retries=20 \
    CMD test -e /site/atual/index.html || exit 1
CMD ["node", "site/gerar.ts"]

FROM caddy:2.11.7-alpine AS web
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist/aluno /srv/aluno
COPY --from=build /web/dist/adm /srv/adm
EXPOSE 8081 8082 8083
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
    CMD wget -q --spider http://127.0.0.1:8081/ && wget -q --spider http://127.0.0.1:8082/ || exit 1
