# =============================================================================
# Stage 1: Build — install dependencies and compile the Vite/React app
# =============================================================================
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first (layer cache — only re-run install if lockfile changes)
COPY package.json package-lock.json* ./

RUN npm ci --prefer-offline

# Copy source and build the production bundle
COPY . .
RUN npm run build

# =============================================================================
# Stage 2: Serve — nginx serves the static dist/ output
# =============================================================================
FROM nginx:alpine AS runtime

# Remove default nginx static assets
RUN rm -rf /usr/share/nginx/html/*

# Copy compiled assets from the build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy SPA-aware nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
