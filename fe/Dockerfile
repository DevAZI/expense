# syntax=docker/dockerfile:1

# ---- build stage -----------------------------------------------------------
# Vite で静的ファイル(dist)を作る。API は実行時に相対パス /api を叩くだけなので
# ビルド時の環境変数は不要（nginx が /api をバックエンドへプロキシする）。
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- run stage -------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
