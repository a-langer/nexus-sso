# Base image:
#   https://github.com/sonatype/docker-nexus3
#   https://hub.docker.com/r/sonatype/nexus3
# docker build --progress=plain --no-cache -t <registry>/sonatype/nexus3:3.37.3 .
# docker rmi $(docker images -f "dangling=true" -q)
# docker run --user=0:0 --rm -it -p 8081:8081/tcp sonatype/nexus3:3.37.3 /bin/bash

ARG NEXUS_BASE_IMAGE="sonatype/nexus3:3.42.0"
FROM $NEXUS_BASE_IMAGE
USER root

ARG BOOTSTRAP_VERSION="3.42.0-01"
ENV BOOT_VERSION="${BOOTSTRAP_VERSION}"
ENV BOOT_PLUGIN="nexus-bootstrap-${BOOT_VERSION}.jar"
ENV NEXUS_PLUGINS="${NEXUS_HOME}/system"

# Override nexus-bootstrap.jar
RUN rm -rf ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/
COPY target/nexus-sso*.jar ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/${BOOT_VERSION}/${BOOT_PLUGIN}
RUN chmod -R 644 ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/${BOOT_VERSION}/${BOOT_PLUGIN}

# Add SSO and urlrewrite configs
COPY etc/nexus-default.properties /opt/sonatype/nexus/etc/nexus-default.properties
COPY etc/jetty/nexus-web.xml /opt/sonatype/nexus/etc/jetty/nexus-web.xml
COPY etc/sso/ /opt/sonatype/nexus/etc/sso/
RUN chown nexus:nexus -R /opt/sonatype/nexus/etc/sso/

# Add OrientDB studio, need -Dorientdb.www.path=/opt/sonatype/nexus/etc/orient
COPY target/studio/www/* /opt/sonatype/nexus/etc/orient/studio/
RUN chown nexus:nexus -R /opt/sonatype/nexus/etc/orient/studio/
ENV INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx2048m -Djava.util.prefs.userRoot=/nexus-data/javaprefs -Dorientdb.www.path=/opt/sonatype/nexus/etc/orient"

# Setup permissions
RUN chown nexus:nexus -R /opt/sonatype/nexus
USER nexus
