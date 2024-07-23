# Single Sign-On patch for Nexus OSS

[![license](https://img.shields.io/badge/license-EPL1-brightgreen.svg)](https://github.com/a-langer/nexus-sso/blob/main/LICENSE "License of source code")
[![image](https://ghcr-badge.deta.dev/a-langer/nexus-sso/latest_tag?trim=major&label=latest)][0]
[![image-size](https://ghcr-badge.deta.dev/a-langer/nexus-sso/size?tag=3.70.0-java11-ubi)][0]
[![JitPack](https://jitpack.io/v/a-langer/nexus-sso.svg)][1]

Patch for [Nexus OSS][2] with authorization via [SSO][7] and [tokens][8]. By default this features available only in PRO version ([see comparison][5]), but this patch provides them an alternative implementation without violating the license.

Solution implement as Docker [container][0] (based on [official image][3] with SSO patch applied) and [compose.yml](./compose.yml) config with Nginx. Example of usage:

  ```bash
  # Clone configuration and change to working directory
  git clone https://github.com/a-langer/nexus-sso.git
  cd ./nexus-sso
  # Copy compose.override.yml from template for you settings
  cp _compose.override.yml compose.override.yml
  # Set environment variables for container user
  export NEXUS_USER=$(id -u) NEXUS_GROUP=$(id -g)
  # Run service and open http://localhost in web browser
  docker compose up -d
  ```

## Supported features and examples of usage

> **Note**: Since version `3.70.0-java11-ubi` image and all libraries have been updated to Java 11. See [release notes][9.1] for more information.

Since version `3.61.0` for using SSO and User Tokens, it is enough to have following [realms][6] in the order listed:

1. "**Local Authenticating Realm**" - built-in realm used by default.
2. "**SSO Pac4j Realm**" - single sign-on realm uses an external Identity Provider (IdP).
3. "**SSO Token Realm**" - realm allows you to use user tokens instead of a password.
4. "**Docker Bearer Token Realm**" - required to access Docker repositories through a Docker client (must be below the "**SSO Token Realm**").

Other realms are not required and may lead to conflicts.

List of features this patch adds:

* [**SAML/SSO**](./docs/SAML.md) - authentication via Single Sign-On (SSO) using a SAML identity provider such as Keycloak, Okta, ADFS and others.

* [**User Auth Tokens**](./docs/Tokens.md) - are applied when security policies do not allow the users password to be used, such as for storing in plain text (in settings Docker, Maven and etc.) or combined with [SAML/SSO](./docs/SAML.md).

* [**Nginx Reverse Proxy**](./docs/Nginx.md) - this Nginx configuration implements a proxy strategy to use Docker registries without additional ports or hostnames. Also provides pre-configured SSL.

* [**Docker Compose**](./docs/Docker.md) - provide flexible Compose configuration and **OrientDB studio** - web interface to interact with an embedded database.

* [**Patch features**](./docs/Patch.md) - additional features implemented in this patch.

## Development environment

Need installed Maven and Docker with [Compose][4] and [BuildKit][4.1] plugins:

1. Change Nexus version if update required (see [Release Notes][9] and [Maven Central][10] for more information), ex.:

    ```bash
    # Set version of the current project and any child modules
    mvn versions:set -DnewVersion=3.46.0
    # Optional can set revision number of the Nexus plugins
    mvn versions:set-property -Dproperty=nexus.extension.version -DnewVersion=02
    ```

2. Execute assembly commands:

    ```bash
    # Build docker image
    mvn clean install -PbuildImage
    # Or build only jar bundle if needed
    mvn clean package
    ```

3. Run docker container and test it:

    ```bash
    # Run service and open http://localhost in web browser
    docker compose down && docker compose up
    ```

4. Accept or revert modifications to the pom.xml files:

    ```bash
    # Accept modifications
    mvn versions:commit
    # Or revert modifications and rebuild docker image
    mvn versions:revert && mvn clean install -PbuildImage
    ```

[0]: https://github.com/a-langer/nexus-sso/pkgs/container/nexus-sso "Docker image with SSO patch applied"
[1]: https://jitpack.io/#a-langer/nexus-sso "Maven repository for builds from source code"
[2]: https://github.com/sonatype/nexus-public "Source code of Nexus OSS"
[3]: https://github.com/sonatype/docker-nexus3 "Docker image Nexus OSS"
[4]: https://docs.docker.com/compose/install/ "Docker plugin for defining and running multi-container Docker applications"
[4.1]: https://github.com/docker/buildx "Docker plugin for capabilities with BuildKit"
[5]: https://www.sonatype.com/products/repository-oss-vs-pro-features "Nexus OSS vs Nexus PRO"
[6]: https://help.sonatype.com/en/realms.html "Nexus Realms"
[7]: https://help.sonatype.com/en/saml.html "Nexus PRO SAML"
[8]: https://help.sonatype.com/en/user-tokens.html "Nexus PRO tokens"
[9]: https://github.com/sonatype/nexus-public/releases "Nexus release notes"
[9.1]: https://help.sonatype.com/en/sonatype-nexus-repository-3-70-0-release-notes.html "Nexus Repository 3.70.0 - 3.70.1 Release Notes"
[10]: https://mvnrepository.com/artifact/org.sonatype.nexus/nexus-bootstrap "Version of Nexus plugins in Maven Central"
