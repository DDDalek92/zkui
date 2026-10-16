# 2026-10-16 接续：跨根删除预检

PR #40 已在准确 head `c837c550` 的 PR `38836967274`、push `38836958256` 六项 CI 成功后审查合并为 `d3a45f0`。其本地超时单独处理，不因 CI 成功而抹除。

此前深度优先逐根收集删除目标：两个各有 6000 个孩子的根，遍历完首根的 6000 个叶子后才发现总预算超限。现以同一个广度优先队列先检查所有选中根，再检查后代；保持 10000 节点、128 深度、8 MiB、10 秒限制、全量预检后删除、后代先于祖先的删除顺序。没有增大 HTTP 超时、跳过断言或放宽预算。

新增确定性真实 ZooKeeper 协议回归：两个 6000 子节点根必须在读取任意后代前拒绝，读取最多每根两次，并保留全部节点。旧实现已由该断言稳定判红（而非依赖时间碰运气）。原有 HTTP 413 与未删除断言保留。首轮本地基线构建与定向判红曾意外重叠，基线输出不作为有效完整验收；修复后将单独 clean verify 重建、核对报告。结果以对应 PR 准确 head CI 为准。

此项修复明确的低效路径，不宣称复现上游 Windows 9s:1s 原始环境，也不提供 ZooKeeper 并发变化下的跨节点原子事务。

---

# Large-tree pagination and work bounds

I02 implementation candidate. Browsing sorts child names and loads only one page of 100 nodes; folder classification uses Stat.numChildren rather than fetching each child's entire child-name list. Page links retain context and namespace. Sorting controls apply to the current page. Child membership can change between requests; this is not a ZooKeeper snapshot or server-side paginated protocol.

The client response buffer defaults to 8 MiB, configurable through zkMaxBufferBytes or an explicit jute.maxbuffer JVM property (1-16 MiB). It is set before ZooKeeper initialization. Browsing refuses parents above 500,000 children. ZooKeeper still returns the complete child-name list in one response; names exceeding the configured packet bound can still fail. Pagination bounds data/HTML work; it cannot promise arbitrary-width trees.

Export/search now traverse iteratively with one child-list fetch per visited folder, bounded by 10,000 discovered nodes (including pending work), depth128, 8 MiB of estimated path/data bytes and a 10-second between-request work budget. Bulk deletion completes bounded discovery before its first deletion. Size/depth refusal leaves the tree untouched. Concurrent changes, network failures or a time limit during the deletion phase can still cause partial mutation: the error asks the operator to inspect state. No multi-node transactional delete is claimed. The work deadline does not cancel an already blocked ZooKeeper RPC.

Load regression uses the test-owned ZooKeeper server's real in-memory DataTree populated with312,763 children, matching upstream#6. Reads go through the real HTTP/JAR/ZooKeeper protocol; fixture insertion bypasses transaction logging, so this is not a write-throughput, durability or production-network benchmark. Tests verify first/second/last pages, invalid pages, context links, refused whole-tree export/search/delete with children intact, page-value bytes, and depth refusal without mutation.

All exact-head platform/browser/container CI must pass before merge. This does not reproduce the original Windows9s/Linux1s environment in#85. No users' ZooKeeper/database service or credentials are used.

Final review additionally requires one budget/preflight across all selected deletion roots and de-duplicates overlapping selections. A combined12,000-child selection is refused with both6,000-child parents intact; selecting a child and its ancestor deletes each once in descendant-first order.
