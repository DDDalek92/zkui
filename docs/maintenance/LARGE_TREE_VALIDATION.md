# Large-tree pagination and work bounds

I02 implementation candidate. Browsing sorts child names and loads only one page of 100 nodes; folder classification uses Stat.numChildren rather than fetching each child's entire child-name list. Page links retain context and namespace. Sorting controls apply to the current page. Child membership can change between requests; this is not a ZooKeeper snapshot or server-side paginated protocol.

The client response buffer defaults to 8 MiB, configurable through zkMaxBufferBytes or an explicit jute.maxbuffer JVM property (1-16 MiB). It is set before ZooKeeper initialization. Browsing refuses parents above 500,000 children. ZooKeeper still returns the complete child-name list in one response; names exceeding the configured packet bound can still fail. Pagination bounds data/HTML work; it cannot promise arbitrary-width trees.

Export/search now traverse iteratively with one child-list fetch per visited folder, bounded by 10,000 discovered nodes (including pending work), depth128, 8 MiB of estimated path/data bytes and a 10-second between-request work budget. Bulk deletion completes bounded discovery before its first deletion. Size/depth refusal leaves the tree untouched. Concurrent changes, network failures or a time limit during the deletion phase can still cause partial mutation: the error asks the operator to inspect state. No multi-node transactional delete is claimed. The work deadline does not cancel an already blocked ZooKeeper RPC.

Load regression uses the test-owned ZooKeeper server's real in-memory DataTree populated with312,763 children, matching upstream#6. Reads go through the real HTTP/JAR/ZooKeeper protocol; fixture insertion bypasses transaction logging, so this is not a write-throughput, durability or production-network benchmark. Tests verify first/second/last pages, invalid pages, context links, refused whole-tree export/search/delete with children intact, page-value bytes, and depth refusal without mutation.

All exact-head platform/browser/container CI must pass before merge. This does not reproduce the original Windows9s/Linux1s environment in#85. No users' ZooKeeper/database service or credentials are used.

Final review additionally requires one budget/preflight across all selected deletion roots and de-duplicates overlapping selections. A combined12,000-child selection is refused with both6,000-child parents intact; selecting a child and its ancestor deletes each once in descendant-first order.
