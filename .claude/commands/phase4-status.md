---
description: Check Phase 4 cleanup progress — reads the actual codebase and reports which of the 3 steps are done.
---

Check the current Phase 4 cleanup progress by examining the actual codebase. Do not rely on memory — read the files.

Report the status of each of the three steps defined in CLAUDE.md:

---

**Step 1 — Product code removed from monolith**
Check whether `src/main/java/org/example/springboot0/product/` still exists and contains files.
List any subdirectories still present (domain, application, infrastructure, api).
If the directory is gone entirely: ✅ Done.

**Step 2 — Product tables dropped from monolith DB**
Scan `src/main/resources/db/migration/` for any migration that drops the products table, product_categories table, or the tsvector column.
If no such migration exists: the tables are still there. ❌ Not started.

**Step 3 — Outbox sync decommissioned**
Check two things independently:
- Does `src/main/java/org/example/springboot0/shared/outbox/` still contain files?
- Does `product-service/src/main/java/org/example/productservice/product/infrastructure/ProductEventConsumer.java` still exist?

Both must be gone for this step to be complete.

---

Format your answer as a checklist with ✅ Done / ⏳ Partial / ❌ Not started for each step, followed by one sentence saying what specifically remains.
