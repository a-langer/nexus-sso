#!/bin/bash

set -o pipefail;

# Logging
function log() {
    msg="`date '+%Y/%m/%d %H:%M:%S'` $1";
    echo "$msg" >> "${2:-$logFile}" && echo "$1";
}

if [[ ! -f "/nexus-data/db/nexus.mv.db" && ! -z $(find /nexus-data/db -path "/*/database.ocf") ]]; then

  # Docs https://help.sonatype.com/en/orient-3-70-java-8-or-11.html

  # Prepare migrator directory
  bakVer=$(date '+%Y-%m-%d-%H-%M-%S')-${PLUG_VERSION}
  migratorDir="/nexus-data/migrator-${bakVer}"
  mkdir -p "${migratorDir}"
  logFile="${migratorDir}/migrator.log"

  # Check DB files
  if [[ -d "/nexus-data/db/logs" ]]; then
    log "Healthcheck log directory /nexus-data/db/logs already exists, exiting!" $logFile
    exit 1;
  fi
  log "Run database healthcheck before migration, see logs in /nexus-data/db/logs" $logFile
  cd /nexus-data/db && java -jar ${NEXUS_HOME}/nexus-db-migrator-*.jar --healthcheck -y
  log "Database healthcheck completed successfully, see logs in /nexus-data/db/logs" $logFile

  # 1. Perform a full backup using normal backup procedures.
  #    https://orientdb.org/docs/3.1.x/console/Console-Command-Backup.html
  #    https://github.com/sonatype/nexus-public/blob/release-3.70.1-02/components/nexus-orient/src/main/java/org/sonatype/nexus/orient/DatabaseManagerSupport.java
  cd "${migratorDir}"
  log "Perform a full backup of databases: component, config, security" $logFile
  for dbPath in /nexus-data/db/{component,config,security}; do
    dbName=$(basename $dbPath);
    fileName=${dbName}-${bakVer};
    log "Backup db ${dbName} to ${fileName}.bak" $logFile
    java -Xmx512m -jar /opt/sonatype/nexus/lib/support/nexus-orient-console.jar \
      "CONNECT PLOCAL:/nexus-data/db/${dbName} admin admin; backup database ./${fileName}.bak -compressionLevel=3 -bufferSize=16384;" > ./${fileName}.log
    if [ $? -ne 0 ]; then
        log "Error ${$?}" $logFile; exit $?;
    fi
  done

  # 2. Copy the backup to a clean working location on a different filesystem so that any extraction doesn’t impact the existing production system.
  # 3. Shut down Nexus Repository.

  # 4. Run the following command from the clean working location containing your database backup.
  log "Perform migration, see logs in ${migratorDir}/logs" $logFile
  java -jar ${NEXUS_HOME}/nexus-db-migrator-*.jar --migration_type=h2 -y
  log "Migration completed successfully, see logs in ${migratorDir}/logs" $logFile

  # 5. Copy the resultant nexus.mv.db file to your $data-dir/db directory.
  log "Backup OrientDB dirctory to /nexus-data/db_${bakVer}" $logFile
  mv -f /nexus-data/db /nexus-data/db_${bakVer} && mkdir -p /nexus-data/db
  log "Copy the resultant nexus.mv.db file to /nexus-data/db" $logFile
  cp ./nexus.mv.db /nexus-data/db

  # 6. Edit the $data-dir/etc/nexus.properties file and add the following line:
  # nexus.datastore.enabled=true
  export INSTALL4J_ADD_VM_PARAMS="${INSTALL4J_ADD_VM_PARAMS} -Dnexus.datastore.enabled=true"
fi

# 7. Start Nexus Repository.
cd /opt/sonatype/nexus
exec ./bin/nexus run
