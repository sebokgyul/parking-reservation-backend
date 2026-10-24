UPDATE reservations SET end_time = start_time + interval '24 hours'
WHERE end_time - start_time > interval '24 hours';

ALTER TABLE reservations
    ADD CONSTRAINT reservations_max_duration CHECK (end_time - start_time <= interval '24 hours');
