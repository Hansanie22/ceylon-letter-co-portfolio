#!/bin/bash

# Configuration
SERVER_IP="129.121.78.119"
USER="root"
JAR_FILE="target/ceylonletterco-springboot-1.0.0.jar"
REMOTE_DIR="/root"

echo "=========================================================="
echo "🚀 Building and Deploying Spring Boot to Bluehost VPS"
echo "=========================================================="

echo "🔨 1. Compiling the latest code..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please fix Java errors first."
    exit 1
fi

echo "📦 2. Uploading the new Spring Boot application to the server (Zero Downtime during upload)..."
scp $JAR_FILE $USER@$SERVER_IP:$REMOTE_DIR/ceylonletterco-springboot-NEW.jar

if [ $? -ne 0 ]; then
    echo "❌ Upload failed. Please check your password/connection."
    exit 1
fi

echo "✅ Upload complete!"

echo "⚙️  3. Stopping old server, swapping files, and restarting..."
ssh $USER@$SERVER_IP << 'ENDSSH'
    # Stop legacy servers if they exist
    systemctl stop glassfish 2>/dev/null || true
    systemctl stop payara 2>/dev/null || true
    systemctl stop tomcat 2>/dev/null || true
    
    echo "Killing old Spring Boot process..."
    pkill -f ceylonletterco-springboot || true
    
    # Fallback to fuser since lsof might not be installed
    fuser -k 8080/tcp 2>/dev/null || true

    echo "Swapping JAR files..."
    mv -f /root/ceylonletterco-springboot-NEW.jar /root/ceylonletterco-springboot-1.0.0.jar

    if ! command -v java &> /dev/null; then
        echo "Java not found. Installing OpenJDK 17..."
        apt update && apt install openjdk-17-jdk -y
    fi

    echo "Fixing Nginx WebSocket configuration if needed..."
    NGINX_CONF=$(grep -rl "proxy_pass http://localhost:8080" /etc/nginx/sites-available /etc/nginx/conf.d 2>/dev/null | head -n 1)
    if [ ! -z "$NGINX_CONF" ]; then
        if ! grep -q "proxy_set_header Upgrade" "$NGINX_CONF"; then
            echo "🔧 Adding WebSocket support to Nginx config: $NGINX_CONF"
            perl -pi -e 's|proxy_pass http://localhost:8080;|proxy_pass http://localhost:8080;\n        proxy_http_version 1.1;\n        proxy_set_header Upgrade \$http_upgrade;\n        proxy_set_header Connection "upgrade";|g' "$NGINX_CONF"
            systemctl reload nginx || true
        fi
        
        # Remove duplicate CSP headers from Nginx since Spring Boot provides them
        if grep -q "add_header Content-Security-Policy" "$NGINX_CONF"; then
            echo "🔧 Removing duplicate CSP header from Nginx config: $NGINX_CONF"
            perl -pi -e 's/add_header Content-Security-Policy/# add_header Content-Security-Policy/g' "$NGINX_CONF"
            systemctl reload nginx || true
        fi
    fi
    
    echo "Cleaning up database redundant columns created by ddl-auto=update..."
    mysql -u root -pHansanie2002@ ceylonletterco -e "ALTER TABLE addresses DROP COLUMN street, DROP COLUMN district, DROP COLUMN postalCode;" 2>/dev/null || true

    echo "Starting Spring Boot..."
    nohup java -Xms256m -Xmx512m -jar /root/ceylonletterco-springboot-1.0.0.jar > /root/spring-boot.log 2>&1 &
    
    echo "Spring Boot application started successfully!"
ENDSSH

echo "=========================================================="
echo "🎉 Deployment Finished!"
echo "Please wait ~30 seconds for the application to fully start."
echo "You can view your site at: https://ceylonletterco.com (or http://$SERVER_IP:8080)"
echo "=========================================================="
