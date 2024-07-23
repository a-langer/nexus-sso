# Nginx configuration

## Docker Repository Reverse Proxy

**Docker Repository Reverse Proxy** - this [Nginx configuration](../etc/nginx/docker_location.conf) implements a proxy strategy to use Docker registries without additional ports or hostnames (while the [official documentation][1] only suggests two proxy strategies: "Port Mapping" and "Host Mapping"). To apply the proxy strategy, required pre-configuration of Nexus (see [gistcomment-4188452][2]):

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

## Nginx SSL

Nginx SSL is pre-configured, to enable it, need copy file [_ssl.conf](../etc/nginx/_ssl.conf) to `ssl.conf` and pass to directory `${NEXUS_ETC}/nginx/tls/` two files:

* `site.crt` - PEM certificate of domain name.
* `site.key` - key for certificate.

[1]: https://help.sonatype.com/en/docker-repository-reverse-proxy-strategies.html "Docker reverse proxy"
[2]: https://gist.github.com/abdennour/74c5de79e57a47f3351217d674238da8?permalink_comment_id=4188452#gistcomment-4188452 "Nginx for Docker registry"
