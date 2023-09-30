# Base image:
#   https://github.com/sonatype/docker-nexus3
#   https://hub.docker.com/r/sonatype/nexus3
# docker build --progress=plain --no-cache -t <registry>/sonatype/nexus3:3.37.3 .
# docker rmi $(docker images -f "dangling=true" -q)
# docker run --user=0:0 --rm -it -p 8081:8081/tcp sonatype/nexus3:3.37.3 /bin/bash

ARG NEXUS_BASE_IMAGE="sonatype/nexus3:3.58.1"
FROM $NEXUS_BASE_IMAGE
USER root

ARG NEXUS_PLUGIN_VERSION="3.58.1-02"
ENV PLUG_VERSION="${NEXUS_PLUGIN_VERSION}"
ENV NEXUS_PLUGINS="${NEXUS_HOME}/system"

# Override nexus-bootstrap.jar
RUN rm -rf ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/
COPY nexus-bootstrap/target/nexus-bootstrap-*.jar ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/${PLUG_VERSION}/nexus-bootstrap-${PLUG_VERSION}.jar
RUN chmod -R 644 ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-bootstrap/${PLUG_VERSION}/nexus-bootstrap-${PLUG_VERSION}.jar

# Override nexus-repository-services.jar
RUN rm -rf ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/
COPY nexus-repository-services/target/nexus-repository-services-*.jar ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/${PLUG_VERSION}/nexus-repository-services-${PLUG_VERSION}.jar
RUN chmod -R 644 ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/${PLUG_VERSION}/nexus-repository-services-${PLUG_VERSION}.jar

# Add SSO and urlrewrite configs
COPY etc/nexus-default.properties /opt/sonatype/nexus/etc/nexus-default.properties
COPY etc/jetty/nexus-web.xml /opt/sonatype/nexus/etc/jetty/nexus-web.xml
COPY nexus-bootstrap/src/main/config/ /opt/sonatype/nexus/etc/sso/config/
COPY nexus-bootstrap/src/main/groovy/ /opt/sonatype/nexus/etc/sso/script/
COPY nexus-bootstrap/src/main/static/ /opt/sonatype/nexus/etc/sso/static/
RUN chown nexus:nexus -R /opt/sonatype/nexus/etc/sso/

ENV INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx2048m -Djava.util.prefs.userRoot=/nexus-data/javaprefs"

# Setup permissions
RUN chown nexus:nexus -R /opt/sonatype/nexus
USER nexus
