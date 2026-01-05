# EasyMeeting - 在线视频会议系统

一个基于 Spring Boot + Vue 3 + WebRTC 的在线视频会议系统，支持实时音视频通话、屏幕共享、会议聊天等功能。

## ✨ 功能特性

- 🎥 **实时音视频通话** - 基于 WebRTC 的点对点音视频通信
- 🖥️ **屏幕共享** - 支持共享整个屏幕或应用窗口 `待实现`
- 💬 **会议聊天** - 支持文字消息和文件传输
- 📅 **预约会议** - 提前预约会议并邀请参会人员
- 👥 **联系人管理** - 添加好友、私聊功能
- 🔐 **用户认证** - JWT Token 认证机制
- 📱 **响应式设计** - 适配桌面端和移动端

## 🛠️ 技术栈

### 后端
- **框架**: Spring Boot 3.4.0
- **语言**: Java 23
- **WebSocket**: Netty 4.1
- **数据库**: MySQL 8.0
- **缓存**: Redis 7.x + Redisson
- **认证**: JWT

### 前端
- **框架**: Vue 3.4 + TypeScript
- **构建工具**: Vite 5
- **UI 组件**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router 4

## 📁 项目结构

```
easymeeting/
├── easymeeting-java/          # 后端 Spring Boot 项目
│   ├── src/main/java/
│   │   └── com/easymeeting/
│   │       ├── controller/    # REST API 控制器
│   │       ├── service/       # 业务逻辑层
│   │       ├── mapper/        # MyBatis 数据访问层
│   │       ├── entity/        # 实体类
│   │       ├── websocket/     # WebSocket/Netty 相关
│   │       └── redis/         # Redis 配置
│   └── Dockerfile
├── easymeeting-web/           # 前端 Vue 项目
│   ├── src/
│   │   ├── views/             # 页面组件
│   │   ├── components/        # 公共组件
│   │   ├── api/               # API 接口
│   │   ├── utils/             # 工具函数
│   │   └── router/            # 路由配置
│   ├── Dockerfile
│   └── nginx.conf
├── init-sql/                  # 数据库初始化脚本
├── docker-compose.yml         # Docker 编排文件
└── docs/                      # 项目文档
```

## 🚀 快速开始

### 方式一：Docker 部署（推荐）

```bash
# 1. 克隆项目
git clone https://github.com/MouShenT/easyMeeting.git
cd easyMeeting

# 2. 配置环境变量
cp .env.example .env
# 编辑 .env 修改数据库和 Redis 密码

# 3. 启动服务
docker compose up -d --build

# 4. 访问应用
# 前端: http://localhost
# 后端 API: http://localhost/api
```

详细部署指南请参考 [Docker 部署文档](docs/docker-deployment-guide.md)

### 方式二：本地开发

#### 环境要求
- JDK 23+
- Node.js 18+
- MySQL 8.0+
- Redis 7.x+

#### 后端启动

```bash
cd easymeeting-java

# 1. 创建数据库并导入初始化脚本
mysql -u root -p < ../init-sql/easymeeting.sql

# 2. 修改配置文件
# 编辑 src/main/resources/application.properties
# 配置数据库和 Redis 连接信息

# 3. 启动后端
mvn spring-boot:run
```

#### 前端启动

```bash
cd easymeeting-web

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

## 📊 数据库设计

| 表名 | 说明 |
|------|------|
| user_info | 用户信息表 |
| meeting_info | 会议信息表 |
| meeting_member | 会议成员表 |
| meeting_reserve | 预约会议表 |
| meeting_reserve_member | 预约会议成员表 |
| user_contact | 用户联系人表 |
| user_contact_apply | 联系人申请表 |
| message_chat_message_XX | 会议聊天消息分表 (01-32) |
| private_chat_message_XX | 私聊消息分表 (01-32) |
| private_chat_unread | 私聊未读消息表 |

## 🔧 配置说明

### 后端配置 (application.properties)

```properties
# 服务端口
server.port=6060
ws.port=6061

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/easymeeting
spring.datasource.username=root
spring.datasource.password=your_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=your_password
```

### 环境变量 (.env)

```bash
MYSQL_ROOT_PASSWORD=your_mysql_password
REDIS_PASSWORD=your_redis_password
```

## 📝 API 文档

主要 API 端点：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/account/register | 用户注册 |
| POST | /api/account/login | 用户登录 |
| POST | /api/meeting/create | 创建会议 |
| POST | /api/meeting/join | 加入会议 |
| GET | /api/meeting/list | 获取会议列表 |
| POST | /api/reserve/create | 创建预约会议 |
| GET | /api/contact/list | 获取联系人列表 |

## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交 Pull Request

## 📄 开源协议

本项目采用 MIT 协议开源，详见 [LICENSE](LICENSE) 文件。

## 📞 联系方式

如有问题或建议，欢迎提交 Issue 或 Pull Request。
