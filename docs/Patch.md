# Patch features configuration

Additional features implemented in this patch.

## Non-transitive privileges in group repositories

**Non-transitive privileges in group repositories** - by default group repository privileges in Nexus are transitive (all or nothing), this [property](../etc/nexus-default.properties) enables mode of non-transitive privileges (only what is allowed):

```properties
nexus.group.nontransitive.privileges.enabled=true
```

> **Note**:
>
> * It is sufficient for a user to have the "browse" or "read" privilege (either one) to read files from the repository.
> * Privileges must be granted to the repository itself and to the group repository in which it is a member.

## Jetty Rewrite Handler

[Jetty Rewrite Handler][1] is used to route HTTP requests within the application and can be further configured using [jetty-sso.xml](../etc/jetty/jetty-sso.xml) (for example override or protect API endpoint). Also it supports hot-reload, to apply the any settings of plugin without restarting the container, run the command:

```bash
docker compose exec -- nexus curl -k http://localhost:8081/rewrite-status
```

> **Note**: Hot-reload not working for environment variables defined in [.env](../.env), this changes take effect only after the container is restarted.

[1]: https://eclipse.dev/jetty/documentation/jetty-9/index.html "Jetty Rewrite Handler"
