# Cloud-native & SRE 学习计划

**目标岗位：** Staff Engineer / Principal Engineer  
**学习周期：** 4 周  
**建议投入：** 每天 1–1.5 小时，每周 5 天  
**学习方式：** 基于现有项目进行实践，结合架构分析与面试准备。

---

## 一、学习目标

本计划不是为了培养 Kubernetes Administrator 或专职 SRE，而是补齐 Principal Engineer 所需要的 Cloud-native Architecture 和 Reliability Engineering 能力。

完成学习后，应具备以下能力：

1. 理解容器化应用的部署、运行和管理机制。
2. 能够设计基于 Kubernetes 的微服务架构。
3. 理解服务自动扩缩容、故障恢复和安全发布机制。
4. 能够设计 Metrics、Logs、Distributed Tracing 可观测性体系。
5. 理解 SLI、SLO、SLA、Error Budget 等 SRE 核心概念。
6. 能够进行生产故障分析（Production Incident Analysis）和根因分析（RCA）。
7. 能够从架构层面讨论 Availability、Scalability、Reliability 和 Resilience。

## 二、四周学习路线

### Week 1：Docker & Kubernetes 基础

**学习目标：理解应用如何容器化，以及如何部署到 Kubernetes 集群。**

#### 1. 学习内容

**Docker**

- Container 与 Virtual Machine 的区别。
- Docker Image 和 Container。
- Dockerfile 和 Image Layers。
- Container Registry。
- Container Networking 和 Volumes。

**Kubernetes 架构**

- Control Plane 和 Worker Node。
- Pod 和 ReplicaSet。
- Deployment。
- Service 和 Ingress。
- Namespace。
- ConfigMap 和 Secret。

**应用健康检查**

- Readiness Probe：就绪探针。
- Liveness Probe：存活探针。
- Startup Probe：启动探针。
- Graceful Shutdown：优雅关闭。

#### 2. 动手实践

- [ ] 为现有项目编写 Dockerfile。
- [ ] 构建 Docker Image，并在本地运行。
- [ ] 部署应用到本地 Kubernetes 集群。
- [ ] 配置 Deployment 和多个 Replicas。
- [ ] 通过 Kubernetes Service 暴露应用。
- [ ] 配置 Readiness Probe 和 Liveness Probe。
- [ ] 模拟 Pod 故障，观察 Kubernetes 如何自动恢复。

#### 3. 阶段验收

能够清楚解释：

- Kubernetes 如何维持期望的 Pod 副本数量？
- Pod、Deployment 和 Service 分别解决什么问题？
- Kubernetes 如何进行应用健康检查？
- Container 和 Pod 出现故障时，Kubernetes 如何处理？

---

### Week 2：Cloud-native Reliability & Resilience

**学习目标：理解应用如何应对故障、流量突增和部署风险。**

#### 1. 学习内容

**资源管理（Resource Management）**

- CPU / Memory Requests 和 Limits。
- Horizontal Pod Autoscaler（HPA）。
- Resource Utilization。
- Capacity Planning。

**部署策略（Deployment Strategies）**

- Rolling Update：滚动更新。
- Rollback：版本回滚。
- Blue-Green Deployment：蓝绿部署。
- Canary Release：金丝雀发布。

**弹性设计模式（Resilience Patterns）**

- Timeout 和 Deadline。
- Retry with Exponential Backoff：指数退避重试。
- Retry Jitter：重试随机抖动。
- Circuit Breaker：熔断器。
- Bulkhead：舱壁隔离。
- Rate Limiting：限流。
- Graceful Degradation：服务降级。

**应用架构设计**

- Stateless Services：无状态服务。
- Externalized State：外部化状态管理。
- Health Checks：健康检查。
- Graceful Shutdown：优雅关闭。

#### 2. 动手实践

- [ ] 配置 CPU 和 Memory Requests / Limits。
- [ ] 配置 HPA，实现自动扩缩容。
- [ ] 模拟流量增加，观察服务扩容行为。
- [ ] 执行 Rolling Update。
- [ ] 测试应用版本回滚。
- [ ] 使用 Resilience4j 实现 Circuit Breaker。
- [ ] 模拟下游服务故障。
- [ ] 验证故障是否会在服务之间级联传播。

#### 3. 阶段验收

能够清楚解释：

- Kubernetes 如何应对流量突增？
- HPA 如何决定是否扩容？
- Readiness Probe 和 Liveness Probe 有什么区别？
- Circuit Breaker 和 Bulkhead 如何防止级联故障？
- 如何在尽量不影响服务的情况下发布新版本？

---

### Week 3：SRE & Observability

**学习目标：从应用能够运行，提升到能够持续监控、衡量和改善生产系统的可靠性。**

#### 1. 学习内容

**可观测性（Observability）**

- Metrics：指标。
- Logs：日志。
- Distributed Tracing：分布式链路追踪。
- OpenTelemetry：可观测性标准与工具体系。

**监控体系（Monitoring）**

- Prometheus。
- Grafana。
- PromQL。
- Alerting：告警机制。

**监控方法论**

- RED Method：Rate、Errors、Duration。
- USE Method：Utilization、Saturation、Errors。

**SRE 核心概念**

- SLI（Service Level Indicator）：服务等级指标。
- SLO（Service Level Objective）：服务等级目标。
- SLA（Service Level Agreement）：服务等级协议。
- Error Budget：错误预算。
- Availability：可用性。
- Latency：延迟。
- MTTD（Mean Time to Detect）：平均故障发现时间。
- MTTR（Mean Time to Recover）：平均故障恢复时间。

**故障管理（Incident Management）**

- Incident Detection：故障发现。
- Incident Response：故障响应。
- Root Cause Analysis（RCA）：根因分析。
- Blameless Postmortem：无责事后复盘。

#### 2. 动手实践

- [ ] 为应用暴露 Prometheus Metrics。
- [ ] 部署 Prometheus 和 Grafana。
- [ ] 建立服务监控 Dashboard。
- [ ] 监控请求量、错误率和响应延迟。
- [ ] 集成 OpenTelemetry，实现分布式链路追踪。
- [ ] 配置服务异常和高延迟告警。
- [ ] 为应用定义至少一个 SLO。
- [ ] 模拟一次生产故障，练习故障定位和恢复。

#### 3. SLO 示例

- 可用性：99.9% 的请求成功完成。
- 延迟：99% 的请求在 300ms 内完成。

以上只是学习示例，实际生产目标应该根据业务要求和系统运行基线确定。

#### 4. 阶段验收

能够清楚解释：

- Metrics、Logs 和 Traces 分别解决什么问题？
- 如何定义有业务意义的 SLI 和 SLO？
- Error Budget 如何影响发布决策？
- 如何发现和定位生产环境中的故障？
- 如何衡量和改善 MTTD 与 MTTR？

---

### Week 4：Principal Engineer 架构设计与系统可靠性

**学习目标：把 Cloud-native、Distributed Systems 和 SRE 知识整合到架构设计与技术决策中。**

#### 1. 学习内容

**高可用架构（High Availability）**

- Redundancy：冗余设计。
- Multi-AZ Architecture：多可用区架构。
- Multi-Region Architecture：多地域架构。
- Load Balancing：负载均衡。
- Failover：故障切换。

**可靠性工程（Reliability Engineering）**

- Fault Tolerance：容错。
- Failure Isolation：故障隔离。
- Graceful Degradation：服务降级。
- Disaster Recovery：灾难恢复。
- Backup and Restore：备份与恢复。
- RTO（Recovery Time Objective）：恢复时间目标。
- RPO（Recovery Point Objective）：恢复点目标。

**生产就绪性（Production Readiness）**

- Production Readiness Review（PRR）：生产就绪评审。
- Capacity Planning：容量规划。
- Security and Access Control：安全与访问控制。
- Deployment and Rollback Strategy：部署与回滚策略。
- Monitoring and Alerting Strategy：监控与告警策略。

**架构权衡（Architecture Trade-offs）**

- Availability vs Consistency：可用性与一致性。
- Latency vs Reliability：延迟与可靠性。
- Cost vs Redundancy：成本与冗余。
- Synchronous vs Asynchronous Processing：同步与异步处理。

#### 2. 动手实践

- [ ] 绘制完整的应用架构图。
- [ ] 识别架构中的潜在单点故障（SPOF）。
- [ ] 定义故障场景和恢复策略。
- [ ] 检查资源分配和自动扩缩容策略。
- [ ] 制定监控和告警要求。
- [ ] 编写部署和回滚方案。
- [ ] 完成一次 Production Readiness Review。
- [ ] 为重要架构决策编写 ADR（Architecture Decision Record）。

#### 3. 重点故障场景

| 故障场景 | 需要考虑的设计方案 |
|---|---|
| Pod 崩溃 | 自动重启与副本恢复 |
| Node 不可用 | 工作负载重新调度 |
| 流量突增 | 水平自动扩容 |
| 数据库响应缓慢 | Timeout、连接池管理、Backpressure |
| Kafka 不可用 | Retry、Buffering、Failure Isolation |
| 下游服务故障 | Circuit Breaker、Graceful Degradation |
| 新版本发布引入 Bug | Rollback、Release Validation |
| 服务可用性下降 | Alerting、Incident Response、Recovery |

#### 4. 阶段验收

能够独立设计和解释一个生产级 Cloud-native 系统，包括：

- 架构设计与技术决策。
- 故障处理与恢复策略。
- 可观测性体系。
- 生产部署与运维准备。
- 可靠性与成本之间的权衡。

---

## 三、项目实践交付物

以现有项目为基础，不需要为了学习而重新开发复杂业务功能。

四周结束时，争取完成以下成果：

- [ ] Dockerfile 和容器化应用。
- [ ] Kubernetes Deployment 和 Service 配置。
- [ ] Health Check 和资源配置。
- [ ] HPA 和自动扩缩容配置。
- [ ] Prometheus 监控配置。
- [ ] Grafana Dashboard。
- [ ] OpenTelemetry 链路追踪集成。
- [ ] SLI / SLO 定义文档。
- [ ] 故障模拟与恢复记录。
- [ ] 系统架构图。
- [ ] Production Readiness Review 文档。

## 四、推荐学习资料

| 学习主题 | 推荐资源 |
|---|---|
| Docker | https://docs.docker.com/get-started/ |
| Kubernetes | https://kubernetes.io/docs/tutorials/ |
| Minikube | https://minikube.sigs.k8s.io/docs/start/ |
| Prometheus | https://prometheus.io/docs/introduction/overview/ |
| Grafana | https://grafana.com/docs/grafana/latest/ |
| OpenTelemetry | https://opentelemetry.io/docs/ |
| Google SRE Book | https://sre.google/sre-book/table-of-contents/ |

**推荐阅读顺序：**

1. Kubernetes 官方教程：掌握核心资源和部署机制。
2. Google SRE Book：重点阅读 Service Level Objectives、Monitoring Distributed Systems、Eliminating Toil 和 Managing Incidents。
3. Prometheus 和 OpenTelemetry 官方文档：理解监控与链路追踪的实现方式。

## 五、面试准备清单

完成学习后，针对以下问题准备结构化回答。

### Kubernetes & Cloud-native

- 如何在 Kubernetes 上部署一个高可用微服务？
- Kubernetes 如何检测和恢复应用故障？
- Readiness Probe 和 Liveness Probe 有什么区别？
- HPA 的工作原理是什么？
- 如何实现尽量零停机的应用发布？

### SRE & Reliability

- 如何定义 SLI、SLO 和 SLA？
- 什么是 Error Budget？它如何影响工程决策？
- 如何监控一个分布式系统？
- 如何定位生产环境中的高延迟问题？
- 如何降低 MTTR？

### Principal Engineer Architecture

- 如何设计一个高可用的分布式系统？
- 如何识别并消除系统中的单点故障？
- 如何处理级联故障？
- 如何进行 Production Readiness Review？
- 如何平衡系统可靠性、性能和基础设施成本？

## 六、学习原则

1. **实践优先：** 尽量通过实际操作理解概念，而不是只看视频和文档。
2. **复用已有经验：** 将新知识与 Java、Spring Boot、Kafka、Spark 和生产数据平台经验联系起来。
3. **关注架构原理：** 不只是学习工具如何配置，更要理解为什么需要这项能力。
4. **主动模拟故障：** 不理解故障与恢复，就不能真正掌握系统可靠性。
5. **准备面试表达：** 每个重要知识点都应该能够用清晰、有结构的英文解释。

---

## 最终目标

从：

**构建和维护分布式应用系统（Building and Maintaining Distributed Applications）**

进一步提升到：

**设计、运营和治理大规模、高可靠的 Cloud-native 平台（Designing, Operating and Governing Reliable Cloud-native Platforms at Scale）。**

这才是 Staff Engineer / Principal Engineer 所需要的 Cloud-native 和 SRE 能力，而不只是会使用 Docker、Kubernetes 或监控工具。