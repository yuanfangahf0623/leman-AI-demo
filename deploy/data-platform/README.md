# Single-tenant data platform deployment

This directory contains the production layout used on `192.168.19.239`.

- Application: Java 17 systemd service on `127.0.0.1:48080`
- UI: nginx on port 80
- Metadata database: Ubuntu MySQL 8 service on `127.0.0.1:3307`
- Warehouse: existing Doris FE on `127.0.0.1:9030`
- Synchronization: existing SeaTunnel 2.3.13 installation, submitted through a local-mode wrapper
- Tenancy: disabled in the UI; platform tables are single-tenant and contain no tenant column

Secrets are generated on the server and stored below
`/opt/data-platform/shared/config` with mode `0600`. They must never be added to this repository.

Releases live in `/opt/data-platform/releases/<timestamp>`. The
`/opt/data-platform/current` symlink makes application and UI rollback atomic.
