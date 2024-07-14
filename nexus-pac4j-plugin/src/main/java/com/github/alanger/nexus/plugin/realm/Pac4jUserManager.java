package com.github.alanger.nexus.plugin.realm;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Description;
import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.security.config.CUser;
import org.sonatype.nexus.security.config.SecurityConfigurationManager;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.AbstractUserManager;
import org.sonatype.nexus.security.user.RoleMappingUserManager;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserCreatedEvent;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserSearchCriteria;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * User manager for SSO realm.
 * 
 * @see org.sonatype.nexus.security.internal.UserManagerImpl
 */
@Singleton
@Named(Pac4jUserManager.SOURCE)
@Description("Pac4j")
public class Pac4jUserManager extends AbstractUserManager implements RoleMappingUserManager {

    public static final String SOURCE = "pac4j";

    private final EventManager eventManager;

    private final SecurityConfigurationManager configuration;

    private final RoleMappingUserManager defaultUserManager;

    @Inject
    public Pac4jUserManager(final EventManager eventManager, final SecurityConfigurationManager configuration,
            @Named("default") final RoleMappingUserManager defaultUserManager) {
        this.eventManager = checkNotNull(eventManager);
        this.configuration = configuration;
        this.defaultUserManager = checkNotNull(defaultUserManager);
    }

    //-- Utils --//

    private CUser toUser(User user) {
        if (user == null) {
            return null;
        }

        CUser secUser = configuration.newUser();

        secUser.setId(user.getUserId());
        secUser.setVersion(user.getVersion());
        secUser.setFirstName(user.getFirstName());
        secUser.setLastName(user.getLastName());
        secUser.setEmail(user.getEmailAddress());
        secUser.setStatus(user.getStatus().name());
        // secUser.setPassword( password )// DO NOT set the users password!

        return secUser;
    }

    private Set<String> getRoleIdsFromUser(User user) {
        Set<String> roles = new HashSet<>();
        for (RoleIdentifier roleIdentifier : user.getRoles()) {
            roles.add(roleIdentifier.getRoleId());
        }
        return roles;
    }

    //-- org.sonatype.nexus.security.user.RoleMappingUserManager --//

    @Override
    public Set<RoleIdentifier> getUsersRoles(final String userId, final String source) throws UserNotFoundException {
        return defaultUserManager.getUsersRoles(userId, source);
    }

    @Override
    public void setUsersRoles(String userId, String userSource, Set<RoleIdentifier> roleIdentifiers) throws UserNotFoundException {
        defaultUserManager.setUsersRoles(userId, userSource, roleIdentifiers);
    }

    //-- org.sonatype.nexus.security.user.UserManager --//

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public String getAuthenticationRealmName() {
        return NexusPac4jRealm.NAME;
    }

    @Override
    public boolean supportsWrite() {
        return false;
    }

    @Override
    public Set<User> listUsers() {
        return defaultUserManager.listUsers();
    }

    @Override
    public Set<String> listUserIds() {
        return defaultUserManager.listUserIds();
    }

    @Override
    public User addUser(User user, String password) {
        final CUser secUser = checkNotNull(this.toUser(user));
        secUser.setPassword("[" + NexusPac4jRealm.NAME + "]");
        configuration.createUser(secUser, getRoleIdsFromUser(user));
        eventManager.post(new UserCreatedEvent(user));
        return user;
    }

    @Override
    public User updateUser(User user) throws UserNotFoundException {
        return defaultUserManager.updateUser(user);
    }

    @Override
    public void deleteUser(String userId) throws UserNotFoundException {
        defaultUserManager.deleteUser(userId);
    }

    @Override
    public Set<User> searchUsers(UserSearchCriteria criteria) {
        return defaultUserManager.searchUsers(criteria);
    }

    @Override
    public User getUser(String userId) throws UserNotFoundException {
        return defaultUserManager.getUser(userId);
    }

    @Override
    public User getUser(final String userId, final Set<String> roleIds) throws UserNotFoundException {
        return defaultUserManager.getUser(userId, roleIds);
    }

    @Override
    public void changePassword(String userId, String newPassword) throws UserNotFoundException {
        throw new UnsupportedOperationException("SSO/SAML users can't change passwords");
    }

    /** @since 3.70.0 */
    @Override
    public boolean isConfigured() {
        return true;
    }

}
