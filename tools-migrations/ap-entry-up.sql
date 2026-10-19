alter table tournaments add column if not exists entry_cost_ap integer check (entry_cost_ap is null or entry_cost_ap >= 0);
alter table registrations add column if not exists ap_paid integer not null default 0;

create or replace function enter_tournament_with_ap(p_id text, p_tournament text, p_squad text, p_captain text, p_uid text, p_ign text, p_phone text, p_proof text, p_client text, p_ip text)
returns jsonb language plpgsql security definer set search_path to 'public' as $f$
declare v_user uuid := auth.uid(); v_cost int; v_bal int; v_res jsonb;
begin
  if v_user is null then return jsonb_build_object('ok', false, 'error', 'Log in first.'); end if;
  select coalesce(entry_cost_ap,0) into v_cost from tournaments where id = p_tournament;
  if not found then return jsonb_build_object('ok', false, 'error', 'Tournament not found.'); end if;
  perform pg_advisory_xact_lock(hashtext('ap:'||v_user::text));
  if v_cost > 0 then
    select coalesce(sum(delta),0) into v_bal from points_ledger where user_id = v_user;
    if v_bal < v_cost then
      return jsonb_build_object('ok', false, 'error', 'Not enough AP. This entry costs '||v_cost||' AP, you have '||v_bal||'.', 'cost', v_cost, 'balance', v_bal);
    end if;
  end if;
  perform set_config('xarena.ap_entry','1',true);
  v_res := register_squad(p_id,p_tournament,p_squad,p_captain,p_uid,p_ign,p_phone,p_proof,p_client,p_ip);
  if coalesce((v_res->>'ok')::boolean,false) and v_cost > 0 then
    insert into points_ledger (user_id, delta, kind, meta) values (v_user, -v_cost, 'tournament_entry', jsonb_build_object('tournament', p_tournament, 'registration', p_id));
    update registrations set ap_paid = v_cost where id = p_id;
    v_res := v_res || jsonb_build_object('ap_charged', v_cost, 'balance', v_bal - v_cost);
  end if;
  return v_res;
end $f$;
grant execute on function enter_tournament_with_ap(text,text,text,text,text,text,text,text,text,text) to authenticated;

-- block paid tournaments being entered through plain register_squad (free ones untouched)
create or replace function trg_reg_paid_guard() returns trigger language plpgsql as $f$
begin
  if coalesce((select entry_cost_ap from tournaments where id = new.tournament_id),0) > 0
     and coalesce(current_setting('xarena.ap_entry', true),'') <> '1' then
    raise exception 'This tournament has an AP entry cost. Use the AP entry option.';
  end if;
  return new;
end $f$;
drop trigger if exists reg_paid_guard on registrations;
create trigger reg_paid_guard before insert on registrations for each row execute function trg_reg_paid_guard();

-- refund AP on reject/cancel/delete
create or replace function trg_reg_ap_refund() returns trigger language plpgsql security definer set search_path to 'public' as $f$
begin
  if tg_op = 'DELETE' then
    if old.ap_paid > 0 and old.user_id is not null then
      insert into points_ledger (user_id, delta, kind, meta) values (old.user_id, old.ap_paid, 'tournament_refund', jsonb_build_object('tournament', old.tournament_id, 'registration', old.id));
    end if;
    return old;
  end if;
  if old.ap_paid > 0 and new.status in ('rejected','cancelled','canceled') and old.status is distinct from new.status and old.user_id is not null then
    insert into points_ledger (user_id, delta, kind, meta) values (old.user_id, old.ap_paid, 'tournament_refund', jsonb_build_object('tournament', old.tournament_id, 'registration', old.id));
    new.ap_paid := 0;
  end if;
  return new;
end $f$;
drop trigger if exists reg_ap_refund_u on registrations;
create trigger reg_ap_refund_u before update of status on registrations for each row execute function trg_reg_ap_refund();
drop trigger if exists reg_ap_refund_d on registrations;
create trigger reg_ap_refund_d before delete on registrations for each row execute function trg_reg_ap_refund();
notify pgrst, 'reload schema';
