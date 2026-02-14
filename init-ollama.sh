#!/bin/bash
# Initialize Ollama with required models

set -e

OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
MAIN_MODEL="${MAIN_MODEL:-llama3}"
EMBEDDING_MODEL="${EMBEDDING_MODEL:-llama3}"
MAX_RETRIES=30
RETRY_DELAY=2

echo "========================================"
echo "🚀 Ollama Model Initialization"
echo "========================================"
echo "Ollama URL: $OLLAMA_URL"
echo "Main Model: $MAIN_MODEL"
echo "Embedding Model: $EMBEDDING_MODEL"
echo ""

# Function to wait for Ollama to be ready
wait_for_ollama() {
    local retries=0
    echo "⏳ Waiting for Ollama to be ready..."

    while [ $retries -lt $MAX_RETRIES ]; do
        if curl -s "$OLLAMA_URL/api/tags" > /dev/null 2>&1; then
            echo "✅ Ollama is ready!"
            return 0
        fi

        retries=$((retries + 1))
        echo "   Attempt $retries/$MAX_RETRIES... (waiting ${RETRY_DELAY}s)"
        sleep $RETRY_DELAY
    done

    echo "❌ Ollama did not respond after $MAX_RETRIES attempts"
    return 1
}

# Function to check if model is available
has_model() {
    local model=$1
    curl -s "$OLLAMA_URL/api/tags" | grep -q "\"name\":\"$model\"" && return 0 || return 1
}

# Function to pull a model
pull_model() {
    local model=$1
    echo "📥 Pulling model: $model"
    curl -X POST "$OLLAMA_URL/api/pull" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$model\"}" \
        --progress-bar
    echo ""
}

# Wait for Ollama
if ! wait_for_ollama; then
    echo "❌ Failed to connect to Ollama. Make sure it's running."
    exit 1
fi

# List available models
echo ""
echo "📋 Currently available models:"
curl -s "$OLLAMA_URL/api/tags" | grep -o '"name":"[^"]*"' | sed 's/"name":"\([^"]*\)"/  - \1/' || echo "  (none)"
echo ""

# Pull main model if not available
if ! has_model "$MAIN_MODEL"; then
    echo "⚠️  Model not found: $MAIN_MODEL"
    pull_model "$MAIN_MODEL"
else
    echo "✅ Model already available: $MAIN_MODEL"
fi

# Pull embedding model if not available and different from main model
if [ "$EMBEDDING_MODEL" != "$MAIN_MODEL" ]; then
    if ! has_model "$EMBEDDING_MODEL"; then
        echo "⚠️  Model not found: $EMBEDDING_MODEL"
        pull_model "$EMBEDDING_MODEL"
    else
        echo "✅ Model already available: $EMBEDDING_MODEL"
    fi
else
    echo "✅ Using main model for embeddings: $MAIN_MODEL"
fi

# Final verification
echo ""
echo "📋 Final model list:"
curl -s "$OLLAMA_URL/api/tags" | grep -o '"name":"[^"]*"' | sed 's/"name":"\([^"]*\)"/  - \1/' || echo "  (none)"

echo ""
echo "========================================"
echo "✅ Ollama initialization complete!"
echo "========================================"

