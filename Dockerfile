# Base image:
#   https://github.com/sonatype/docker-nexus3
#   https://hub.docker.com/r/sonatype/nexus3
# docker build --progress=plain --no-cache -t <registry>/sonatype/nexus3:3.37.3 .
# docker rmi $(docker images -f "dangling=true" -q)
# docker run --user=0:0 --rm -it -p 8081:8081/tcp sonatype/nexus3:3.37.3 /bin/bash

ARG NEXUS_BASE_IMAGE="sonatype/nexus3:3.70.0-java11-ubi"
FROM $NEXUS_BASE_IMAGE
USER root

ARG NEXUS_PLUGIN_VERSION="3.70.0-03"
ENV PLUG_VERSION="${NEXUS_PLUGIN_VERSION}"
ENV NEXUS_PLUGINS="${NEXUS_HOME}/system"

ARG ANSIBLEGALAXY_VERSION="0.3.2-fixed"

# Add nexus-pac4j-plugin.jar
RUN rm -rf ${NEXUS_PLUGINS}/com/github/alanger/nexus/plugin/nexus-pac4j-plugin/
COPY nexus-pac4j-plugin/target/nexus-pac4j-plugin-*.jar ${NEXUS_PLUGINS}/com/github/alanger/nexus/plugin/nexus-pac4j-plugin/${PLUG_VERSION}/nexus-pac4j-plugin-${PLUG_VERSION}.jar
RUN chmod -R 644 ${NEXUS_PLUGINS}/com/github/alanger/nexus/plugin/nexus-pac4j-plugin/${PLUG_VERSION}/nexus-pac4j-plugin-${PLUG_VERSION}.jar
RUN echo "reference\:file\:com/github/alanger/nexus/plugin/nexus-pac4j-plugin/${PLUG_VERSION}/nexus-pac4j-plugin-${PLUG_VERSION}.jar = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties

# Override nexus-repository-services.jar
RUN rm -rf ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/
COPY nexus-repository-services/target/nexus-repository-services-*.jar ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/${PLUG_VERSION}/nexus-repository-services-${PLUG_VERSION}.jar
RUN chmod -R 644 ${NEXUS_PLUGINS}/org/sonatype/nexus/nexus-repository-services/${PLUG_VERSION}/nexus-repository-services-${PLUG_VERSION}.jar

# Add SSO and urlrewrite configs
COPY etc/nexus-default.properties /opt/sonatype/nexus/etc/nexus-default.properties
COPY etc/jetty/nexus-web.xml /opt/sonatype/nexus/etc/jetty/nexus-web.xml
COPY etc/jetty/jetty-sso.xml /opt/sonatype/nexus/etc/jetty/jetty-sso.xml
COPY nexus-pac4j-plugin/src/main/config/ /opt/sonatype/nexus/etc/sso/config/
COPY nexus-pac4j-plugin/src/main/groovy/ /opt/sonatype/nexus/etc/sso/script/
RUN chown nexus:nexus -R /opt/sonatype/nexus/etc/sso/

# Add nexus-repository-ansiblegalaxy.jar
RUN rm -rf ${NEXUS_PLUGINS}/org/sonatype/nexus/plugins/nexus-repository-ansiblegalaxy/
COPY nexus-docker/target/nexus-repository-ansiblegalaxy-*.jar ${NEXUS_PLUGINS}/org/sonatype/nexus/plugins/nexus-repository-ansiblegalaxy/${ANSIBLEGALAXY_VERSION}/nexus-repository-ansiblegalaxy-${ANSIBLEGALAXY_VERSION}.jar
RUN chmod -R 644 ${NEXUS_PLUGINS}/org/sonatype/nexus/plugins/nexus-repository-ansiblegalaxy/${ANSIBLEGALAXY_VERSION}/nexus-repository-ansiblegalaxy-${ANSIBLEGALAXY_VERSION}.jar
RUN echo "reference\:file\:org/sonatype/nexus/plugins/nexus-repository-ansiblegalaxy/${ANSIBLEGALAXY_VERSION}/nexus-repository-ansiblegalaxy-${ANSIBLEGALAXY_VERSION}.jar = 200" >> /opt/sonatype/nexus/etc/karaf/startup.properties

ENV INSTALL4J_ADD_VM_PARAMS="-Xms512m -Xmx2048m -Djava.util.prefs.userRoot=/nexus-data/javaprefs"

# Setup permissions
RUN chown nexus:nexus -R /opt/sonatype/nexus
USER nexus
