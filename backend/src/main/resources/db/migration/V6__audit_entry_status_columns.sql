-- Status changes in the audit trail (underwriting spec U-12): the status before and after an action. Both are NULL for
-- entries that are not status changes (for example a rule-set publish), so existing rows stay valid. V2 is not edited.
ALTER TABLE audit_entry ADD COLUMN previous_status VARCHAR(30);
ALTER TABLE audit_entry ADD COLUMN new_status VARCHAR(30);
