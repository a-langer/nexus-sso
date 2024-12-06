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

## DB console

**DB console** - interface to interact with an embedded database. Available in the following modes:

1. H2 TCP server + command line:

    Start service with H2 TCP server:

    ```bash
    export INSTALL4J_ADD_VM_PARAMS="-Dnexus.sso.h2.tcpListenerEnabled=true -Dnexus.sso.h2.tcpListenerPort=2424"
    docker compose up
    ```

    Example SQL executing from command line:

    ```bash
    docker compose exec -- nexus bash

    # Locking admin account
    java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.Shell -url jdbc:h2:tcp://nexus:2424/nexus <<<"update SECURITY_USER set STATUS = 'locked' WHERE ID = 'admin';"

    # Unlocking admin account
    java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.Shell -url jdbc:h2:tcp://nexus:2424/nexus <<<"update SECURITY_USER set STATUS = 'active' WHERE ID = 'admin';"
    ```

2. H2 TCP server + Web console in different container:

   Start service with H2 TCP server and Web console in different container if the "debug" profile is set (does not start by default):

    ```bash
    export INSTALL4J_ADD_VM_PARAMS="-Dnexus.sso.h2.tcpListenerEnabled=true -Dnexus.sso.h2.tcpListenerPort=2424"
    docker compose --profile debug up 
    ```

    Open web browser: `http://localhost:2480` -> JDBC URL: `jdbc:h2:tcp://nexus:2424/nexus` -> User/Password: `empty` -> `Connect`.

3. Or use embedded Web console:

    Expose port `2481` in compose config before running, then:

    ```bash
    export INSTALL4J_ADD_VM_PARAMS="-Dnexus.h2.httpListenerEnabled=true -Dnexus.h2.httpListenerPort=2481"
    docker compose up
    ```

    Open web browser: `http://localhost:2481` -> JDBC URL: `jdbc:h2:/nexus-data/db/nexus` -> User/Password: `empty` -> `Connect`.

## Rebuild DB

If the integrity of the H2 database is compromised, follow the instruction [1] and [2]:

```bash
docker compose down
docker compose run -w /nexus-data/db --rm nexus bash

# Backup nexus.mv.db to nexus.zip (if required)
java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.Script -url jdbc:h2:./nexus -script nexus.zip -options compression zip

# Create a dump nexus.h2.sql of the current database nexus.mv.db
java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.Recover -db nexus -trace

# Rename the corrupt database file to nexus.mv.db.bak
mv nexus.mv.db nexus.mv.db.bak

# Import the dump nexus.h2.sql to new nexus.mv.db
java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.RunScript -url jdbc:h2:./nexus -script nexus.h2.sql -checkResults

# Run and check logs
docker compose up -d
docker compose logs -f

# Restore nexus.mv.db from nexus.zip (if required)
java -cp $NEXUS_HOME/system/com/h2database/h2/*/h2*.jar org.h2.tools.RunScript -url jdbc:h2:./nexus -script nexus.zip -options compression zip
```

[0]: https://docs.docker.com/compose/multiple-compose-files/merge/ "Merge Compose files"
[1]: https://stackoverflow.com/a/41898677 "Rebuild H2DB"
[2]: https://www.h2database.com/html/tutorial.html#upgrade_backup_restore "Upgrade, Backup, and Restore"
