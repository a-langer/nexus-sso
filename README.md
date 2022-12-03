# Single sign-on patch for Nexus OSS

[![license](https://img.shields.io/badge/license-EPL1-brightgreen.svg)](https://github.com/a-langer/nexus-sso/blob/main/LICENSE "License of source code")
[![image](https://ghcr-badge.herokuapp.com/a-langer/nexus-sso/latest_tag?trim=major&label=latest)][0]
[![image-size](https://ghcr-badge.herokuapp.com/a-langer/nexus-sso/size?tag=3.38.1)][0]
[![JitPack](https://jitpack.io/v/a-langer/nexus-sso.svg)][1]

Patch for [Nexus OSS][2] with authorization via [SSO][9] and [tokens][10]. By default this features available only in PRO version ([see comparison][5]), but this patch provides them an alternative implementation without violating the license.

Available solutions:

* Docker [container][0] (based on [official image][3] with SSO patch applied) and [compose.yml](./compose.yml) config with Nginx.
* Library [nexus-bootstrap.jar][1] (for integration to any [Nexus instance][4]).

## Supported features and examples of usage

List of features this patch adds:

* **SAML/SSO** - authentication via Single Sign-On (SSO) using a SAML identity provider such as [Keycloak][12], [Okta][13], [ADFS][14] and others. Nexus uses access system based on [Apache Shiro][6], this patch extends it with a [Pac4j][8] and [buji-pac4j][7] libraries, which can be configured with "[shiro.ini](./etc/sso/config/shiro.ini)" (see documentation of Apache Shiro and Pac4j for more detail informations). SSO users are created as internal Nexus accounts the first time they sign-in and are updated every next time. Example of usage SSO:
  * Go to menu "Sign in", press to button "Sign in with SSO".
  * You will be redirected to the login page of identity provider.
  * Type you credentials (login, password, 2FA, etc.).
  * You will be redirected to the main page of Nexus, roles and permissions will be mapped with your account as configured.
* **User Auth Tokens** - are applied when security policies do not allow the users password to be used, such as for storing in plain text (in settings Docker, Maven and etc.). Each user can set a personal token that can be used instead of a password. The creation of tokens is implemented through the "NuGet API Key" menu, however, the tokens themselves apply to all types of repositories without exception. Example of usage user token:
  * Go to menu "Nexus -> Manage your user account -> NuGet API Key", press "Access API key".
  * Type your password or username if using SSO login, press "Authenticate".
  * Copy "Your NuGet API Key", press "Close" and "Sign out".
  * Press "Sign in", type your username and token instead of password - done.
  * Also can be use HTTP api:

    ```bash
    # Authorization header for basic:
    Basic <login:token in base64> 
    # Authorization header for bearer:
    Bearer <token>
    ```

* **Docker Repository Reverse Proxy** - this [Nginx configuration](./etc/nginx/docker_location.conf) implements a proxy strategy to use Docker registries without additional ports or hostnames (while the [official documentation][11] only suggests two proxy strategies: "Port Mapping" and "Host Mapping"). To apply the proxy strategy, required pre-configuration of Nexus (see [gistcomment-4188452][18]):
  * After deployment, three Docker registries need to be created:
    * `docker-login` - uses to check authorization, it is recommended to choose type "group" containing registry "proxy" for "hub.docker.com". To allow anonymous access, enable "Allow anonymous docker pull".
    * `docker-group` - choose type "group", uses to look up images in docker registries. CLI searches will be performed on all registries added to this group (assuming the user has read permissions or the "Allow anonymous docker pull" option is enabled).
    * `docker-root` (optional) - is used to pull an image from the Docker registry hosted in the Nexus root, i.e. without a given repository name. Can be of any type, for host your own images required the "hosted" type. Image names in this repository must not contain a slash (for example, myhost/myimage:latest).
  * After authorization, working with docker registries is controlled by Nexus permissions. For example, if you don't give a user permission to write to the "super-secret-docker-hosted-repo" registry, they can log in, but they can't push images to that registry.
  * Example of usage for host "https://nexus_host" and registry "my-hosted-registry":
  
    ```bash
    # Download an image "alpine" from a public registry
    docker pull alpine:latest
    # Change tag of image "alpine"
    docker tag alpine:latest nexus_host/my-hosted-registry/alpine:latest
    # Log in to the local registry
    docker login nexus_host -u $username -p $password_or_token
    # Pushing image "alpine" to registry "my-hosted-registry"
    docker push nexus_host/my-hosted-registry/alpine:latest
    # Search image "alpine" in hosted registry "my-hosted-registry"
    docker search nexus_host/my-hosted-registry/alpine:latest
    # Pulling image "alpine" from hosted registry "my-hosted-registry"
    docker pull nexus_host/my-hosted-registry/alpine:latest
    ```

* **OrientDB studio** - web interface to interact with a embedded database, available at the URL "http://localhost:2480/studio/index.html".

## Additional settings (tips and tricks)

* [Docker compose](./compose.yml) configuration may be extended with [compose.override.yml](./_compose.override.yml) (for example, pass additional files to the container).
* SAML/SSO authentication may be configured with environment variables in [.env](./.env) file, for more flexible settings, can make changes directly to [shiro.ini](./etc/sso/config/shiro.ini) ([variable interpolation][16] supported). However, this also requires that the configuration files of service provider (ex., [sp-metadata.xml](./etc/sso/config/sp-metadata.xml)) and identity provider (ex., [metadata-okta.xml](./etc/sso/config/metadata.xml) or [metadata-keycloak.xml](./etc/sso/config/metadata-keycloak.xml)) will be passed to the container. Examples of creating SAML configurations see in "[Keycloak SAML integration with Nexus application][15]".
* Nginx SSL is pre-configured, to enable it, need rename file [_ssl.conf](./etc/nginx/_ssl.conf) to `ssl.conf` and pass to `${NEXUS_ETC}/nginx/tls/` two files:
  * `site.crt` - PEM certificate of domain name.
  * `site.key` - key for certificate.
* [UrlRewriteFilter][17] is used to route HTTP requests within the application and can be further configured using [urlrewrite.xml](./etc/sso/config/urlrewrite.xml) (for example override or protect API endpoint). Status of UrlRewriteFilter available in http://localhost:8081/rewrite-status.

## Development environment

Need installed Maven and Docker:

```bash
# Build jar bundle:
mvn clean package
# Build docker image:
mvn clean install -PbuildImage
```

[0]: https://github.com/a-langer/nexus-sso/pkgs/container/nexus-sso "Docker image with SSO patch applied"
<!-- [0]: https://github.com/users/a-langer/packages/container/package/nexus-sso "Docker image with SSO patch applied" -->
[1]: https://jitpack.io/#a-langer/nexus-sso "Maven repository for builds from source code"
[2]: https://github.com/sonatype/nexus-public "Source code of Nexus OSS"
[3]: https://github.com/sonatype/docker-nexus3 "Docker image Nexus OSS"
[4]: https://help.sonatype.com/repomanager3/product-information/download "Download Nexus OSS"
[5]: https://www.sonatype.com/products/repository-oss-vs-pro-features "Nexus OSS vs Nexus PRO"
[6]: https://shiro.apache.org/web.html "Shiro security framework"
[7]: https://github.com/bujiio/buji-pac4j "Bridge from Pac4j to Shiro"
[8]: https://www.pac4j.org/docs/clients/saml.html "Pac4j security framework"
[9]: https://help.sonatype.com/repomanager3/nexus-repository-administration/user-authentication/saml "Nexus PRO SAML"
[10]: https://help.sonatype.com/repomanager3/nexus-repository-administration/user-authentication/security-setup-with-user-tokens "Nexus PRO tokens"
[11]: https://help.sonatype.com/repomanager3/nexus-repository-administration/formats/docker-registry/docker-repository-reverse-proxy-strategies "Docker reverse proxy"
[12]: https://www.keycloak.org/docs/latest/server_admin/#assembly-managing-clients_server_administration_guide "Keycloak SAML"
[13]: https://developer.okta.com/docs/concepts/saml/#enabling-saml-for-everyone-vs-a-subset-of-users "Okta SAML"
[14]: https://docs.microsoft.com/en-us/power-apps/maker/portals/configure/configure-saml2-settings "ADFS SAML"
[15]: https://support.sonatype.com/hc/en-us/articles/1500000976522-Keycloak-SAML-integration-with-Nexus-Applications "Keycloak-SAML + Nexus"
[16]: https://commons.apache.org/proper/commons-configuration/userguide/howto_basicfeatures.html "Variable interpolation"
[17]: https://tuckey.org/urlrewrite/manual/4.0/index.html "Url Rewrite Filter"
[18]: https://gist.github.com/abdennour/74c5de79e57a47f3351217d674238da8?permalink_comment_id=4188452#gistcomment-4188452 "Nginx for Docker registry"