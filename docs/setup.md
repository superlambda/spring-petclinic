# 环境搭建记录

本机 Cloud-native & SRE 学习环境索引。最后更新：2026-11-24。

## 当前环境

| 项 | 值 |
|---|---|
| 分支 | `cloude-native-sre-v3` |
| 上游基线 | `spring-projects/spring-petclinic`（Spring Boot 4.1.0，仅参考；remote 只有 `origin`） |
| JDK / Maven | 26.0.1 / 3.9.16（`./mvnw`） |
| Docker Desktop | 4.99.0，Engine 29.9.3，BuildKit |
| Kubernetes | Docker Desktop，1 node，v1.36.4，context `docker-desktop` |
| 命名空间 | `petclinic`（已设为当前 context 默认） |
| 镜像 | `petclinic:dev`（2026-11-22 构建，非 root 10001） |

## 1. 仓库与 CLI

```bash
git branch -vv
./mvnw -v
docker version
kubectl version --client
```

`docker` / `kubectl` 已加入 PATH（`~/.docker/bin`）。

当前分支基于上游 `main`，额外保留 `docs/`、`Dockerfile`、`docker-compose.dev.yml`、`CLAUDE.md`；`k8s/petclinic.yml` 已本地化修改。

## 2. Dockerfile

多阶段构建：

- `build`：`./mvnw -B -Dmaven.test.skip=true package`
- `production`：非 root（10001:10001）、`-XX:MaxRAMPercentage=75.0`
- `development`：保留给 `docker-compose.dev.yml`，使用 MySQL profile

## 3. 镜像

基础镜像和 `petclinic:dev` 已在本地；本机未配置 registry mirror。基础镜像被清理时先拉取：

```bash
docker pull eclipse-temurin:17-jdk-jammy
docker pull eclipse-temurin:17-jre-jammy
```

重建镜像：

```bash
docker build --pull=false --target production -t petclinic:dev .
```

验证：

```bash
docker images petclinic:dev
docker inspect petclinic:dev --format '{{.Config.User}}'
docker run --rm --entrypoint id petclinic:dev
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

## 4. Kubernetes 部署

```bash
kubectl config use-context docker-desktop
kubectl get nodes
kubectl get pods -n petclinic
```

命名空间（已创建）：

```bash
kubectl create namespace petclinic
kubectl config set-context --current --namespace=petclinic
```

当前部署配置：

- `k8s/db.yml`：PostgreSQL 18.4，Service/Secret `demo-db`，角色 `user`，库 `petclinic`
- `k8s/petclinic.yml`：`petclinic:dev` + `postgres` profile + 显式 `POSTGRES_*`（不使用 Service Binding）+ startupProbe `/livez` + 优雅关闭 + `/readyz` 含 `db`
- Phase 1 验证通过：`/`、`/owners/1`、`/livez`、`/readyz`、`/actuator/health`
- Phase 2 验证通过：优雅关闭日志、`/readyz` 含 `db`
- Phase 3 验证通过：删 Pod 由 ReplicaSet 补齐；`kill 1` 容器原地重启、RESTARTS 加 1
- Phase 4 验证通过：扩到 3 副本，EndpointSlice 有 3 个 endpoint；删 1 个后补齐，已恢复 `replicas: 1`
- 手工查库用 `-U user`（没有 `postgres` 角色）

### 4.1 部署

```bash
kubectl apply -n petclinic -f k8s/db.yml
kubectl rollout status -n petclinic deployment/demo-db --timeout=180s
kubectl get pods -n petclinic -l app=demo-db -o wide

kubectl apply -n petclinic -f k8s/petclinic.yml
kubectl rollout status -n petclinic deployment/petclinic --timeout=180s
kubectl get pods -n petclinic -o wide
kubectl get rs -n petclinic
kubectl logs -n petclinic deployment/petclinic --tail=100
```

### 4.2 端口转发与入口验证

终端 A，保持运行：

```bash
kubectl port-forward -n petclinic svc/petclinic 8080:80
```

终端 B，逐条执行：

```bash
curl -sS -o /dev/null -w 'GET /               -> %{http_code}\n' localhost:8080/
curl -sS -o /dev/null -w 'GET /owners/1       -> %{http_code}\n' localhost:8080/owners/1
curl -sS -o /dev/null -w 'GET /livez          -> %{http_code}\n' localhost:8080/livez
curl -sS -o /dev/null -w 'GET /readyz         -> %{http_code}\n' localhost:8080/readyz
curl -sS -o /dev/null -w 'GET /actuator/health -> %{http_code}\n' localhost:8080/actuator/health
curl -sS localhost:8080/actuator/health; echo
```

预期：全部 200，health 为 `{"status":"UP"}`。

手工查库：

```bash
kubectl exec -n petclinic deployment/demo-db -- psql -U user -d petclinic -c 'select count(*) from owners;'
```

### 4.3 生命周期验证（Phase 2）

```bash
kubectl exec -n petclinic deployment/petclinic -- env | grep -E 'SERVER_SHUTDOWN|SPRING_LIFECYCLE|READINESS'
kubectl get deployment -n petclinic petclinic -o jsonpath='{.spec.template.spec.terminationGracePeriodSeconds}{"\n"}'
```

优雅关闭（一个终端、整块一次执行）：

```bash
POD=$(kubectl get pods -n petclinic -l app=petclinic -o jsonpath='{.items[0].metadata.name}')
kubectl logs -f -n petclinic "$POD" &
sleep 2
kubectl delete pod -n petclinic "$POD" --wait=false
wait
```

预期日志：

```text
Commencing graceful shutdown. Waiting for active requests to complete
Graceful shutdown complete
```

### 4.4 自愈验证（Phase 3）

终端 A，持续观察，Ctrl+C 退出：

```bash
kubectl get pods -n petclinic -l app=petclinic -w
```

终端 B，删除 Pod：

```bash
kubectl delete pod -n petclinic -l app=petclinic
```

容器级重启，终端 A：

```bash
kubectl get pods -n petclinic -l app=petclinic -w
```

终端 B：

```bash
kubectl exec -n petclinic deployment/petclinic -- sh -c 'kill 1'
```

预期：删 Pod 出现新 Pod 名；`kill 1` 的 Pod 名不变、RESTARTS 加 1。

### 4.5 扩容验证（Phase 4）

```bash
kubectl set env -n petclinic deployment/petclinic SPRING_SQL_INIT_MODE=never
kubectl rollout status -n petclinic deployment/petclinic --timeout=180s
kubectl scale -n petclinic deployment/petclinic --replicas=3
kubectl rollout status -n petclinic deployment/petclinic --timeout=180s
kubectl get pods -n petclinic -l app=petclinic -o wide
kubectl get endpointslices -n petclinic -l kubernetes.io/service-name=petclinic
```

删除一个副本并观察补齐：

```bash
kubectl delete pod -n petclinic "$(kubectl get pods -n petclinic -l app=petclinic -o jsonpath='{.items[0].metadata.name}')"
kubectl get pods -n petclinic -l app=petclinic -w
```

恢复声明式配置：

```bash
kubectl apply -n petclinic -f k8s/petclinic.yml
kubectl rollout status -n petclinic deployment/petclinic --timeout=180s
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

1. Week 1 验收文档：`docs/week-1-acceptance.md`（已完成）。
2. 进入 Week 2：resources/limits、HPA、RollingUpdate/rollback。
