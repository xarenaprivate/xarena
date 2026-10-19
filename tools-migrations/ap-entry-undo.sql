drop trigger if exists reg_paid_guard on registrations;
drop trigger if exists reg_ap_refund_u on registrations;
drop trigger if exists reg_ap_refund_d on registrations;
drop function if exists trg_reg_paid_guard();
drop function if exists trg_reg_ap_refund();
drop function if exists enter_tournament_with_ap(text,text,text,text,text,text,text,text,text,text);
alter table registrations drop column if exists ap_paid;
alter table tournaments drop column if exists entry_cost_ap;
notify pgrst, 'reload schema';
