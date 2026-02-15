-- Invoked as psql -f schema-sql10.sql
--
-- Make sure that Postgres is running!
--

\connect sql10;

CREATE TABLE Crop(
  name TEXT PRIMARY KEY,
  family TEXT,
  yield REAL -- pounds per square foot
);
CREATE TABLE Harvest(
  crop TEXT REFERENCES Crop,
  date DATE,
  amount REAL, -- per pound
  PRIMARY KEY(crop, date)
);
CREATE TABLE Cost(
  crop TEXT REFERENCES Crop,
  year INT,
  cost REAL, -- per pound
  PRIMARY KEY(crop, year)
);
CREATE TABLE SalePrice(
  crop TEXT REFERENCES Crop,    
  year INT, 
  salePrice REAL,
  PRIMARY KEY(crop, year)
);

