---
description: Review implementation against plan and specs — final gate before merge
argument-hint: <feature or branch>
---

# Review Implementation (Gate Review)

Verify that code changes match the plan, conform to specs, and pass all tests with required coverage.

## Steps:

1. **Read the plan** that was executed
2. **Compare code vs. plan**: Check each planned change was implemented
3. **Check against specs**:
   - `docs/api-spec.md` — do endpoints match the contract?
   - `docs/data-model.md` — does schema match the spec?
   - `docs/python-pipeline.md` — do scripts follow the CLI interface contract?
   - `docs/scoring-spec.md` — does scoring match the formula?
4. **Run `/test`** to delegate to the test-runner sub-agent for a full gate report across all suites
5. **Gate criteria — ALL must pass** (from the test-runner report):
   - Test pass rate: **100%** (zero failures allowed)
   - Code coverage: **≥95%** on changed modules
   - No compiler warnings or type errors
   - No skipped or disabled tests
6. **Document deviations**: Note any intentional or accidental differences from plan
7. **Update docs**: If implementation diverged from spec, update the spec doc to match
8. **Write journal**: Log what was reviewed, deviations found, gate result to journal. Do NOT expand memory blocks.

## Rules:
- When code exists, prefer executable source of truth over plan docs
- If code is correct but spec is outdated, update the spec — don't rewrite the code
- If code is wrong but spec is correct, fix the code
- **GATE BLOCKS**: If any gate criterion fails, the change CANNOT proceed. Fix the issue, do not bypass.
- **KEEP MEMORY LEAN**: Write details to journal, not memory. Memory = compressed pointers only.

## Gate Checklist (must all be YES):
- [ ] All integration tests pass (100%)
- [ ] Coverage ≥95% on changed modules
- [ ] No compiler/type errors
- [ ] API contract matches `docs/api-spec.md`
- [ ] Python scripts follow `docs/python-pipeline.md` contract
- [ ] No new warnings introduced
- [ ] Docs updated if behavior diverged from spec

$ARGUMENTS
