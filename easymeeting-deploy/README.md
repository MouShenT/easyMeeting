# EasyMeeting Docker 部署包

## 目录结构

```
easymeeting-deploy/
├── docker-compose.yml      # Docker 编排文件
├── .env.example            # 环境变量模板
├── backend/
│   ├── Dockerfile          # 后端镜像配置
│   └── app.jar             # 后端 JAR 包（需要放入）
├── frontend/
│   ├── Dockerfile          # 前端镜像配置
│   ├── nginx.conf          # Nginx 配置
│   └── dist/               # 前端构建产物（需要放入）
└── init-sql/
    └── easymeeting.sql     # 数据库初始化脚本
```

## 快速部署

### 1. 配置镜像加速（国内服务器必须）

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": ["https://你的专属域名.xuanyuan.run"]
}
EOF
systemctl daemon-reload
systemctl restart docker
```

### 2. 配置环境变量

```bash
cp .env.example .env
vi .env  # 修改密码
```

### 3. 启动服务

```bash
docker compose up -d --build
```

### 4. 查看状态

```bash
docker compose ps
docker compose logs -f
```

## 访问地址

- 前端：http://服务器IP
- API：http://服务器IP/api
- WebSocket：ws://服务器IP/ws

## 常用命令

```bash
docker compose up -d        # 启动
docker compose down         # 停止
docker compose restart      # 重启
docker compose logs -f      # 查看日志
```
