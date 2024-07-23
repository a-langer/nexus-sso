# Docker Compose configuration

[Docker compose](../compose.yml) configuration may be extended with [compose.override.yml](../_compose.override_prod.yml) (for example, pass additional files to the container).

## OrientDB studio

**OrientDB studio** - web interface to interact with an embedded database, will available at the URL `http://localhost:2480/studio/index.html` if run service with profile "debug" (does not start by default):

  ```bash
  docker compose --profile debug up
  ```
