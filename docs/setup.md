# 环境搭建记录

本机 Cloud-native & SRE 学习环境索引。最后更新：2026-11-22。

## 当前环境

| 项 | 值 |
|---|---|
| 分支 | `cloude-native-sre-v2` |
| 上游 | `spring-projects/spring-petclinic`（Spring Boot 4.1.0） |
| JDK / Maven | 26.0.1 / 3.9.16（`./mvnw`） |
| Docker Desktop | 4.99.0，Engine 29.9.3，BuildKit |
| Kubernetes | Docker Desktop kind，1 node，v1.36.4，context `docker-desktop` |
| 镜像 | `petclinic:dev`（已构建并 smoke test） |

## 1. 仓库与 CLI

```bash
git branch -vv
./mvnw -v

export PATH="$HOME/.docker/bin:$PATH"
docker version
kubectl version --client
```

如果新终端找不到命令，把 PATH 写进 `~/.zshrc`。

当前分支基于上游 `main`，额外保留 `docs/`、`Dockerfile`、`docker-compose.dev.yml`、`CLAUDE.md`；`k8s/` 来自上游。

## 2. Dockerfile

多阶段构建，关键点：

- `build` 阶段：`./mvnw -B -Dmaven.test.skip=true package`
- `production` 阶段：非 root（UID/GID 10001）、`-XX:MaxRAMPercentage=75.0`
- `development` 阶段保留给 `docker-compose.dev.yml`

## 3. 构建镜像

本机直连 Docker Hub 不稳定，当前可复现的步骤：

```bash
# 1) 在 ~/.docker/daemon.json 配置 registry-mirrors
#    "registry-mirrors": ["https://docker.m.daocloud.io"]
docker desktop restart

# 2) 预拉基础镜像（避免 BuildKit 解析 tag 时 401）
docker pull eclipse-temurin:17-jdk-jammy
docker pull eclipse-temurin:17-jre-jammy

# 3) 构建（--pull=false 使用本地基础镜像）
docker build --pull=false --target production -t petclinic:dev .
```

如果 Docker 能通过代理直连 Docker Hub，可以移除 mirror，直接：

```bash
docker build --target production -t petclinic:dev .
```

验证：

```bash
docker images petclinic:dev
docker inspect petclinic:dev --format '{{.Config.User}}'   # 10001:10001
docker run --rm --entrypoint id petclinic:dev              # uid=10001
```

Smoke test：

```bash
docker run -d --name petclinic-dev -p 18080:8080 petclinic:dev
curl -sS --retry 30 --retry-delay 2 --retry-all-errors localhost:18080/actuator/health
curl -sS -o /dev/null -w '%{http_code}\n' localhost:18080/owners/1
docker exec petclinic-dev id
docker rm -f petclinic-dev
```

预期：health 为 `UP`，owners 为 `200`，uid 为 10001。

## 4. Kubernetes

```bash
kubectl config get-contexts
kubectl config use-context docker-desktop
kubectl get nodes
kubectl get pods -A
```

创建命名空间：

```bash
kubectl create namespace petclinic
kubectl config set-context --current --namespace=petclinic
```

## 5. 测试

```bash
./mvnw test
./mvnw test -Dtest=PostgresIntegrationTests
./mvnw test -Dtest=OwnerControllerTests#testInitCreationForm
./mvnw spring-javaformat:apply
./mvnw validate
```

`PostgresIntegrationTests` 需要 Docker CLI + daemon；`MySqlIntegrationTests` 使用 Testcontainers，无 Docker 时会跳过。镜像构建已用 `-Dmaven.test.skip=true` 跳过测试。

## 6. 下一步

1. 修改 `k8s/petclinic.yml`：`image: petclinic:dev`、`imagePullPolicy: IfNotPresent`、`replicas: 1`、startupProbe、优雅关闭环境变量。
2. 部署 `k8s/db.yml` 和 `k8s/petclinic.yml`。
3. 验证 `/livez`、`/readyz`，扩容、删 Pod 观察自愈。
4. 在 `docs/` 记录 Week 1 验收答案。
