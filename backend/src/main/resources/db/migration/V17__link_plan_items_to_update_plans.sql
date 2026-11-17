alter table plan_items add column update_plan_id uuid;
alter table plan_items add column status varchar(30) not null default 'PLANNED';
alter table plan_items add column execution_order integer not null default 0;
alter table plan_items drop constraint if exists uq_plan_item_occurrence;
alter table plan_items add constraint uq_plan_item_plan_occurrence unique (update_plan_id, occurrence_id);
alter table plan_items add constraint fk_plan_item_update_plan foreign key (update_plan_id) references update_plans(id) on delete cascade;
create index ix_plan_items_update_plan on plan_items(update_plan_id);
