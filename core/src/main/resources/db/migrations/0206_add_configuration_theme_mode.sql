-- themeMode holds the three-state theme selection; darkTheme stays as the legacy boolean mirror so
-- clients that only understand a flag keep working. Existing rows are copied across once, then
-- themeMode is the column the application reads and writes.
ALTER TABLE Configuration ADD COLUMN themeMode TEXT DEFAULT 'light';
UPDATE Configuration SET themeMode = 'dark' WHERE darkTheme IS NOT NULL AND TRIM(darkTheme) = '1';
UPDATE Configuration SET themeMode = 'light' WHERE darkTheme IS NULL
    OR (TRIM(darkTheme) <> '1' AND TRIM(darkTheme) NOT IN ('light', 'dark', 'system'));
