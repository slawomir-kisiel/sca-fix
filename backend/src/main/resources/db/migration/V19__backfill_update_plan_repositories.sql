-- Plans created before repository scoping was introduced can still be
-- associated with a repository through their target projects.
update update_plans p
set repository_id = (
    select pr.repository_id
    from plan_items pi
    join projects pr on pr.id = pi.target_project_id
    where pi.update_plan_id = p.id
    fetch first 1 row only
)
where p.repository_id is null
  and exists (
    select 1
    from plan_items pi
    join projects pr on pr.id = pi.target_project_id
    where pi.update_plan_id = p.id
  );
