# syntax=docker/dockerfile:1.7
# Frontend ShopVi: build Angular SSR, chạy server Node (Express) đã bundle — không cần node_modules ở runtime.

# ---------- build ----------
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---------- runtime ----------
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production PORT=4000
COPY --from=build --chown=node:node /src/dist/shopvi-frontend ./dist/shopvi-frontend
USER node
EXPOSE 4000
# API_ORIGIN: backend trong mạng nội bộ (SSR gọi trực tiếp); SITE_URL: URL công khai cho sitemap / robots
HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=5 \
    CMD wget -qO- http://localhost:4000/robots.txt > /dev/null || exit 1
CMD ["node", "dist/shopvi-frontend/server/server.mjs"]
