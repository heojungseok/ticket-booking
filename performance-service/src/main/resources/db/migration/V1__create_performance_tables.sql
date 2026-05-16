create table performances (
    id uuid primary key,
    title varchar(255) not null,
    description text,
    venue_name varchar(255) not null,
    starts_at timestamp not null,
    sale_type varchar(30) not null,
    sale_starts_at timestamp,
    sale_ends_at timestamp,
    status varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table seats (
    id uuid primary key,
    performance_id uuid not null references performances(id),
    section varchar(100) not null,
    row_name varchar(100) not null,
    seat_number varchar(50) not null,
    price numeric(12, 2) not null,
    status varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index ux_seats_position
    on seats (performance_id, section, row_name, seat_number);

create table outbox_events (
    id uuid primary key,
    aggregate_type varchar(50) not null,
    aggregate_id uuid not null,
    event_type varchar(80) not null,
    payload jsonb not null,
    status varchar(30) not null,
    retry_count integer not null default 0,
    error_message text,
    created_at timestamp not null,
    published_at timestamp,
    lease_expires_at timestamp
);

create index ix_outbox_events_status_created_at
    on outbox_events (status, created_at);

create table processed_events (
    event_id uuid not null,
    consumer_name varchar(120) not null,
    event_type varchar(80) not null,
    processed_at timestamp not null,
    primary key (event_id, consumer_name)
);
