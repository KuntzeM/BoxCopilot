FROM node:24-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci || npm install
COPY . .

# Build-time environment variable for Vite
ARG VITE_API_BASE_URL=http://backend:8080
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}

RUN npm run build

FROM nginx:1.30-alpine
WORKDIR /usr/share/nginx/html
COPY --from=builder /app/dist ./
# The image entrypoint renders /etc/nginx/templates/*.template into /etc/nginx/conf.d/ (suffix stripped) at start.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.inc.template /etc/nginx/templates/security-headers.inc.template
# Substitute only these; nginx's own $uri, $host, ... must survive. Defaults are the safe ones; compose overrides.
ENV NGINX_ENVSUBST_FILTER="^(API_ORIGIN|CSP_MODE|HSTS_ENABLED)$"     API_ORIGIN="" CSP_MODE=report HSTS_ENABLED=false
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
