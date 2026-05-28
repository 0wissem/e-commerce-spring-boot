CREATE TABLE outbox_events (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    source      VARCHAR(50)  NOT NULL,
    payload     TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status);