#!/bin/bash

################################################################################
# AI Data Lakehouse Assistant - AWS EC2 Deployment Script (AWS Glue Edition)
#
# This script deploys the POC to AWS EC2 with AWS Glue Catalog:
# - Spring Boot Backend (with Spark + AWS Glue Catalog integration)
# - React Frontend (served by Spring Boot)
# - Ollama (Local LLM - llama3)
# - NO MinIO (uses real AWS S3)
# - NO Iceberg REST Catalog (uses AWS Glue Catalog directly)
# - NO PostgreSQL (stateless application)
#
# Prerequisites:
#   - AWS EC2 instance (Ubuntu 22.04/20.04, t3.xlarge recommended)
#   - AWS IAM Role attached to EC2 with:
#     * AmazonS3FullAccess (or specific S3 bucket access)
#     * AWSGlueConsoleFullAccess (or specific Glue Catalog access)
#   - S3 bucket created for data warehouse
#   - Security Group allows ports: 22, 8080
#
# Usage:
#   sudo bash deploy-ec2-aws-glue.sh https://github.com/yourusername/ai-datalake-platform.git
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

# Verify AWS credentials
check_aws_credentials() {
    print_header "Step 1: Verifying AWS Configuration"

    # Check if AWS CLI is available or install it
    if ! command -v aws &> /dev/null; then
        print_info "Installing AWS CLI..."
        apt-get install -y awscli > /dev/null 2>&1
        print_success "AWS CLI installed"
    fi

    # Try to get EC2 instance metadata (verifies IAM role)
    if curl -s --max-time 5 http://169.254.169.254/latest/meta-data/iam/info > /dev/null 2>&1; then
        print_success "EC2 IAM Role detected"
        IAM_ROLE=$(curl -s http://169.254.169.254/latest/meta-data/iam/info | grep InstanceProfileArn | cut -d'"' -f4)
        print_info "IAM Role: $IAM_ROLE"
    else
        print_error "No IAM Role attached to EC2 instance!"
        print_info "Please attach an IAM role with S3 and Glue permissions"
        exit 1
    fi

    # Get AWS region from EC2 metadata
    AWS_REGION=$(curl -s http://169.254.169.254/latest/meta-data/placement/region || echo "us-east-1")
    export AWS_REGION
    print_success "AWS Region: $AWS_REGION"
}

# Update system
update_system() {
    print_header "Step 2: Updating System Packages"
    apt-get update -y > /dev/null 2>&1
    apt-get upgrade -y > /dev/null 2>&1
    print_success "System packages updated"
}

# Install Java and Maven
install_java_maven() {
    print_header "Step 3: Installing Java 17 & Maven"

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
    print_header "Step 4: Installing Node.js 18.x & npm"

    if command -v node &> /dev/null; then
        print_info "Node.js already installed: $(node --version)"
    else
        curl -fsSL https://deb.nodesource.com/setup_18.x | bash - > /dev/null 2>&1
        apt-get install -y nodejs > /dev/null 2>&1
        print_success "Node.js installed: $(node --version)"
    fi
}

# Install Git
install_git() {
    print_header "Step 5: Installing Git"

    if command -v git &> /dev/null; then
        print_info "Git already installed: $(git --version)"
    else
        apt-get install -y git curl wget > /dev/null 2>&1
        print_success "Git installed"
    fi
}

# Install Ollama
install_ollama() {
    print_header "Step 6: Installing Ollama (Local LLM)"

    if command -v ollama &> /dev/null; then
        print_info "Ollama already installed"
    else
        print_info "Installing Ollama..."
        curl -fsSL https://ollama.com/install.sh | sh > /dev/null 2>&1
        print_success "Ollama installed"
    fi

    # Start Ollama service
    print_info "Starting Ollama service..."
    systemctl start ollama 2>/dev/null || true
    systemctl enable ollama 2>/dev/null || true

    # Pull llama3 model
    print_info "Pulling llama3 model (this may take 5-10 minutes)..."
    ollama pull llama3 > /dev/null 2>&1 &
    OLLAMA_PID=$!

    # Wait with progress indicator
    while kill -0 $OLLAMA_PID 2>/dev/null; do
        echo -n "."
        sleep 3
    done
    echo ""

    print_success "Ollama llama3 model ready"
}

# Clone repository
clone_repo() {
    print_header "Step 7: Cloning Repository"

    local REPO_URL="$1"
    local INSTALL_DIR="/opt/ai-datalake"

    if [ -d "$INSTALL_DIR" ]; then
        print_info "Repository already exists at $INSTALL_DIR"
        cd "$INSTALL_DIR"
        git pull origin main > /dev/null 2>&1 || git pull origin master > /dev/null 2>&1
    else
        mkdir -p /opt
        git clone "$REPO_URL" "$INSTALL_DIR"
        cd "$INSTALL_DIR"
        print_success "Repository cloned to $INSTALL_DIR"
    fi

    export INSTALL_DIR
}

# Configure application for AWS Glue
configure_application() {
    print_header "Step 8: Configuring Application for AWS Glue Catalog"

    cd "$INSTALL_DIR"
    local PUBLIC_IP=$(curl -s https://checkip.amazonaws.com || curl -s http://169.254.169.254/latest/meta-data/public-ipv4 || echo "localhost")

    # Prompt for S3 bucket
    echo ""
    read -p "Enter your S3 bucket name for data warehouse (e.g., datalake-xxxx-dev): " S3_BUCKET
    if [ -z "$S3_BUCKET" ]; then
        print_error "S3 bucket name is required!"
        exit 1
    fi

    # Create application.yml override
    cat > "$INSTALL_DIR/src/main/resources/application-prod.yml" << EOF
spring:
  application:
    name: ai-datalake-platform

server:
  port: 8080
  address: 0.0.0.0

# AWS Glue Catalog Configuration
aws:
  region: ${AWS_REGION}
  glue:
    enabled: true
    catalog-id: \${AWS_ACCOUNT_ID:}
  s3:
    bucket: ${S3_BUCKET}
    warehouse-path: warehouse
    endpoint: ""  # Empty for real AWS S3
    path-style-access: false

# Spark Configuration (ENABLED for AWS Glue)
spark:
  enabled: true
  master: local[*]
  metastore:
    type: glue
  warehouse: s3a://${S3_BUCKET}/warehouse

# LLM Configuration (Local Ollama)
llm:
  ollama:
    url: http://localhost:11434
    model: llama3
    embedding-model: llama3
    timeout: 60s
  temperature: 0.3

# RAG Configuration
rag:
  knowledge-base:
    path: src/main/resources/knowledge-base

# Logging
logging:
  level:
    com.datalake: INFO
    org.apache.iceberg: WARN
    org.apache.spark: WARN
EOF

    print_success "Application configured for AWS Glue Catalog"
    print_info "S3 Warehouse: s3a://${S3_BUCKET}/warehouse"
    print_info "Public IP: $PUBLIC_IP"

    export S3_BUCKET
    export PUBLIC_IP
}

# Build Frontend
build_frontend() {
    print_header "Step 9: Building React Frontend"

    cd "$INSTALL_DIR/frontend"

    # Update API URL with public IP
    cat > "$INSTALL_DIR/frontend/.env.production" << EOF
REACT_APP_API_URL=http://${PUBLIC_IP}:8080/api
EOF

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

# Build Backend
build_backend() {
    print_header "Step 10: Building Spring Boot Backend"

    cd "$INSTALL_DIR"

    print_info "Building backend with Maven (this may take 3-5 minutes)..."
    mvn clean package -DskipTests > /dev/null 2>&1

    if [ -f "target/ai-datalake-platform-1.0.0-SNAPSHOT.jar" ]; then
        print_success "Backend JAR built successfully"
    else
        print_error "Backend build failed"
        return 1
    fi
}

# Create systemd service
create_systemd_service() {
    print_header "Step 11: Creating Systemd Service"

    cat > /etc/systemd/system/ai-datalake.service << EOF
[Unit]
Description=AI Data Lakehouse Assistant
After=network.target ollama.service

[Service]
Type=simple
User=root
WorkingDirectory=$INSTALL_DIR
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="AWS_REGION=$AWS_REGION"
Environment="JAVA_OPTS=-Xmx4g -XX:+UseG1GC --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED"
ExecStart=/usr/bin/java \$JAVA_OPTS -jar $INSTALL_DIR/target/ai-datalake-platform-1.0.0-SNAPSHOT.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    print_success "Systemd service created"
}

# Start application
start_application() {
    print_header "Step 12: Starting Application"

    # Stop if already running
    systemctl stop ai-datalake 2>/dev/null || true

    # Start service
    systemctl start ai-datalake
    systemctl enable ai-datalake

    print_success "Application service started"
}

# Wait for application to be ready
wait_for_application() {
    print_header "Step 13: Waiting for Application to be Ready"

    local max_attempts=60
    local attempt=0

    print_info "Waiting for backend API..."
    while [ $attempt -lt $max_attempts ]; do
        if curl -s -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
            print_success "Backend API is ready"
            break
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 3
    done

    if [ $attempt -eq $max_attempts ]; then
        print_error "Backend did not start. Check logs: journalctl -u ai-datalake -f"
        return 1
    fi
}

# Display access information
display_access_info() {
    print_header "Step 14: Deployment Complete! 🎉"

    echo ""
    echo -e "${GREEN}🌐 ACCESS INFORMATION${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${YELLOW}🚀 Application URL:${NC}"
    echo -e "  ${GREEN}http://${PUBLIC_IP}:8080${NC}"
    echo ""
    echo -e "${YELLOW}📊 Data Warehouse:${NC}"
    echo -e "  S3: ${GREEN}s3://${S3_BUCKET}/warehouse${NC}"
    echo -e "  Metastore: ${GREEN}AWS Glue Catalog${NC}"
    echo ""
    echo -e "${YELLOW}🤖 Local LLM:${NC}"
    echo -e "  Ollama: ${GREEN}http://localhost:11434${NC}"
    echo -e "  Model: llama3"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${GREEN}📋 NEXT STEPS:${NC}"
    echo "  1. Open http://${PUBLIC_IP}:8080 in your browser"
    echo "  2. Try example queries from the UI"
    echo "  3. Explore your AWS Glue databases and tables"
    echo ""
    echo -e "${YELLOW}💡 EXAMPLE QUERIES:${NC}"
    echo "  • What databases are available?"
    echo "  • List tables in [your-database]"
    echo "  • Show schema for [your-table]"
    echo "  • Create table demo with 3 fields"
    echo ""
    echo -e "${YELLOW}🔧 USEFUL COMMANDS:${NC}"
    echo "  • Check status:  systemctl status ai-datalake"
    echo "  • View logs:     journalctl -u ai-datalake -f"
    echo "  • Restart:       systemctl restart ai-datalake"
    echo "  • Stop:          systemctl stop ai-datalake"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    echo -e "${GREEN}📊 SHARE WITH YOUR TEAM:${NC}"
    echo "  ${GREEN}http://${PUBLIC_IP}:8080${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  IMPORTANT:${NC}"
    echo "  • Using AWS Glue Catalog (not local Hive)"
    echo "  • Data stored in S3: ${S3_BUCKET}"
    echo "  • Spark enabled for Iceberg operations"
    echo "  • Local LLM (Ollama) for AI queries"
    echo ""
}

# Cleanup function
cleanup_on_error() {
    print_error "Deployment failed!"
    print_info "Check logs:"
    echo "  • Application: journalctl -u ai-datalake -f"
    echo "  • Ollama: journalctl -u ollama -f"
    exit 1
}

# Main deployment flow
main() {
    local REPO_URL="${1}"

    if [ -z "$REPO_URL" ]; then
        print_error "Repository URL is required!"
        echo "Usage: sudo bash deploy-ec2-aws-glue.sh <GITHUB-REPO-URL>"
        exit 1
    fi

    print_header "🚀 AI Data Lakehouse - AWS EC2 Deployment (Glue Edition)"
    print_info "Repository: $REPO_URL"
    print_info "Target: AWS Glue Catalog + S3 + Local LLM"
    echo ""

    trap cleanup_on_error ERR

    check_permissions
    check_aws_credentials
    update_system
    install_java_maven
    install_nodejs
    install_git
    install_ollama
    clone_repo "$REPO_URL"
    configure_application
    build_frontend
    build_backend
    create_systemd_service
    start_application
    wait_for_application
    display_access_info

    print_success "🎉 Deployment completed successfully!"
    print_info "All services running: Spring Boot + Ollama + AWS Glue + S3"
}

# Run main function
main "$@"

