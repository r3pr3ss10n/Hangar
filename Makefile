# Hangar — developer task runner.
# Run `make help` for the target list.

.DEFAULT_GOAL := help

# Load .env (if present) so DB/dev targets see HANGAR_* and POSTGRES_* values.
ifneq (,$(wildcard .env))
include .env
export
endif

.PHONY: help gen-key sqlc up down migrate backend frontend test build tidy

help: ## Show this help.
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(firstword $(MAKEFILE_LIST)) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

gen-key: ## Print a fresh 32-byte AES key (base64) for HANGAR_ENCRYPTION_KEY.
	@openssl rand -base64 32

sqlc: ## Regenerate the type-safe DB layer from queries/migrations.
	cd backend && sqlc generate

up: ## Build and start the full stack in the background.
	docker compose up -d --build

down: ## Stop the stack and remove its containers.
	docker compose down

migrate: ## Apply DB migrations (the server runs goose on startup).
	docker compose up -d postgres
	docker compose up -d backend

backend: ## Run the API server locally (auto-migrates on startup).
	cd backend && go run ./cmd/server

frontend: ## Run the Nuxt SPA dev server.
	cd frontend && bun run dev

test: ## Run backend Go tests and verify the frontend build.
	cd backend && go test ./...
	cd frontend && bun run build

build: ## Build the backend binary and the frontend static bundle.
	cd backend && go build -o bin/server ./cmd/server
	cd frontend && bun run build

tidy: ## Tidy the Go module.
	cd backend && go mod tidy
