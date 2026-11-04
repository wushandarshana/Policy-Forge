-- Renewal (AC-07.1, AC-07.3, NFR-05). A renewal term records the rule version its premium was rated with, so the term in
-- force keeps the version it was priced with while the next offer is refreshed with the version active on the run date.
-- Term 1 is the first term of every policy and takes the rule version of the policy's version 1 (the version the quote
-- was priced with at issuance). Existing rows are backfilled from it and the column is then made NOT NULL. policy_term
-- stays a mutable aggregate in one respect only (the paid flag), so this update touches no append-only table.
-- Fix forward: V1 to V9 are never edited.
ALTER TABLE policy_term ADD COLUMN rule_version INT;

UPDATE policy_term t
SET rule_version = (SELECT v.rule_version FROM policy_version v
                    WHERE v.policy_number = t.policy_number AND v.version_no = 1);

ALTER TABLE policy_term ALTER COLUMN rule_version SET NOT NULL;
ALTER TABLE policy_term ADD CONSTRAINT ck_policy_term_rule_version CHECK (rule_version >= 1);
