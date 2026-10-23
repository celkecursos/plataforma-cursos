# Duas imagens a partir do mesmo build:
#   site (alvo `site`): site público em React renderizado no servidor (Node, porta 3000), que lê a
#                       API do próprio ambiente pela rede interna
#   web  (padrão):      Caddy interno com a área do aluno (8081) e o ADM (8082)
# O Caddy da VPS manda /api para a API, cursos.* para o site e o resto para o container web.

FROM node:26.11-bookworm-slim AS build
RUN apt-get update -q && apt-get install -y -q --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# O lock guarda o kole-react como git+ssh; o repositório é público e é baixado por HTTPS
RUN git config --global url."https://github.com/".insteadOf "ssh://git@github.com/"
WORKDIR /web
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build
# O servidor do site só precisa das dependências de produção
RUN npm prune --omit=dev --no-audit --no-fund

# Site público: o servidor do React Router com o build do site
FROM node:26.11-alpine AS site
# Correções do sistema; npm e yarn saem da imagem (o servidor não instala nada)
RUN apk upgrade --no-cache \
    && rm -rf /usr/local/lib/node_modules/npm /usr/local/bin/npm /usr/local/bin/npx \
        /opt/yarn-* /usr/local/bin/yarn /usr/local/bin/yarnpkg
WORKDIR /app
COPY --from=build /web/package.json ./
COPY --from=build /web/node_modules ./node_modules
COPY --from=build /web/dist/site ./dist/site
# O build grava a pasta dos arquivos do navegador relativa ao app (../dist/site/client): o servidor
# roda de /app/site
WORKDIR /app/site
USER node
ENV NODE_ENV=production PORT=3000
EXPOSE 3000
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=20 \
    CMD wget -q -O /dev/null http://127.0.0.1:3000/saude || exit 1
CMD ["node", "../node_modules/@react-router/serve/bin.cjs", "../dist/site/server/index.js"]

FROM caddy:2.11.7-alpine AS web
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /web/dist/aluno /srv/aluno
COPY --from=build /web/dist/adm /srv/adm
EXPOSE 8081 8082
HEALTHCHECK --interval=5s --timeout=3s --retries=20 \
    CMD wget -q --spider http://127.0.0.1:8081/ && wget -q --spider http://127.0.0.1:8082/ || exit 1
