-- Phase 1 indexes.
--
-- WHY A SECOND SPATIAL INDEX:
--
-- V1 created a GiST index on `location`, which is GEOMETRY(Point, 4326).
-- A geometry index answers geometry operators. But distance in SRID 4326 is
-- measured in DEGREES, not metres, so a radius search written against geometry
-- would interpret "5000" as 5000 degrees and silently return the entire table.
--
-- The query therefore casts to `geography`, where distances are real metres.
-- That cast makes the geometry index unusable: PostgreSQL will not use an index
-- on `location` to answer a predicate on `location::geography`. Without a
-- matching functional index the search falls back to a sequential scan, which
-- looks fine on seed data and collapses on a real dataset.
--
-- The cast from geometry to geography is IMMUTABLE, so it can be indexed.
CREATE INDEX idx_gas_stations_location_geog
    ON gas_stations USING GIST ((location::geography));

-- The real access pattern is "latest price for this station and fuel type".
-- V1's single-column index on station_id cannot serve the ordering, so the
-- database sorts every matching row. This composite index covers filter and
-- ordering together.
CREATE INDEX idx_fuel_prices_lookup
    ON fuel_prices (station_id, fuel_type, reported_at DESC);
