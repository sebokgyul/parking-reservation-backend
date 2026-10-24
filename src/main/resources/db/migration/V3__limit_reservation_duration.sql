ALTER TABLE reservations
    ADD CONSTRAINT reservations_max_duration CHECK (end_time - start_time <= interval '24 hours');
