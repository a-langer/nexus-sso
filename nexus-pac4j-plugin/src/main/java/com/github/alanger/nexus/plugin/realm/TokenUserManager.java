package com.github.alanger.nexus.plugin.realm;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.eclipse.sisu.Description;

import org.sonatype.nexus.common.event.EventManager;
import org.sonatype.nexus.security.config.SecurityConfigurationManager;
import org.sonatype.nexus.security.user.RoleMappingUserManager;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserNotFoundException;

/**
 * User manager for SSO Token realm.
 * 
 * @since 3.70.1-02
 * @see org.sonatype.nexus.security.internal.UserManagerImpl
 */
@Singleton
@Named(TokenUserManager.SOURCE)
@Description("Pac4jToken")
public class TokenUserManager extends Pac4jUserManager {

    public static final String SOURCE = "pac4jToken";

    @Inject
	public TokenUserManager(EventManager eventManager, SecurityConfigurationManager configuration, RoleMappingUserManager defaultUserManager) {
		super(eventManager, configuration, defaultUserManager);
	}
    
    //-- org.sonatype.nexus.security.user.UserManager --//

    @Override
    public String getSource() {
        return SOURCE;
    }

    @Override
    public String getAuthenticationRealmName() {
        return NexusTokenRealm.NAME;
    }

    @Override
    public User addUser(User user, String password) {
        throw new UnsupportedOperationException("SSO/Token users can't add");
    }

    @Override
    public User updateUser(User user) throws UserNotFoundException {
        throw new UnsupportedOperationException("SSO/Token users can't update");
    }

    @Override
    public void deleteUser(String userId) throws UserNotFoundException {
        throw new UnsupportedOperationException("SSO/Token users can't delete");
    }

    @Override
    public void changePassword(String userId, String newPassword) throws UserNotFoundException {
        throw new UnsupportedOperationException("SSO/Token users can't change passwords");
    }
}
