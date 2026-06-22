# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------------
# Install deps and statically generate the Nuxt SPA with bun.
FROM oven/bun:1 AS builder

WORKDIR /app

# Install dependencies first for better layer caching. Either lockfile may be
# present depending on how the workspace was bootstrapped.
COPY package.json bun.lock* bun.lockb* ./
RUN bun install --frozen-lockfile || bun install

# Browser-visible API origin, baked into the static bundle at generate time.
# Nuxt maps NUXT_PUBLIC_API_BASE -> runtimeConfig.public.apiBase during the build.
ARG NUXT_PUBLIC_API_BASE=http://localhost:8080
ENV NUXT_PUBLIC_API_BASE=$NUXT_PUBLIC_API_BASE

# Build the static SPA. With ssr:false, `nuxt generate` emits a fully static
# site under .output/public (index.html + hashed client assets) — exactly what a
# plain static file server needs. `bun run build` (nuxt build) would instead
# produce a Nitro server bundle, so we generate here.
COPY . .
RUN bun run generate

# --- Final stage -------------------------------------------------------------
# Serve the prerendered static output with a tiny static file server. `serve` is
# installed at build time (not fetched at container start) and its -s flag does
# SPA fallback so client-side routing works on deep links / refreshes.
FROM oven/bun:1-slim AS runner

WORKDIR /app
ENV NODE_ENV=production

# Vendor the static server so the container needs no network at startup.
RUN bun install --global serve

COPY --from=builder /app/.output/public ./public

EXPOSE 3000
CMD ["serve", "public", "-l", "3000", "-s"]
