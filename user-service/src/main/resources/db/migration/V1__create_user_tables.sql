create table users (
    id uuid primary key,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    name varchar(100) not null,
    role varchar(30) not null,
    status varchar(30) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index ux_users_email on users (email);
