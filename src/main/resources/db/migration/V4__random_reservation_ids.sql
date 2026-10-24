ALTER TABLE reservations ALTER COLUMN id DROP DEFAULT;
ALTER TABLE reservations ALTER COLUMN id TYPE uuid USING gen_random_uuid();
ALTER TABLE reservations ALTER COLUMN id SET DEFAULT gen_random_uuid();
DROP SEQUENCE reservations_id_seq;
