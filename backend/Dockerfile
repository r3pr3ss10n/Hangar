# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------------
# Compile a static, CGO-free server binary with the pinned Go toolchain.
FROM golang:1.26 AS builder

WORKDIR /src

# Download dependencies first so this layer caches independently of source edits.
COPY go.mod go.sum ./
RUN go mod download

# Build the server. CGO is disabled to produce a fully static binary that runs
# on the distroless/static base below.
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/server ./cmd/server

# Pre-create the data dir owned by the distroless nonroot uid (65532) so the
# named volume mounted at /data inherits writable ownership on first init —
# otherwise the nonroot server can't mkdir tus/cache subdirs under it.
RUN mkdir -p /out/data && touch /out/data/.keep

# --- Final stage -------------------------------------------------------------
# distroless/static carries only CA certificates, tzdata, and a nonroot user —
# no shell, no package manager — keeping the image tiny and the attack surface low.
FROM gcr.io/distroless/static:nonroot

WORKDIR /app
COPY --from=builder /out/server /app/server

# Scratch space for tus temp files and the download cache (HANGAR_DATA_DIR),
# pre-owned by nonroot (65532) so the mounted volume is writable.
COPY --from=builder --chown=65532:65532 /out/data /data
VOLUME ["/data"]
ENV HANGAR_DATA_DIR=/data

EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/app/server"]
