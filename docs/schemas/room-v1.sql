-- Generated from exported Room v1 schema. SQLite documentation snapshot only.

-- Do not apply this to Supabase/PostgreSQL. No schema changes are proposed here.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS `households` (`id` TEXT NOT NULL, `displayCode` TEXT NOT NULL, `headName` TEXT NOT NULL, `address` TEXT NOT NULL, `status` TEXT NOT NULL, PRIMARY KEY(`id`));

CREATE UNIQUE INDEX IF NOT EXISTS `index_households_displayCode` ON `households` (`displayCode`);

CREATE TABLE IF NOT EXISTS `members` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `displayName` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_members_householdId` ON `members` (`householdId`);

CREATE UNIQUE INDEX IF NOT EXISTS `index_members_id_householdId` ON `members` (`id`, `householdId`);

CREATE TABLE IF NOT EXISTS `interviews` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `questionnaireVersion` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE UNIQUE INDEX IF NOT EXISTS `index_interviews_householdId` ON `interviews` (`householdId`);

CREATE UNIQUE INDEX IF NOT EXISTS `index_interviews_id_householdId` ON `interviews` (`id`, `householdId`);

CREATE TABLE IF NOT EXISTS `visits` (`id` TEXT NOT NULL, `interviewId` TEXT NOT NULL, `outcome` TEXT NOT NULL, `callbackAt` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`interviewId`) REFERENCES `interviews`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_visits_interviewId` ON `visits` (`interviewId`);

CREATE TABLE IF NOT EXISTS `household_answers` (`interviewId` TEXT NOT NULL, `section` TEXT NOT NULL, `field` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`interviewId`, `section`, `field`), FOREIGN KEY(`interviewId`) REFERENCES `interviews`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `member_answers` (`interviewId` TEXT NOT NULL, `householdId` TEXT NOT NULL, `memberId` TEXT NOT NULL, `section` TEXT NOT NULL, `field` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`interviewId`, `memberId`, `section`, `field`), FOREIGN KEY(`interviewId`, `householdId`) REFERENCES `interviews`(`id`, `householdId`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`memberId`, `householdId`) REFERENCES `members`(`id`, `householdId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_member_answers_interviewId_householdId` ON `member_answers` (`interviewId`, `householdId`);

CREATE INDEX IF NOT EXISTS `index_member_answers_memberId_householdId` ON `member_answers` (`memberId`, `householdId`);

CREATE TABLE IF NOT EXISTS `coordinates` (`householdId` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `accuracyMetres` REAL, `capturedAt` INTEGER NOT NULL, PRIMARY KEY(`householdId`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE TABLE IF NOT EXISTS `attachments` (`id` TEXT NOT NULL, `householdId` TEXT NOT NULL, `memberId` TEXT, `kind` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`householdId`) REFERENCES `households`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`memberId`, `householdId`) REFERENCES `members`(`id`, `householdId`) ON UPDATE NO ACTION ON DELETE CASCADE );

CREATE INDEX IF NOT EXISTS `index_attachments_householdId` ON `attachments` (`householdId`);

CREATE INDEX IF NOT EXISTS `index_attachments_memberId_householdId` ON `attachments` (`memberId`, `householdId`);
