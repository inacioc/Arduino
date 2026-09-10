-- Product names are a natural business key: the catalogue must not contain two
-- products with the same name. Enforced here (not just in application code) so
-- the guarantee holds even under concurrent requests (TOCTOU-safe) and even if
-- a row is ever inserted by something other than ProductPersistenceAdapter.
--
-- ProductPersistenceAdapter.save() relies on this: AbstractPersistenceAdapter
-- classifies the resulting unique-violation as a duplicate and translates it into
-- ProductAlreadyExistsException. See Validation.md for the full
-- infrastructure-exception -> domain-exception translation pattern.
ALTER TABLE products ADD CONSTRAINT uk_products_name UNIQUE (name);
