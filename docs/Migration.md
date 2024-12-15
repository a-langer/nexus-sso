# Migration

Since version [`3.70.1-java11-ubi`][0] your need migrate from legacy [OrientDB][1] to [H2DB][2]. Don't worry, this version make migration automatically, just update the image version and run the container (see [migrator.sh](../nexus-docker/migrator.sh) for more information).

> **WARN**: Versions [`3.71.0`](https://help.sonatype.com/en/download.html#download-sonatype-nexus-repository-database-migrator) and above of the Database Migrator utility only support migrating between `H2` and `PostgreSQL`.

Of course, you can perform the migration yourself following the instructions below:

1. [Sonatype Nexus Repository 3.70.0 was the final release to include our legacy OrientDB](https://help.sonatype.com/en/upgrading-to-nexus-repository-3-71-0-and-beyond.html).
2. [3.71.0 and beyond do not support OrientDB, Java 8, or Java 11](https://help.sonatype.com/en/sonatype-nexus-repository-3-71-0-release-notes.html).
3. [Migrating From OrientDB to H2](https://help.sonatype.com/en/orient-3-70-java-8-or-11.html).
4. [Database Migrator Utility for 3.70.x](https://help.sonatype.com/en/orientdb-downloads.html).

[0]: https://help.sonatype.com/en/sonatype-nexus-repository-3-70-0-release-notes.html "Nexus Repository 3.70.0 - 3.70.1 Release Notes"
[1]: http://orientdb.org/docs/2.2.x/ "OrientDB"
[2]: https://www.h2database.com/html/main.html "H2 Database Engine"
