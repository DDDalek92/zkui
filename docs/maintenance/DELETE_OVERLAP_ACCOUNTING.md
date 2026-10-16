# I02 correction: overlapping selection budget

Latest remote baseline15a281d9571255de01bb1dd7d4fd59837590328a (#41), isolated checkout. This is a separate correction after #41 merged, not a duplicate of its early cross-root work fix. No upstream writes, users' services, databases or credentials.

## Reproduction and review

#41 exact head2e5fffaf63ae389f6d3d90f7cd7add485dd35164 passed its existing CI but introduced a boundary not covered there. A root with9998 children plus two explicitly selected existing children is a9999-node union. Reserving3 selected roots then9998 children rejects10001. A real loopback protocol probe failed deterministically at2026-10-16 02:06:21 UTC with TreeWork.LimitException instead of reaching its deliberately stopping descendant probe (7 unit pass, one integration failure, BUILD FAILURE). The probe stops before any delete and is not a latency/full-delete benchmark. #41 merged while that independent review was completing, so work resumes from its new master without rewriting its branch.

An unpublished alternative from the preceding baseline was not submitted when #41 was discovered; its local results are not treated as validation of this correction or #41. No duplicate PR was created for the original early rejection issue.

## Correction

Validate paths and bound the number/estimated bytes of distinct selections, then coalesce only real segment-prefix ancestors before the existing shared breadth-first queue. Disjoint selected roots cannot rediscover one another, so normal tree traversal counts their union without selected descendants twice. Keep the breadth-first early cross-root refusal, all existing limits (10000 nodes/depth128/8MiB/10 seconds), HTTP timeout, and descendant-first mutation order unchanged.

After collection and before any mutation, every original selected path must appear in targets. Thus a selected missing descendant still refuses even when its existing ancestor covered the traversal root. Opposite selection orders, duplicate selections and similar textual prefixes have explicit regressions. The primary operation deadline includes normalization and traversal; auxiliary selection limits are not a larger runtime allowance.

## Regression scope

Focused corrected verification completed2026-10-16 02:10:25 UTC:7 unit+9 integration=16, zero failures/errors/skips, BUILD SUCCESS. This includes all existing six LargeTreeIT cases and three new overlap-accounting cases. Full clean verify and exact-head CI remain pending at this snapshot; local environment has no Docker/MySQL/Chrome and cannot validate those jobs.

New DeleteOverlapAccountingIT:9999-node union accepted at discovery in both root/child orders and with duplicates, deliberately interrupted at first descendant read before deletes; missing selected descendants preserve ancestor/child in both orders; two6000-child roots with similar text prefixes still refuse with data intact. Existing LargeTreeIT keeps actual overlapping deletion, early4-read cap, HTTP413,312763 children and depth/byte checks. Full clean verify and all six exact-head CI jobs/logs are separate merge gates.

Only owned loopback ZooKeeper and temporary synthetic fixtures are used. No POM, V1, database migration implementation, runtime limit or HTTP timeout changes. No transactional deletion, universal latency or original Windows9s:1s root-cause claim. D01 broader time precision/timezones, filesystem permissions/disk failure, security/license/SBOM and deployment remain unfinished;14 classified PRs/46 classified issues are not all fixed.
