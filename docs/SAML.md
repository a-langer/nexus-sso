# SAML configuration

## Configure service and identity providers

Description of configuration:

- [sp-metadata.xml](../etc/sso/config/sp-metadata.xml) - this is the configuration of service provider (hereinafter **SP**), made specifically for the Nexus application. The only thing that may be required is to correct "**entityID**" and "**Location**" depending on the DNS name you use, ex.: `http(s)://myDomainName/callback?client_name=SAML2Client`. Endpoint will always be "**/callback?client_name=SAML2Client**".
- The value of the attribute "**entityID**" in sp-metadata.xml should be the same as the attribute "**serviceProviderEntityId**" and "**callbackUrl**" in [shiro.ini](../etc/sso/config/shiro.ini) (also depending on the DNS name you use), ex:

    ```ini
    # Same as the attribute entityID
    saml2Config.serviceProviderEntityId = http://localhost/callback?client_name=SAML2Client
    # Same as the attribute entityID without URI parameter
    clients.callbackUrl = http://localhost/callback
    ```

    > **_NOTE:_** ADFS does not support URI parameter in entityID, remove "_?client_name=SAML2Client_" from **Client ID** in the ADFS settings, from **serviceProviderEntityId** in shiro.ini and from **entityID** in sp-metadata.xml.

- [metadata.xml](../etc/sso/config/metadata.xml) - this is the configuration of the identity provider (hereinafter **IdP**), it needs to be downloaded from Okta/Keycloak/ADFS/Etc and passed into the Nexus container. Additionally, you must specify the "**SP Entity ID**/**Client ID**" and "**Single sign-on URL**/**Client SAML endpoint**" attributes in the IdP settings, whose value must match the "**entityID**" from sp-metadata.xml.
- By default, "Nexus SSO" is already pre-configured for authorization through [Okta](https://www.okta.com/) with HTTP protocol on localhost (the endpoint `http://localhost/callback?client_name=SAML2Client`, see [IdP settings](https://user-images.githubusercontent.com/15138089/230576296-f064501e-7c0d-4838-9ace-e522d9c8f100.png)). To configure authorization through another IdP-server is required:
    1. Configure new SAML client in the IdP server with DNS name for your Nexus instance.
    2. Download **metadata.xml** from IdP server and pass his to the Nexus container.
    3. Replace the protocol and DNS name in **sp-metadata.xml** and **shiro.ini** (as shown in the first paragraph).

## Attributes mapping

The names of user attributes depend on the structure of the profile in IdP. User attribute mapping can be configured in [shiro.ini](../etc/sso/config/shiro.ini):

```properties
# User roles attribute (list of roles)
authorizationGenerator.roleAttributes = roles
# User principal name attribute (user identifier)
pac4jRealm.principalNameAttribute = id

# User profile attributes mapping in one line
pac4jAuthenticationListener.map(attrs) = firstName:myIdpFirstName, lastName:myIdpLastName, email:myIdpEmailaddress
# User profile attributes mapping by separately
pac4jAuthenticationListener.attrs[id] = myIdpUPN
pac4jAuthenticationListener.attrs[firstName] = myIdpFirstName
pac4jAuthenticationListener.attrs[lastName] = myIdpLastName
pac4jAuthenticationListener.attrs[email] = myIdpEmailaddress
```

> **_NOTE:_** If variable interpolation is used, such as `${PAC4J_PRINCIPAL_NAME_ATTR:-username}`, then the value of the variables must be changed in the [.env](../.env) file. To quickly apply changes, can use hot-reload, see [Additional settings (tips and tricks)](../README.md#additional-settings-tips-and-tricks).

## Group and role mapping

In UX will need to create a roles with type "**Nexus role**" instead of external mapping. Roles from Nexus and groups from IdP will be mapped together by name (ex.: role "samltestgroup" in Nexus = group "samltestgroup" in IdP).

## Debug

To enable debugging, add the following lines to the [logback.xml](../etc/logback/logback.xml) file (output will be to `${NEXUS_DATA}/log/nexus.log`):

```xml
<logger name="com.github.alanger.nexus.bootstrap.Pac4jAuthenticationListener" level="TRACE" />
<logger name="org.pac4j.saml.client" level="TRACE" />
<logger name="org.opensaml.saml.metadata.resolver" level="TRACE" />
```
