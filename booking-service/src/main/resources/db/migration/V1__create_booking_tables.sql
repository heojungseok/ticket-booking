create table booking_seats (
    seat_id uuid primary key,
    performance_id uuid not null,
    section varchar(100) not null,
    row_name varchar(100) not null,
    seat_number varchar(50) not null,
    price numeric(12, 2) not null,
    status varchar(30) not null,
    synced_at timestamp not null
);

create table bookings (
    id uuid primary key,
    user_id uuid not null,
    performance_id uuid not null,
    seat_id uuid not null,
    price numeric(12, 2) not null,
    status varchar(30) not null,
    paid_at timestamp,
    cancelled_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index ux_bookings_active_seat
    on bookings (seat_id)
    where status in ('PENDING_PAYMENT', 'PAID');

create table payments (
    id uuid primary key,
    booking_id uuid not null references bookings(id),
    amount numeric(12, 2) not null,
    status varchar(30) not null,
    provider varchar(50) not null,
    provider_transaction_id varchar(120),
    failure_reason varchar(500),
    requested_at timestamp not null,
    completed_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index ix_payments_booking_id on payments (booking_id);

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
