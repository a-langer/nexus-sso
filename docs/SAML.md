# SAML configuration

[SAML/SSO][0] - authentication via Single Sign-On (SSO) using a SAML identity provider such as [Keycloak][4], [Okta][5], [ADFS][6] and others. Nexus uses access system based on [Apache Shiro][1], this patch extends it with a [Pac4j][3] and [buji-pac4j][2] libraries, which can be configured with [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini) (see documentation of Apache Shiro and Pac4j for more detail informations). SSO users are created as internal Nexus accounts the first time they sign-in and are updated every next time. Example of usage SSO:

* Enable "**SSO Pac4j Realm**" in the server administration panel and sign out.
* Reload page, go to menu "Sign in", press to button "Sign in with SSO".
* You will be redirected to the login page of identity provider.
* Type you credentials (login, password, 2FA, etc.).
* You will be redirected to the main page of Nexus, roles and permissions will be mapped with your account as configured.

SAML/SSO authentication may be configured with environment variables in [.env](../.env) file, for more flexible settings, can make changes directly to [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini) ([variable interpolation][8] supported). However, this also requires that the configuration files of service provider (ex., [sp-metadata.xml](../nexus-pac4j-plugin/src/main/config/sp-metadata.xml)) and identity provider (ex., [metadata-okta.xml](../nexus-pac4j-plugin/src/main/config/metadata.xml) or [metadata-keycloak.xml](../nexus-pac4j-plugin/src/main/config/metadata-keycloak.xml)) will be passed to the container. Examples of creating SAML configurations see in "[Keycloak SAML integration with Nexus application][7]" (except "Configure Sonatype Platform", instead follow this instruction).

## Configure service and identity providers

Description of configuration:

* [sp-metadata.xml](../nexus-pac4j-plugin/src/main/config/sp-metadata.xml) - this is the configuration of service provider (hereinafter **SP**), made specifically for the Nexus application. The only thing that may be required is to correct "**entityID**" and "**Location**" depending on the DNS name and protocol you use, endpoint will always be "**/callback?client_name=SAML2Client**". Example for DNS `myNexusDomain`:

    ```xml
    <md:EntityDescriptor ... entityID="http(s)://myNexusDomain/callback?client_name=SAML2Client" validUntil="2042-03-17T05:02:50.999Z">
            ...
        <md:SPSSODescriptor ...>
            <md:Extensions xmlns:init="urn:oasis:names:tc:SAML:profiles:SSO:request-init">
                <init:RequestInitiator ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client"/>
            </md:Extensions>
            ...
            <md:SingleLogoutService ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client&amp;logoutendpoint=true"/>
            <md:SingleLogoutService ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client&amp;logoutendpoint=true"/>
            <md:SingleLogoutService ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client&amp;logoutendpoint=true"/>
            <md:SingleLogoutService ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client&amp;logoutendpoint=true"/>
            ...
            <md:AssertionConsumerService ... Location="http(s)://myNexusDomain/callback?client_name=SAML2Client" index="0"/>
        </md:SPSSODescriptor>
    </md:EntityDescriptor>
    ```

* The value of the attribute "**entityID**" in sp-metadata.xml should be the same as the attribute "**serviceProviderEntityId**" and "**callbackUrl**" in [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini) (also depending on the DNS name you use), ex:

    ```ini
    # Same as the attribute entityID
    saml2Config.serviceProviderEntityId = http(s)://myNexusDomain/callback?client_name=SAML2Client
    # Same as the attribute entityID without URI parameter
    clients.callbackUrl = http(s)://myNexusDomain/callback
    ```

    > **_NOTE:_** ADFS does not support URI parameter in entityID, remove "_?client_name=SAML2Client_" from **Client ID** in the ADFS settings, from **serviceProviderEntityId** in shiro.ini and from **entityID** in sp-metadata.xml.

* [metadata.xml](../nexus-pac4j-plugin/src/main/config/metadata.xml) - this is the configuration of the identity provider (hereinafter **IdP**), it needs to be downloaded from Okta/Keycloak/ADFS/Etc and passed into the Nexus container. Additionally, you must specify the "**SP Entity ID**/**Client ID**" and "**Single sign-on URL**/**Client SAML endpoint**" attributes in the IdP settings, whose value must match the "**entityID**" from sp-metadata.xml.

    > **_NOTE:_** Attribute `WantAuthnRequestsSigned` should be `false` by default in the **metadata.xml** file. If you want authentication requests to be signed, you will need to perform more complex settings that are beyond the scope of this instruction.

* By default, "Nexus SSO" is already pre-configured for authorization through [Okta](https://www.okta.com/) with HTTP protocol on localhost (the endpoint `http://localhost/callback?client_name=SAML2Client`, see [Okta settings](./Okta-Nexus-SAML.png)). To configure authorization through another IdP-server is required:
    1. Replace the protocol and DNS name in **sp-metadata.xml** and **shiro.ini** (and **.env** if variable interpolation is used) as show above.
    2. Configure new SAML client in the IdP server with DNS name and Client ID for your Nexus instance and download **metadata.xml**.
    3. Pass **metadata.xml**, **sp-metadata.xml**, **shiro.ini** and custom **.env** to the Nexus container, see [_compose.override_prod.yml](../_compose.override_prod.yml) for an example.

## Attributes mapping

The names of user attributes depend on the structure of the profile in IdP. User attribute mapping can be configured in [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini):

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

> **_NOTE:_** If variable interpolation is used, such as `${PAC4J_PRINCIPAL_NAME_ATTR:-username}`, then the value of the variables must be changed in the [.env](../.env) file. To quickly apply changes, can use hot-reload, see [Jetty Rewrite Handler](./Patch.md#jetty-rewrite-handler).

## Group and role mapping

Roles from Nexus and groups from IdP are mapped to each other by name, for example the role "samltestgroup" in Nexus will be equal to the group "samltestgroup" in IdP. There is no specific way to map specific groups to roles at this time. For mapping, you need to create Nexus roles with the same names as the groups in IdP. In administrative UX will need to create a roles with type "**Nexus role**" instead of external mapping. After logging in, only those groups that correspond to the roles in Nexus with the same names will be applied to the user, other groups will be ignored.

## Debug

To enable debugging, add the following lines to the [shiro.ini](../nexus-pac4j-plugin/src/main/config/shiro.ini):

```ini
# Force re-authenticate, see https://github.com/a-langer/nexus-sso/issues/11
saml2Config.forceAuth = true
# Disable all validation, good for testing
saml2Config.allSignatureValidationDisabled = true
# Debug callback
callbackLogic = com.github.alanger.nexus.plugin.Pac4jCallbackLogic
callbackFilter.callbackLogic = $callbackLogic
```

And following lines to the [logback.xml](../etc/logback/logback.xml) file (output will be to `${NEXUS_DATA}/log/nexus.log`):

```xml
<logger name="com.github.alanger.nexus.bootstrap.Pac4jAuthenticationListener" level="TRACE" />
<logger name="com.github.alanger.nexus.plugin.Pac4jCallbackLogic" level="TRACE" />
<logger name="org.pac4j.jee.filter.SecurityFilter" level="TRACE" />
<logger name="org.pac4j.jee.filter.CallbackFilter" level="TRACE" />
<logger name="org.pac4j.saml.client" level="TRACE" />
<logger name="org.opensaml.saml.metadata.resolver" level="TRACE" />
```

It is better to perform each check in a new private browser window (or delete cookies for Nexus and IdP sites, which is quite difficult), otherwise the browser may remember invalid cookies and will not go to the login page, which in turn confuses and complicates diagnostics.

[0]: https://help.sonatype.com/en/saml.html "Nexus PRO SAML"
[1]: https://shiro.apache.org/web.html "Shiro security framework"
[2]: https://github.com/bujiio/buji-pac4j "Bridge from Pac4j to Shiro"
[3]: https://www.pac4j.org/docs/clients/saml.html "Pac4j security framework"
[4]: https://www.keycloak.org/docs/latest/server_admin/#assembly-managing-clients_server_administration_guide "Keycloak SAML"
[5]: https://developer.okta.com/docs/concepts/saml/#enabling-saml-for-everyone-vs-a-subset-of-users "Okta SAML"
[6]: https://docs.microsoft.com/en-us/power-apps/maker/portals/configure/configure-saml2-settings "ADFS SAML"
[7]: https://support.sonatype.com/hc/en-us/articles/1500000976522-Keycloak-SAML-integration-with-Nexus-Applications "Keycloak-SAML + Nexus"
[8]: https://commons.apache.org/proper/commons-configuration/userguide/howto_basicfeatures.html "Variable interpolation"
