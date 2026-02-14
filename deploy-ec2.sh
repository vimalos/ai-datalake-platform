#!/bin/bash

################################################################################
# AI Data Lakehouse Assistant - AWS EC2 Deployment Script
#
# This script deploys the entire POC to AWS EC2 with:
# - All required tools: Git, Maven, Node.js, Docker, Docker Compose
# - All services: MinIO, Iceberg REST, Ollama (local LLM), Backend, Frontend
# - AWS Glue Catalog integration for metadata (no PostgreSQL needed)
# - Auto-configuration for public IP access
# - Health checks and service verification
#
# Prerequisites:
#   - AWS EC2 instance (Ubuntu 22.04 or 20.04)
#   - AWS credentials configured for Glue Catalog access
#
# Usage:
#   sudo bash deploy-ec2.sh https://github.com/yourusername/ai-datalake-platform.git
#
################################################################################

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print functions
print_header() {
    echo -e "${BLUE}═════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}═════════════════════════════════════════════════════════${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ️  $1${NC}"
}

# Check if running as root or with sudo
check_permissions() {
    if [ "$EUID" -ne 0 ]; then
        print_error "This script must be run as root or with sudo"
        exit 1
    fi
    print_success "Running with required permissions"
}

# Update system
update_system() {
    print_header "Step 1: Updating System Packages"
    apt-get update -y > /dev/null 2>&1
    apt-get upgrade -y > /dev/null 2>&1
    print_success "System packages updated"
}

# Install Docker
install_docker() {
    print_header "Step 2: Installing Docker"

    if command -v docker &> /dev/null; then
        print_info "Docker already installed: $(docker --version)"
    else
        curl -fsSL https://get.docker.com -o get-docker.sh
        sh get-docker.sh
        rm get-docker.sh
        print_success "Docker installed"
    fi

    # Add current user to docker group
    usermod -aG docker $SUDO_USER 2>/dev/null || true
}

# Install Docker Compose
install_docker_compose() {
    print_header "Step 3: Installing Docker Compose"

    if command -v docker-compose &> /dev/null; then
        print_info "Docker Compose already installed: $(docker-compose --version)"
    else
        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
        chmod +x /usr/local/bin/docker-compose
        print_success "Docker Compose installed"
    fi
}

# Install Java and Maven
install_java_maven() {
    print_header "Step 4: Installing Java & Maven"

    if command -v java &> /dev/null; then
        print_info "Java already installed: $(java -version 2>&1 | head -n 1)"
    else
        apt-get install -y openjdk-17-jdk > /dev/null 2>&1
        print_success "Java 17 installed"
    fi

    if command -v mvn &> /dev/null; then
        print_info "Maven already installed: $(mvn --version | head -n 1)"
    else
        apt-get install -y maven > /dev/null 2>&1
        print_success "Maven installed"
    fi
}

# Install Node.js and npm
install_nodejs() {
    print_header "Step 5: Installing Node.js & npm"

    if command -v node &> /dev/null; then
        print_info "Node.js already installed: $(node --version)"
    else
        # Install Node.js 18.x LTS
        curl -fsSL https://deb.nodesource.com/setup_18.x | bash - > /dev/null 2>&1
        apt-get install -y nodejs > /dev/null 2>&1
        print_success "Node.js installed: $(node --version)"
    fi

    if command -v npm &> /dev/null; then
        print_info "npm already installed: $(npm --version)"
    else
        print_error "npm should be installed with Node.js"
    fi
}

# Install Git
install_git() {
    print_header "Step 6: Installing Git"

    if command -v git &> /dev/null; then
        print_info "Git already installed: $(git --version)"
    else
        apt-get install -y git > /dev/null 2>&1
        print_success "Git installed"
    fi
}

# Clone repository
clone_repo() {
    print_header "Step 7: Cloning Repository"

    local REPO_URL="$1"
    local INSTALL_DIR="/opt/ai-datalake"

    if [ -d "$INSTALL_DIR" ]; then
        print_info "Repository already exists at $INSTALL_DIR"
        cd "$INSTALL_DIR"
        print_info "Pulling latest changes..."
        git pull origin main > /dev/null 2>&1 || git pull origin master > /dev/null 2>&1
    else
        mkdir -p /opt
        git clone "$REPO_URL" "$INSTALL_DIR"
        cd "$INSTALL_DIR"
        print_success "Repository cloned to $INSTALL_DIR"
    fi

    export INSTALL_DIR
}

# Build Backend (Spring Boot)
build_backend() {
    print_header "Step 8: Building Spring Boot Backend"

    cd "$INSTALL_DIR"

    print_info "Building backend with Maven (this may take 2-3 minutes)..."
    mvn clean package -DskipTests > /dev/null 2>&1

    if [ -f "target/ai-datalake-platform-1.0.0-SNAPSHOT.jar" ]; then
        print_success "Backend JAR built successfully"
    else
        print_error "Backend build failed"
        return 1
    fi
}

# Build Frontend (React)
build_frontend() {
    print_header "Step 9: Building React Frontend"

    cd "$INSTALL_DIR/frontend"

    print_info "Installing npm dependencies..."
    npm install > /dev/null 2>&1

    print_info "Building production frontend..."
    npm run build > /dev/null 2>&1

    if [ -d "build" ]; then
        print_success "Frontend built successfully"

        # Copy build to backend static resources
        mkdir -p "$INSTALL_DIR/src/main/resources/static"
        cp -r build/* "$INSTALL_DIR/src/main/resources/static/"
        print_success "Frontend deployed to backend static resources"
    else
        print_error "Frontend build failed"
        return 1
    fi
}

# Get public IP
get_public_ip() {
    # Try multiple methods to get public IP
    PUBLIC_IP=$(curl -s https://checkip.amazonaws.com || curl -s http://169.254.169.254/latest/meta-data/public-ipv4 || echo "localhost")
    echo "$PUBLIC_IP"
}

# Create/Update .env file
setup_environment() {
    print_header "Step 10: Setting Up Environment Variables"

    local PUBLIC_IP=$(get_public_ip)
    local ENV_FILE="$INSTALL_DIR/.env"

    # Create .env file configured for AWS Glue Catalog
    cat > "$ENV_FILE" << 'EOF'
# ═════════════════════════════════════════════════════════
# AI Data Lakehouse Assistant - Environment Configuration
# Configured for AWS Glue Catalog (No PostgreSQL needed)
# ═════════════════════════════════════════════════════════

# ─── MinIO Configuration (S3-Compatible Local Storage) ───
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001

# ─── Iceberg REST Catalog ───
ICEBERG_REST_PORT=8181

# ─── Ollama (Local LLM) ───
OLLAMA_PORT=11434
OLLAMA_HOST=0.0.0.0:11434
OLLAMA_MODEL=llama3
OLLAMA_EMBEDDING_MODEL=llama3

# ─── Spring Boot Backend ───
SPRING_PROFILES_ACTIVE=default

# ─── Spark Configuration (ENABLED for AWS Glue) ───
SPARK_ENABLED=true
SPARK_MASTER=local[*]
METASTORE_TYPE=glue
SPARK_WAREHOUSE=s3a://warehouse

# ─── AWS Configuration ───
# For local development with MinIO
AWS_REGION=us-east-1
AWS_GLUE_ENABLED=false
AWS_S3_BUCKET=warehouse
AWS_S3_WAREHOUSE_PATH=
AWS_S3_ENDPOINT=http://minio:9000
AWS_ACCESS_KEY_ID=minioadmin
AWS_SECRET_ACCESS_KEY=minioadmin
AWS_S3_PATH_STYLE=true

# For production with real AWS Glue Catalog, set:
# AWS_GLUE_ENABLED=true
# AWS_GLUE_CATALOG_ID=<your-aws-account-id>
# AWS_S3_BUCKET=<your-s3-bucket>
# AWS_S3_ENDPOINT=
# AWS_ACCESS_KEY_ID=<your-access-key>
# AWS_SECRET_ACCESS_KEY=<your-secret-key>
# AWS_S3_PATH_STYLE=false

# ─── LLM Configuration ───
OLLAMA_URL=http://ollama:11434
OLLAMA_MODEL=llama3
OLLAMA_EMBEDDING_MODEL=llama3

# ─── Iceberg & Storage ───
ICEBERG_CATALOG_URI=http://iceberg-rest:8181
S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY_ID=minioadmin
S3_SECRET_ACCESS_KEY=minioadmin

# ─── Backend Port ───
BACKEND_PORT=8080

# ─── React Frontend ───
FRONTEND_PORT=3000
REACT_APP_API_URL=http://REPLACE_WITH_PUBLIC_IP:8080/api

# ─── JVM Options (Required for Spark + Arrow) ───
JAVA_OPTS=-Xmx4g -XX:+UseG1GC --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED
EOF

    # Replace public IP in .env
    sed -i "s|REPLACE_WITH_PUBLIC_IP|$PUBLIC_IP|g" "$ENV_FILE"

    print_success "Environment configured with Public IP: $PUBLIC_IP"
    print_info ".env file created at $ENV_FILE"
    print_info "AWS Glue Catalog support enabled (using MinIO for local storage)"
}

# Start Docker Compose
start_services() {
    print_header "Step 11: Starting Docker Services"

    cd "$INSTALL_DIR"

    print_info "Starting services (this may take 2-3 minutes for Ollama model download)..."
    docker-compose up -d

    print_success "Docker services started"
}

# Wait for services to be healthy
wait_for_services() {
    print_header "Step 12: Waiting for Services to be Ready"

    local max_attempts=60
    local attempt=0

    # Wait for MinIO
    print_info "Waiting for MinIO..."
    while [ $attempt -lt $max_attempts ]; do
        if docker-compose exec -T minio curl -s -f http://localhost:9000/minio/health/live > /dev/null 2>&1; then
            print_success "MinIO is ready"
            break
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    if [ $attempt -eq $max_attempts ]; then
        print_error "MinIO did not start"
        return 1
    fi

    # Wait for Iceberg REST Catalog
    print_info "Waiting for Iceberg REST Catalog..."
    attempt=0
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f http://localhost:8181/v1/config > /dev/null 2>&1; then
            print_success "Iceberg REST Catalog is ready"
            break
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    # Wait for Ollama
    print_info "Waiting for Ollama (this may take several minutes for model download)..."
    attempt=0
    while [ $attempt -lt 120 ]; do
        if docker-compose exec -T ollama curl -s -f http://localhost:11434/api/tags > /dev/null 2>&1; then
            print_success "Ollama is ready"
            break
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 3
    done

    # Wait for Backend (Spring Boot)
    print_info "Waiting for Backend API..."
    attempt=0
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1 || curl -s -f http://localhost:8080/api/chat/health > /dev/null 2>&1; then
            print_success "Backend API is ready"
            break
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 3
    done
}

# Verify deployment
verify_deployment() {
    print_header "Step 13: Verifying Deployment"

    local PUBLIC_IP=$(get_public_ip)

    # Check services status
    echo ""
    print_info "Service Status:"
    docker-compose ps

    echo ""
    print_success "All services are running!"
}

# Display access information
display_access_info() {
    print_header "Step 14: Deployment Complete! 🎉"

    local PUBLIC_IP=$(get_public_ip)

    echo ""
    echo -e "${GREEN}🌐 ACCESS INFORMATION${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${YELLOW}Frontend (Web UI):${NC}"
    echo -e "  URL: ${GREEN}http://$PUBLIC_IP:3000${NC}"
    echo -e "  Direct access to AI Data Lakehouse Assistant"
    echo ""
    echo -e "${YELLOW}Backend API:${NC}"
    echo -e "  URL: ${GREEN}http://$PUBLIC_IP:8080/api${NC}"
    echo -e "  REST API endpoints"
    echo ""
    echo -e "${YELLOW}MinIO (S3 Console):${NC}"
    echo -e "  URL: ${GREEN}http://$PUBLIC_IP:9001${NC}"
    echo -e "  Credentials: minioadmin / minioadmin"
    echo -e "  View uploaded data and warehouse"
    echo ""
    echo -e "${YELLOW}Ollama (Local LLM):${NC}"
    echo -e "  URL: ${GREEN}http://$PUBLIC_IP:11434${NC}"
    echo -e "  Model: llama3"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${GREEN}📋 NEXT STEPS:${NC}"
    echo "  1. Open http://$PUBLIC_IP:3000 in your browser"
    echo "  2. Start with 'How to Use' section in the sidebar"
    echo "  3. Try example queries from the right panel"
    echo "  4. Explore databases, tables, and create new ones"
    echo ""
    echo -e "${YELLOW}💡 EXAMPLE QUERIES TO TRY:${NC}"
    echo "  • What databases are available?"
    echo "  • List tables in analytics"
    echo "  • Show schema for users"
    echo "  • Create table products with 3 fields"
    echo "  • Insert 5 records into products"
    echo "  • What is Apache Iceberg?"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${GREEN}📊 SHARING THIS POC:${NC}"
    echo "  Share this URL with your team for testing:"
    echo "  ${GREEN}http://$PUBLIC_IP:3000${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  IMPORTANT NOTES:${NC}"
    echo "  • First load may take a minute (Ollama initializing)"
    echo "  • Using AWS Glue Catalog integration (Spark enabled)"
    echo "  • All data stored in MinIO (S3-compatible)"
    echo "  • Local LLM (Ollama) with llama3 model"
    echo "  • Refer to README.md for full documentation"
    echo ""
}

# Cleanup function
cleanup_on_error() {
    print_error "Deployment failed!"
    print_info "To view logs, run:"
    echo "  docker-compose logs -f backend"
    echo "  docker-compose logs -f ollama"
    exit 1
}

# Main deployment flow
main() {
    # Get repository URL
    local REPO_URL="${1:-https://github.com/yourusername/ai-datalake-platform.git}"

    print_header "🚀 AI Data Lakehouse Assistant - AWS EC2 Deployment"
    print_info "Repository: $REPO_URL"
    print_info "Target: AWS Glue Catalog with Local LLM"
    echo ""

    # Set error trap
    trap cleanup_on_error ERR

    check_permissions
    update_system
    install_docker
    install_docker_compose
    install_java_maven
    install_nodejs
    install_git
    clone_repo "$REPO_URL"
    build_backend
    build_frontend
    setup_environment
    start_services
    wait_for_services
    verify_deployment
    display_access_info

    print_success "Deployment completed successfully! 🎉"
    print_info "All tools installed: Docker, Maven, Node.js, Git, Local LLM (Ollama)"
}

# Run main function
main "$@"

