# OpenManus-Java 快速启动指南

本文档将指导您如何快速启动并运行 OpenManus-Java 项目。

## 1. 先决条件

在开始之前，请确保您的开发环境中已安装以下软件：

- **Java 21**: 项目需要 Java 21 (JDK 21) 或更高版本。
- **Maven 3.6+**: 用于项目构建和依赖管理。
- **Docker**: (推荐) 用于一键启动应用，并为 Agent 提供沙箱环境。

## 2. 配置

### 2.1. API 密钥配置

项目运行需要一些外部服务的环境变量。我们使用 `.env` 文件来管理这些配置。

1.  **复制 `.env` 文件**:
    在项目根目录下，找到 `dotenv.example` 文件，并复制一份，将其重命名为 `.env`。

    ```bash
    cp dotenv.example .env
    ```

2.  **编辑 `.env` 文件**:
    打开 `.env` 文件，填入您自己的模型配置。

    ```dotenv
    # -----------------------------------------------------------------------------
    # Environment variables for OpenManus Java
    # -----------------------------------------------------------------------------

    # 默认 LLM（必需）
    OPENMANUS_LLM_DEFAULT_LLM_API_TYPE=openai
    OPENMANUS_LLM_DEFAULT_LLM_BASE_URL=https://api.openai.com/v1
    OPENMANUS_LLM_DEFAULT_LLM_API_KEY=your-openai-compatible-api-key-here
    OPENMANUS_LLM_DEFAULT_LLM_MODEL=gpt-5.4

    # Serper API Key (可选)
    # 用于 Agent 的网页搜索功能。
    # 获取地址: https://serper.dev
    SERPER_API_KEY=your-serper-api-key-here
    ```

    **注意**:
    - `OPENMANUS_LLM_DEFAULT_LLM_BASE_URL`、`OPENMANUS_LLM_DEFAULT_LLM_API_KEY`、`OPENMANUS_LLM_DEFAULT_LLM_MODEL` 是项目运行所必需的。
    - 其他 API 密钥是可选的，但会影响部分 Agent 功能（如网页搜索）。

## 3. 启动应用

我们推荐使用 Docker Compose 进行一键启动，这是最简单快捷的方式。

### 3.1. 使用 Docker Compose 启动 (推荐)

在项目根目录下，运行以下命令：

```bash
docker-compose up --build
```

这个命令会：
1.  **构建 Docker 镜像**: 根据 `Dockerfile` 中的定义，编译项目并构建一个可运行的镜像。
2.  **创建并启动容器**: 使用构建好的镜像启动一个 Docker 容器。
3.  **加载环境变量**: 从 `.env` 文件中读取 API 密钥并注入到容器中。
4.  **挂载数据卷**: 将本地的 `workspace` 和 `logs` 目录挂载到容器中，方便查看和持久化数据。

启动成功后，您应该能看到类似以下的日志输出：

```
openmanus-java    | ...
openmanus-java    | ...  Tomcat started on port(s): 8089 (http) with context path ''
openmanus-java    | ...  Started WebApplication in ... seconds
```

### 3.2. 本地直接运行 (不使用 Docker)

如果您不想使用 Docker，也可以直接在本地运行。

1.  **使用 Maven 运行**:
    在项目根目录下，运行以下 Maven 命令：

    ```bash
    mvn spring-boot:run
    ```

    应用启动时会自动读取项目根目录 `.env`，然后编译并启动 Spring Boot 应用。

## 4. 访问应用

应用启动后，您可以通过以下地址访问：

- **Web 界面**: [http://localhost:8089](http://localhost:8089)
- **API 文档 (Swagger UI)**: [http://localhost:8089/swagger-ui.html](http://localhost:8089/swagger-ui.html)

现在，您可以开始与 OpenManus-Java Agent 进行交互了！

## 5. 界面预览

![工作台概览](img01.png)

![网页预览（代理模式）](img02.png)

## 6. 常见问题

### 6.1 为什么有些网站在右侧“网页”预览里打不开？

部分网站会通过 `X-Frame-Options` 或 CSP `frame-ancestors` 禁止被 iframe 嵌入，浏览器会直接拦截，因此会出现“此网站无法在此预览”的提示。

### 6.2 如何解决“此网站无法在此预览”？

在右侧地址栏开启 **“代理”** 开关后重试（会通过后端代理加载页面，从而绕过 iframe 限制）。

> 注意：代理能力仅建议用于开发/演示场景；若目标网站本身需要登录、强校验 CSRF/Referrer/Cookie 等，仍可能无法完整使用。
