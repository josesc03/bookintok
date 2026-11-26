-- Book photos are now uploaded to the API instead of linking to an external host
ALTER TABLE books DROP COLUMN image_url;
ALTER TABLE books ADD COLUMN image_key VARCHAR(100);

-- Locations are stored approximately (about 1 km), never the exact point
ALTER TABLE users ADD COLUMN location_label VARCHAR(100);
UPDATE users
SET latitude = round(latitude::numeric, 2), longitude = round(longitude::numeric, 2)
WHERE latitude IS NOT NULL;
