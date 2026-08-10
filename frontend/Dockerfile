# ---------- 构建阶段:Node 20 ----------
FROM node:20-alpine AS build
WORKDIR /build

# 国内服务器网络慢可取消下一行注释换用 npmmirror 源
# RUN npm config set registry https://registry.npmmirror.com

COPY package*.json ./
RUN npm ci --no-fund --no-audit

COPY . .
RUN npm run build

# ---------- 运行阶段:Nginx 托管静态资源并反代后端 ----------
FROM nginx:alpine
COPY --from=build /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
