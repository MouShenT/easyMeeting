# EasyMeeting Docker 部署指南 (CentOS)

本指南详细介绍如何在 CentOS 服务器上通过 Docker 部署 EasyMeeting 视频会议系统。

## 📋 目录

1. [项目架构](#1-项目架构)
2. [环境准备](#2-环境准备)
3. [安装 Docker](#3-安装-docker)
4. [项目部署](#4-项目部署)
5. [数据库初始化](#5-数据库初始化)
6. [常用命令](#6-常用命令)
7. [故障排查](#7-故障排查)
8. [生产环境配置](#8-生产环境配置)
9. [更新与维护](#9-更新与维护)

---

## 1. 项目架构

### 1.1 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot + Netty | 3.4.0 / Java 23 |
| 前端 | Vue 3 + Vite + TypeScript | 3.4.x |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.x |
| 消息通道 | Redis Pub/Sub | - |
| Web服务器 | Nginx | Alpine |

### 1.2 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 (Nginx) | 80 | HTTP 访问入口 |
| 后端 API | 6060 | REST API 接口 |
| WebSocket | 6061 | 实时通信 (Netty) |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存服务 |

### 1.3 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        用户浏览器                            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Nginx (端口 80)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  静态文件   │  │  /api 代理  │  │    /ws 代理         │  │
│  │  (Vue 前端) │  │  → :6060    │  │    → :6061          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌─────────────────┐ ┌───────────┐ ┌───────────────────┐
│  Spring Boot    │ │   Netty   │ │                   │
│  REST API       │ │ WebSocket │ │                   │
│  (端口 6060)    │ │ (端口6061)│ │                   │
└────────┬────────┘ └─────┬─────┘ │                   │
         │                │       │                   │
         └────────┬───────┘       │                   │
                  ▼               │                   │
         ┌────────────────┐       │                   │
         │     Redis      │◄──────┤   Docker Network  │
         │   (端口 6379)  │       │                   │
         └────────────────┘       │                   │
                  │               │                   │
                  ▼               │                   │
         ┌────────────────┐       │                   │
         │     MySQL      │◄──────┘                   │
         │   (端口 3306)  │                           │
         └────────────────┘                           │
                                                      │
└─────────────────────────────────────────────────────┘
```

---

## 2. 环境准备

### 2.1 系统要求

| 配置项 | 最低要求 | 推荐配置 |
|--------|----------|----------|
| 操作系统 | CentOS 7/8/Stream, Rocky Linux 8/9 | CentOS Stream 9 |
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 40 GB | 100 GB SSD |
| 带宽 | 1 Mbps | 5 Mbps |

### 2.2 更新系统

```bash
# CentOS 7
sudo yum update -y

# CentOS 8/Stream 或 Rocky Linux
sudo dnf update -y
```

### 2.3 安装必要工具

```bash
# CentOS 7
sudo yum install -y yum-utils git wget curl vim net-tools

# CentOS 8/Stream 或 Rocky Linux
sudo dnf install -y dnf-utils git wget curl vim net-tools
```

### 2.4 配置防火墙

```bash
# 方式一：关闭防火墙（仅测试环境）
sudo systemctl stop firewalld
sudo systemctl disable firewalld

# 方式二：开放必要端口（生产环境推荐）
sudo firewall-cmd --permanent --add-port=80/tcp      # 前端 HTTP
sudo firewall-cmd --permanent --add-port=443/tcp     # HTTPS（如需要）
sudo firewall-cmd --permanent --add-port=6060/tcp    # 后端 API
sudo firewall-cmd --permanent --add-port=6061/tcp    # WebSocket
sudo firewall-cmd --reload

# 验证端口开放
sudo firewall-cmd --list-ports
```

### 2.5 关闭 SELinux（可选）

```bash
# 查看当前状态
getenforce

# 临时关闭
sudo setenforce 0

# 永久关闭（需重启生效）
sudo sed -i 's/SELINUX=enforcing/SELINUX=disabled/g' /etc/selinux/config
```

---

## 3. 安装 Docker

### 3.1 卸载旧版本

```bash
sudo yum remove -y docker docker-client docker-client-latest docker-common \
    docker-latest docker-latest-logrotate docker-logrotate docker-engine
```

### 3.2 添加 Docker 仓库

```bash
# 使用阿里云镜像源（国内推荐）
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo

# 或使用官方源（国外服务器）
# sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
```

### 3.3 安装 Docker

```bash
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

### 3.4 启动 Docker

```bash
# 启动服务
sudo systemctl start docker

# 设置开机自启
sudo systemctl enable docker

# 验证安装
docker --version
docker compose version
```

### 3.5 配置镜像加速（国内必须）

```bash
sudo mkdir -p /etc/docker

sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": [
    "https://mirror.ccs.tencentyun.com",
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
EOF

# 重启 Docker
sudo systemctl daemon-reload
sudo systemctl restart docker
```

### 3.6 添加用户到 docker 组（可选）

```bash
# 避免每次使用 sudo
sudo usermod -aG docker $USER

# 重新登录或执行
newgrp docker
```

---

## 4. 项目部署

### 4.1 克隆项目

```bash
cd /opt
git clone https://github.com/你的用户名/easymeeting.git
cd easymeeting
```

### 4.2 项目文件结构

确保项目包含以下 Docker 相关文件：

```
easymeeting/
├── docker-compose.yml              # Docker 编排文件
├── .env.example                    # 环境变量模板
├── easymeeting-java/
│   ├── Dockerfile                  # 后端 Dockerfile
│   └── src/main/resources/
│       └── application-docker.properties  # Docker 环境配置
└── easymeeting-web/
    ├── Dockerfile                  # 前端 Dockerfile
    └── nginx.conf                  # Nginx 配置
```

### 4.3 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑环境变量
vi .env
```

修改 `.env` 文件内容：

```bash
# ==================== 必须修改 ====================
# MySQL root 密码（请使用强密码）
MYSQL_ROOT_PASSWORD=YourSecurePassword123!

# Redis 密码（请使用强密码）
REDIS_PASSWORD=YourRedisPassword456!
```

### 4.4 创建数据库初始化目录

```bash
mkdir -p init-sql
```

如果有数据库初始化脚本，放入此目录：

```bash
# 示例：复制初始化 SQL
cp /path/to/easymeeting.sql init-sql/
```

### 4.5 构建并启动服务

```bash
# 首次部署：构建镜像并启动（需要较长时间）
docker compose up -d --build

# 查看构建日志
docker compose logs -f
```

### 4.6 验证部署

```bash
# 查看容器状态（所有服务应为 running）
docker compose ps

# 预期输出：
# NAME                    STATUS
# easymeeting-mysql       running (healthy)
# easymeeting-redis       running (healthy)
# easymeeting-backend     running
# easymeeting-frontend    running

# 测试后端 API
curl http://localhost:6060/api/account/checkCode

# 测试前端
curl -I http://localhost:80
```

### 4.7 访问应用

- 前端页面：`http://你的服务器IP`
- 后端 API：`http://你的服务器IP/api`
- WebSocket：`ws://你的服务器IP/ws`

---

## 5. 数据库初始化

### 5.1 自动初始化

将 SQL 文件放入 `init-sql/` 目录后重新部署：

```bash
# 停止并删除容器（保留数据卷则不加 -v）
docker compose down

# 删除 MySQL 数据卷以重新初始化
docker volume rm easymeeting_mysql_data

# 重新启动
docker compose up -d
```

### 5.2 手动初始化

```bash
# 进入 MySQL 容器
docker exec -it easymeeting-mysql mysql -uroot -p

# 输入密码后执行
USE easymeeting;

# 执行建表语句...
# 或者退出后导入文件
```

### 5.3 导入 SQL 文件

```bash
# 方式一：从宿主机导入
docker exec -i easymeeting-mysql mysql -uroot -p你的密码 easymeeting < /path/to/your.sql

# 方式二：复制到容器后导入
docker cp easymeeting.sql easymeeting-mysql:/tmp/
docker exec -it easymeeting-mysql bash
mysql -uroot -p easymeeting < /tmp/easymeeting.sql
```

---

## 6. 常用命令

### 6.1 服务管理

```bash
# 启动所有服务
docker compose up -d

# 停止所有服务
docker compose down

# 重启所有服务
docker compose restart

# 重启单个服务
docker compose restart backend
docker compose restart frontend

# 查看服务状态
docker compose ps

# 查看资源使用
docker stats
```

### 6.2 日志查看

```bash
# 查看所有日志
docker compose logs -f

# 查看单个服务日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose logs -f redis

# 查看最近 100 行日志
docker compose logs --tail 100 backend

# 查看指定时间后的日志
docker compose logs --since 1h backend
```

### 6.3 镜像管理

```bash
# 重新构建镜像
docker compose build

# 强制重新构建（不使用缓存）
docker compose build --no-cache

# 拉取最新基础镜像
docker compose pull
```

### 6.4 进入容器

```bash
# 进入后端容器
docker exec -it easymeeting-backend bash

# 进入 MySQL
docker exec -it easymeeting-mysql mysql -uroot -p

# 进入 Redis
docker exec -it easymeeting-redis redis-cli -a 你的Redis密码

# 进入前端容器
docker exec -it easymeeting-frontend sh
```

### 6.5 数据备份

```bash
# 备份 MySQL
docker exec easymeeting-mysql mysqldump -uroot -p你的密码 easymeeting > backup_$(date +%Y%m%d).sql

# 恢复 MySQL
docker exec -i easymeeting-mysql mysql -uroot -p你的密码 easymeeting < backup.sql

# 备份 Redis
docker exec easymeeting-redis redis-cli -a 你的密码 BGSAVE
```

---

## 7. 故障排查

### 7.1 容器无法启动

```bash
# 查看详细日志
docker compose logs backend

# 常见原因：
# 1. 端口被占用
netstat -tlnp | grep -E '80|6060|6061|3306|6379'

# 2. 内存不足
free -h

# 3. 磁盘空间不足
df -h
```

### 7.2 后端无法连接 MySQL

```bash
# 检查 MySQL 是否健康
docker compose ps mysql

# 检查网络连通性
docker exec easymeeting-backend ping mysql

# 查看 MySQL 日志
docker compose logs mysql

# 手动测试连接
docker exec -it easymeeting-backend bash
# 在容器内执行
apt-get update && apt-get install -y mysql-client
mysql -h mysql -u root -p
```

### 7.3 前端无法访问后端 API

```bash
# 检查后端是否运行
curl http://localhost:6060/api/account/checkCode

# 检查 Nginx 配置
docker exec easymeeting-frontend cat /etc/nginx/conf.d/default.conf

# 查看 Nginx 错误日志
docker exec easymeeting-frontend cat /var/log/nginx/error.log
```

### 7.4 WebSocket 连接失败

```bash
# 检查 WebSocket 端口
netstat -tlnp | grep 6061

# 检查防火墙
sudo firewall-cmd --list-ports

# 测试 WebSocket（需要安装 wscat）
npm install -g wscat
wscat -c ws://localhost:6061/ws
```

### 7.5 清理与重置

```bash
# 停止并删除所有容器
docker compose down

# 删除所有数据卷（⚠️ 会丢失数据）
docker compose down -v

# 清理未使用的资源
docker system prune -a

# 完全重置
docker compose down -v
docker rmi $(docker images -q)
docker compose up -d --build
```

---

## 8. 生产环境配置

### 8.1 HTTPS 配置

使用 Certbot 获取免费 SSL 证书：

```bash
# 安装 Certbot
sudo yum install -y certbot

# 停止 Nginx（释放 80 端口）
docker compose stop frontend

# 获取证书
sudo certbot certonly --standalone -d your-domain.com

# 证书位置
# /etc/letsencrypt/live/your-domain.com/fullchain.pem
# /etc/letsencrypt/live/your-domain.com/privkey.pem
```

修改 `easymeeting-web/nginx.conf` 添加 HTTPS：

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;
    
    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    
    # ... 其他配置
}
```

### 8.2 自动备份脚本

```bash
# 创建备份脚本
cat > /opt/easymeeting/backup.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/opt/backups/easymeeting
MYSQL_PASSWORD=${MYSQL_ROOT_PASSWORD:-12345678}

mkdir -p $BACKUP_DIR

# 备份 MySQL
docker exec easymeeting-mysql mysqldump -uroot -p$MYSQL_PASSWORD easymeeting > $BACKUP_DIR/mysql_$DATE.sql

# 压缩
gzip $BACKUP_DIR/mysql_$DATE.sql

# 删除 7 天前的备份
find $BACKUP_DIR -name "*.gz" -mtime +7 -delete

echo "Backup completed: mysql_$DATE.sql.gz"
EOF

chmod +x /opt/easymeeting/backup.sh

# 添加定时任务（每天凌晨 2 点）
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/easymeeting/backup.sh >> /var/log/easymeeting-backup.log 2>&1") | crontab -
```

### 8.3 日志轮转

Docker 已配置日志轮转（daemon.json），也可以配置系统级别：

```bash
cat > /etc/logrotate.d/easymeeting << 'EOF'
/opt/backups/easymeeting/*.log {
    daily
    rotate 7
    compress
    missingok
    notifempty
}
EOF
```

### 8.4 性能优化

修改 `docker-compose.yml` 添加资源限制：

```yaml
services:
  backend:
    # ... 其他配置
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

---

## 9. 更新与维护

### 9.1 更新代码

```bash
cd /opt/easymeeting

# 拉取最新代码
git pull origin main

# 重新构建并启动
docker compose up -d --build

# 查看日志确认启动成功
docker compose logs -f
```

### 9.2 回滚版本

```bash
# 查看历史版本
git log --oneline -10

# 回滚到指定版本
git checkout <commit-hash>

# 重新部署
docker compose up -d --build
```

### 9.3 监控建议

推荐使用以下工具进行监控：

- **Prometheus + Grafana**：容器和应用监控
- **Portainer**：Docker 可视化管理
- **Uptime Kuma**：服务可用性监控

---

## 🚀 快速部署命令汇总

```bash
# ========== 一键部署 ==========
cd /opt
git clone https://github.com/你的用户名/easymeeting.git
cd easymeeting

# 配置环境变量
cp .env.example .env
vi .env  # 修改 MYSQL_ROOT_PASSWORD 和 REDIS_PASSWORD

# 构建并启动
docker compose up -d --build

# 查看状态
docker compose ps
docker compose logs -f

# ========== 常用操作 ==========
docker compose restart          # 重启所有服务
docker compose logs -f backend  # 查看后端日志
docker compose down             # 停止服务
docker compose up -d --build    # 更新后重新部署
```

---

## 📞 常见问题

**Q: 构建时间很长怎么办？**
A: 首次构建需要下载依赖，后续构建会使用缓存。确保配置了 Docker 镜像加速。

**Q: 如何修改端口？**
A: 修改 `docker-compose.yml` 中的 ports 映射，如 `"8080:80"` 将前端改为 8080 端口。

**Q: 数据存储在哪里？**
A: 数据存储在 Docker 数据卷中，使用 `docker volume ls` 查看。

**Q: 如何查看数据库？**
A: 使用 `docker exec -it easymeeting-mysql mysql -uroot -p` 进入 MySQL。

---

部署完成后访问：`http://你的服务器IP`

如有问题，请查看日志：`docker compose logs -f`
