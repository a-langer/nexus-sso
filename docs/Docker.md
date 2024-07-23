# Docker Compose configuration

[Docker compose](../compose.yml) configuration may be extended with [compose.override.yml][0] (for example, pass additional files to the container). Example settings for an production environment:

1. Set variables for your production environment, ex.:

    ```bash
    export NEXUS_USER=$(id -u) NEXUS_GROUP=$(id -g)
    export NEXUS_ETC="./etc_prod" NEXUS_DATA="./data_prod"
    ```

2. Prepare Docker production configuration, ex.:

    ```bash
    cp ./_compose.override_prod.yml ./compose.override.yml
    cp ./.env ./.env_prod
    sed -i "s/NEXUS_USER=.*/NEXUS_USER=\"${NEXUS_USER}\"/" ./.env_prod
    sed -i "s/NEXUS_GROUP=.*/NEXUS_GROUP=\"${NEXUS_GROUP}\"/" ./.env_prod
    sed -i "s|NEXUS_ETC=.*|NEXUS_ETC=\"${NEXUS_ETC}\"|" ./.env_prod
    sed -i "s|NEXUS_DATA=.*|NEXUS_DATA=\"${NEXUS_DATA}\"|" ./.env_prod
    ```

3. Prepare Nexus directories and configuration templates, ex.:

    ```bash
    mkdir -p ${NEXUS_ETC}/sso/config/ ${NEXUS_DATA}
    cp -rf ./etc/* ${NEXUS_ETC}/
    cp -rf ./nexus-pac4j-plugin/src/main/config/* ${NEXUS_ETC}/sso/config/
    ```

4. Modify shiro.ini, metadata.xml and sp-metadata.xml, see [docs/SAML.md](./SAML.md).
5. Enable Nginx SSL configuration, ex.:

    ```bash
    cp ${NEXUS_ETC}/nginx/_ssl.conf ${NEXUS_ETC}/nginx/ssl.conf
    openssl req -x509 -nodes -sha256 -days 3650 -newkey rsa:2048 -keyout ${NEXUS_ETC}/nginx/tls/site.key -out ${NEXUS_ETC}/nginx/tls/site.crt
    ```

6. Change others settings for your production environment, see examples in [_compose.override_prod.yml](../_compose.override_prod.yml) and [_compose.override.yml](../_compose.override.yml).

## OrientDB studio

**OrientDB studio** - web interface to interact with an embedded database, will available at the URL `http://localhost:2480/studio/index.html` if run service with profile "debug" (does not start by default):

```bash
docker compose --profile debug up
```

## Rebuild OrientDB

If the integrity of the database is compromised, follow the [instruction][1]:

```bash
docker compose run --rm -u 0:0 nexus bash
cd /tmp
java -Xmx128m -jar /opt/sonatype/nexus/lib/support/nexus-orient-console.jar

CONNECT PLOCAL:/nexus-data/db/component admin admin
REBUILD INDEX *
REPAIR DATABASE --fix-graph
REPAIR DATABASE --fix-links
REPAIR DATABASE --fix-ridbags
REPAIR DATABASE --fix-bonsai
DISCONNECT

docker compose restart nexus
docker compose logs -f

# Backup (if required)
backup database /nexus-data/db-component-backup.zip

# Restore (if required)
restore database /nexus-data/db-component-backup.zip
```

[0]: https://docs.docker.com/compose/multiple-compose-files/merge/ "Merge Compose files"
[1]: https://gist.github.com/micalbrecht/212d37865fbc24afc6d8b1aab621dea6 "Rebuild OrientDB gist"
