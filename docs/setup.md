# 环境搭建记录

本机 Cloud-native & SRE 学习环境索引。最后更新：2026-11-25。

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

### 4.6 资源配置（Week 2）

- App：requests `cpu=250m`、`memory=512Mi`；limits `cpu=2`、`memory=1Gi`；QoS `Burstable`。
- 无 limit 时 JVM max heap 7.05 GiB；1Gi limit 后 `MaxHeapSize=805306368`（768 MiB）。
- 测量基线：空闲 RSS 约 401 MiB，heap 85 MiB，non-heap 134 MiB；空闲 CPU 约 0.005 核。
- 初始 `cpu=1`：JVM `system.cpu.count=1`，GC 从 G1 变为 Serial（`UseSerialGC=true`）；空闲无 throttling，轻负载出现 `nr_throttled +12`、`throttled_usec +0.966s`。
- 调参后 `cpu=2`：JVM `system.cpu.count=2`，仍为 Serial GC；同样轻负载下 `nr_throttled` 与 `throttled_usec` 增量均为 0。
- Actuator `jvm.memory.max?tag=area:heap` 为 742.5 MiB，是池 max 求和少算一个 survivor，不是堆上限。
- live 仍有 `SPRING_SQL_INIT_MODE=never`，`k8s/petclinic.yml` 未声明；HPA 前补上。
- `requests.cpu=250m` 仍是初始值；HPA 压测时根据实际利用率校准。
- 1 核 → 2 核的对照数据和 JVM 视角见 4.7。
- 下一步：安装 metrics-server、配置 HPA、做负载测试。

### 4.7 测量与验证命令

Actuator 查询前保持端口转发（终端 A）：

```bash
kubectl port-forward -n petclinic svc/petclinic 8080:80
```

配置前基线：

```bash
kubectl exec -n petclinic deployment/petclinic -- sh -c 'echo memory.current=$(cat /sys/fs/cgroup/memory.current); echo memory.peak=$(cat /sys/fs/cgroup/memory.peak); echo memory.max=$(cat /sys/fs/cgroup/memory.max); grep -E "VmRSS|VmHWM|Threads" /proc/1/status; cat /sys/fs/cgroup/cpu.stat'
```

```bash
curl -s 'localhost:8080/actuator/metrics/jvm.memory.max'; echo
curl -s 'localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap'; echo
curl -s 'localhost:8080/actuator/metrics/jvm.memory.used?tag=area:nonheap'; echo
curl -s 'localhost:8080/actuator/metrics/process.cpu.usage'; echo
curl -s 'localhost:8080/actuator/metrics/system.cpu.count'; echo
```

配置后验证：

```bash
kubectl get pod -n petclinic -l app=petclinic -o jsonpath='{.items[0].spec.containers[0].resources}{"\n"}'
kubectl get pod -n petclinic -l app=petclinic -o jsonpath='{.items[0].status.qosClass}{"\n"}'
kubectl exec -n petclinic deployment/petclinic -- env | grep SPRING_SQL_INIT_MODE
```

```bash
kubectl exec -n petclinic deployment/petclinic -- sh -c 'echo memory.max=$(cat /sys/fs/cgroup/memory.max); echo memory.current=$(cat /sys/fs/cgroup/memory.current); echo cpu.max=$(cat /sys/fs/cgroup/cpu.max); cat /sys/fs/cgroup/cpu.stat'
kubectl exec -n petclinic deployment/petclinic -- java -XX:MaxRAMPercentage=75.0 -XX:+PrintFlagsFinal -version 2>/dev/null | grep -E "MaxHeapSize|UseSerialGC|UseG1GC"
```

```bash
curl -s 'localhost:8080/actuator/metrics/system.cpu.count'; echo
curl -s 'localhost:8080/actuator/metrics/jvm.memory.max?tag=area:heap'; echo
```

本次结果：

| 配置 | `cpu.max` | `system.cpu.count` | `MaxHeapSize` | GC |
|---|---|---|---:|---|
| `cpu=1` | `100000 100000` | 1.0 | 805306368 | Serial |
| `cpu=2` | `200000 100000` | 2.0 | 805306368 | Serial |

注意：`kubectl exec -- java` 不会 attach 到运行中的 Java 进程，而是在同一容器、同一 cgroup 里启动一个新的 JVM；它验证的是相同参数下的 ergonomics。运行中进程的启动参数用 `/proc/1/cmdline` 确认，运行指标用 Actuator。新 JVM 不继承 ENTRYPOINT 的 `-XX:MaxRAMPercentage=75.0`，所以验证 `MaxHeapSize` 时必须显式带上，否则得到默认 25% 的 256Mi。

CPU throttling 60 秒增量：

```bash
kubectl exec -n petclinic deployment/petclinic -- sh -c 'echo cpu.max=$(cat /sys/fs/cgroup/cpu.max); echo before; grep -E "usage_usec|nr_periods|nr_throttled|throttled_usec" /sys/fs/cgroup/cpu.stat; sleep 60; echo after; grep -E "usage_usec|nr_periods|nr_throttled|throttled_usec" /sys/fs/cgroup/cpu.stat'
```

- 空闲测试：执行期间不做页面操作。
- 轻负载测试：执行期间做同类页面操作。
- 判断看 delta：`nr_throttled` 与 `throttled_usec` 的增量。

本次结果（60 秒，页面操作）：

| 配置 | CPU 使用 | `nr_periods` | `nr_throttled` | `throttled_usec` |
|---|---:|---:|---:|---:|
| `cpu=1` | +2.435s | +249 | +12 | +0.966s |
| `cpu=2` | +2.725s | +229 | 0 | 0 |

注：`usage_usec` 等累计值受 Pod 启动和额外 `exec` 影响，比较只看 60 秒 delta。

### 4.8 metrics-server（HPA 前置）

- 版本：v0.9.1，本地清单 `k8s/metrics-server.yml`，来自官方 release 产物。
- 网络：GitHub 和 Docker Hub 不可达；清单经 jsDelivr 获取，镜像从 `registry.k8s.io` 拉取。
- 镜像：`registry.k8s.io/metrics-server/metrics-server:v0.9.1`。

安装：

```bash
kubectl apply -f k8s/metrics-server.yml
kubectl rollout status -n kube-system deployment/metrics-server --timeout=180s
kubectl get pods -n kube-system -l k8s-app=metrics-server -o wide
```

- 本地集群必须加 `--kubelet-insecure-tls`：Docker Desktop 的 kubelet serving cert 没有 IP SAN，默认校验会报 `x509: ... doesn't contain any IP SANs`，readiness 返回 500。该参数关闭 metrics-server 到 kubelet 的证书校验，**仅限本地学习集群**；生产环境应使用受信任 CA 签发的 kubelet serving cert。
- APIService 里的 `insecureSkipTLSVerify: true` 是官方默认值，作用于 API Server 到 metrics-server，和上面的 kubelet 参数不是一回事。

验证：

```bash
kubectl get apiservices | grep metrics
kubectl top nodes
kubectl top pods -n petclinic
```

2026-11-25 结果：

| 检查 | 结果 |
|---|---|
| APIService | `v1beta1.metrics.k8s.io` True |
| Node | `desktop-control-plane` 175m / 1976Mi |
| App Pod | `petclinic` 3m / 284Mi |
| DB Pod | `demo-db` 2m / 129Mi |

结论：metrics-server Ready，Metrics API 可用，HPA 的指标前置条件满足。

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
2. Week 2 资源配置：已完成并复测（见 4.6；CPU limit 2，轻负载无 throttling）。
3. metrics-server：已安装并验证（见 4.8）。
4. 下一步：把 `SPRING_SQL_INIT_MODE=never` 写进 `k8s/petclinic.yml`，然后配置 HPA 并压测。
