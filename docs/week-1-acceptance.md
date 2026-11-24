# Week 1 验收记录

日期：2026-11-24
环境：Docker Desktop Kubernetes v1.36.4，namespace `petclinic`

## 验证结果

| 检查 | 结果 |
|---|---|
| 镜像 `petclinic:dev` | Boot 4.1.0，非 root UID 10001 |
| 部署 | DB 与 App Pod 均 Ready |
| 入口 | `/`、`/owners/1`、`/livez`、`/readyz`、`/actuator/health` 均返回 200 |
| 优雅关闭 | `Commencing graceful shutdown` / `Graceful shutdown complete` |
| 自愈：删 Pod | 新 Pod 名，ReplicaSet 自动补齐 |
| 自愈：`kill 1` | Pod 名不变，RESTARTS 加 1 |
| 扩容 | 3 副本 Ready，EndpointSlice 3 个 endpoint；删 1 个后补齐 |
| 恢复 | `kubectl apply` 回到 1 副本，并移除临时 `SPRING_SQL_INIT_MODE` |

## 关键实现

- 本地构建的镜像没有 `spring-cloud-bindings`，K8s 用显式 `POSTGRES_*` 环境变量，凭据来自 Secret `demo-db`。
- startup / liveness / readiness 分离；`/readyz` 包含 `db`，liveness 不包含数据库。
- 优雅关闭 30s，`terminationGracePeriodSeconds: 40`。
- 扩容前设 `SPRING_SQL_INIT_MODE=never`；单副本时由应用启动执行 SQL 初始化。

## 验收问题

### 1. Kubernetes 如何维持期望的 Pod 副本数量？

Deployment 声明期望副本数，Deployment controller 创建/更新 ReplicaSet；ReplicaSet controller 持续比较实际与期望 Pod 数，不足就创建，超出就删除。scheduler 选择节点，kubelet 启动容器。节点失联时，node controller 标记 Pod 不健康，ReplicaSet 在其他节点创建替代 Pod。

### 2. Pod、Deployment 和 Service 分别解决什么问题？

- Pod：最小调度单元，一个或多个共享网络、IP 和卷的容器。
- Deployment：声明式管理 Pod 副本、滚动更新和回滚，通过 ReplicaSet 管理 Pod。
- Service：为临时 Pod IP 提供稳定的虚拟 IP/DNS 和四层负载均衡，通过 label selector 选择 Pod，并用 EndpointSlice 维护实际后端。

### 3. Kubernetes 如何进行应用健康检查？

- startupProbe：启动期间生效，成功后 liveness/readiness 才开始。
- livenessProbe：失败时 kubelet 重启容器，适合检测进程卡死。
- readinessProbe：失败时把 Pod 从 Service 摘除，不重启，适合检测是否能接流量。
- 本应用 `/livez` 对应 liveness group，`/readyz` 对应 readiness group（包含 `db`）。

### 4. Container 和 Pod 出现故障时，Kubernetes 如何处理？

- 容器进程退出：kubelet 按 `restartPolicy: Always` 原地重启容器，Pod 名不变，RESTARTS 加 1。
- Pod 被删除：ReplicaSet 创建新 Pod，名字和 IP 变化。
- 节点故障：node controller 标记并驱逐 Pod，ReplicaSet 在其他节点重建；单节点本地集群无法真正验证节点级重调度。

## 已知限制

- 单节点 Docker Desktop，无法验证节点故障后的重新调度。
- Postgres 是单副本 Deployment 且无 PVC，属于单点故障且数据易失；高可用和备份留到 Week 4。
- 应用启动时执行 SQL 初始化，不适合多副本并发；生产应使用独立迁移工具。

## 下一步

Week 2：resources/limits、HPA、RollingUpdate/rollback。
