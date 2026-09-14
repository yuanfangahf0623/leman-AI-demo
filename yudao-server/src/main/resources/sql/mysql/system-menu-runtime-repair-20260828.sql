-- Keep local menu seed data usable after older runs left project menus disabled.

UPDATE `system_menu`
SET `status` = 0,
    `visible` = b'1',
    `deleted` = b'0',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `id` BETWEEN 910000 AND 910699
   OR `id` BETWEEN 920600 AND 920799;

-- Legacy data-platform routes and removed AI routes stay hidden in this service.
UPDATE `system_menu`
SET `status` = 1,
    `visible` = b'0',
    `updater` = 'admin',
    `update_time` = NOW()
WHERE `id` BETWEEN 920000 AND 920099
   OR `id` IN (910607, 910670, 910671);

INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`)
SELECT 1, `id`
FROM `system_menu`
WHERE `deleted` = b'0'
  AND `status` = 0
  AND EXISTS (
    SELECT 1
    FROM `system_role`
    WHERE `id` = 1
      AND `deleted` = b'0'
  );
