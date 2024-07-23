# User Auth Tokens

[User Auth Tokens][0] - are applied when security policies do not allow the users password to be used, such as for storing in plain text (in settings Docker, Maven and etc.) or combined with [SAML/SSO](./SAML.md). Each user can set a personal token that can be used instead of a password. The creation of tokens is implemented through the "NuGet API Key" menu (privilegies `nx-apikey-all` required), however, the tokens themselves apply to all types of repositories. Example of usage user token:

* Enable "**SSO Token Realm**" (above "**Docker Bearer Token Realm**") in the server administration panel.
* Go to menu "Nexus -> Manage your user account -> NuGet API Key", press "Access API key".
* Type your **username** if using SSO login, otherwise type password, then press "Authenticate".
* Copy "Your NuGet API Key", press "Close" and "Sign out".
* To validate a token: press "Sign in", type your username and token instead of password.
* Also, a pair of username+token can be used for authorization in Maven, Docker, Pip, etc., example for HTTP basic authorization - `Authorization: Basic <login:token in base64>`.

## Debug

To enable debugging, add the following lines to the [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini):

```ini
# Disable authentication caching
tokenRealm.authenticationCachingEnabled = false
```

And following lines to the [logback.xml](../etc/logback/logback.xml) file (output will be to `${NEXUS_DATA}/log/nexus.log`):

```xml
<logger name="org.sonatype.nexus.siesta.internal.UnexpectedExceptionMapper" level="TRACE" />
<logger name="com.github.alanger.nexus.plugin.realm.NexusPac4jRealm" level="TRACE" />
<logger name="com.github.alanger.nexus.plugin.realm.NexusTokenRealm" level="TRACE" />
<logger name="com.github.alanger.nexus.plugin.rest.NugetApiKeyResource" level="TRACE" />
```

[0]: https://help.sonatype.com/en/user-tokens.html "Nexus PRO tokens"
