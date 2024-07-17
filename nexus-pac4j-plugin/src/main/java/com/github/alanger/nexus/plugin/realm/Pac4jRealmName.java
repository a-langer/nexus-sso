package com.github.alanger.nexus.plugin.realm;

import static com.github.alanger.shiroext.realm.RealmUtils.asList;
import static com.github.alanger.shiroext.realm.RealmUtils.filterBlackOrWhite;

import io.buji.pac4j.realm.Pac4jRealm;
import io.buji.pac4j.token.Pac4jToken;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.pac4j.core.profile.UserProfile;

import java.util.*;

import com.github.alanger.shiroext.realm.ICommonPermission;
import com.github.alanger.shiroext.realm.ICommonRole;
import com.github.alanger.shiroext.realm.IFilterPermission;
import com.github.alanger.shiroext.realm.IFilterRole;
import com.github.alanger.shiroext.realm.IPrincipalName;
import com.github.alanger.shiroext.realm.IUserPrefix;

/**
 * Moved from shiro-ext library for recompile with
 * {@link org.pac4j.core.profile.UserProfile} as interface instead of class.
 * 
 * @since buji-pac4j:5.0.0
 * @since Nexus:3.70.0
 * @see https://github.com/bujiio/buji-pac4j/blob/master/src/main/java/io/buji/pac4j/realm/Pac4jRealm.java
 */
public class Pac4jRealmName extends Pac4jRealm
        implements ICommonPermission, ICommonRole, IUserPrefix, IPrincipalName, IFilterRole, IFilterPermission {

    private String commonRole = null;
    private String commonPermission = null;
    private String userPrefix = "";

    private String roleWhiteList;
    private String roleBlackList;
    private String permissionWhiteList;
    private String permissionBlackList;

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken authenticationToken)
            throws AuthenticationException {

        final Pac4jToken token = (Pac4jToken) authenticationToken;

        // Compatibility with buji-pac4j 4.1.1
        final List<? extends UserProfile> profiles = token.getProfiles();

        final Pac4jPrincipalName principal = new Pac4jPrincipalName(profiles, getPrincipalNameAttribute());
        principal.setUserPrefix(getUserPrefix());
        final PrincipalCollection principalCollection = new SimplePrincipalCollection(principal, getName());
        return new SimpleAuthenticationInfo(principalCollection, profiles.hashCode());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
        final Set<String> roles = new HashSet<>();
        final Set<String> permissions = new HashSet<>();
        final Pac4jPrincipalName principal = principals.oneByType(Pac4jPrincipalName.class);
        if (principal != null) {
            roles.addAll(asList(commonRole));
            permissions.addAll(asList(commonPermission));

            // Compatibility with buji-pac4j 4.1.1
            final List<? extends UserProfile> profiles = principal.getProfiles();
            for (final UserProfile profile : profiles) {
                if (profile != null) {
                    roles.addAll(profile.getRoles());
                    profile.addRoles(asList(commonRole));

                    permissions.addAll(profile.getPermissions());
                    profile.addPermissions(asList(commonPermission));
                }
            }
        }

        final SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo();
        filterBlackOrWhite(roles, roleWhiteList, roleBlackList);
        simpleAuthorizationInfo.addRoles(roles);
        filterBlackOrWhite(permissions, permissionWhiteList, permissionBlackList);
        simpleAuthorizationInfo.addStringPermissions(permissions);
        return simpleAuthorizationInfo;
    }

    @Override
    public String getCommonRole() {
        return commonRole;
    }

    @Override
    public void setCommonRole(String commonRole) {
        this.commonRole = commonRole;
    }

    @Override
    public String getCommonPermission() {
        return commonPermission;
    }

    @Override
    public void setCommonPermission(String commonPermission) {
        this.commonPermission = commonPermission;
    }

    @Override
    public String getUserPrefix() {
        return userPrefix;
    }

    @Override
    public void setUserPrefix(String userPrefix) {
        this.userPrefix = userPrefix;
    }

    @Override
    public String getRoleWhiteList() {
        return roleWhiteList;
    }

    @Override
    public void setRoleWhiteList(String roleWhiteList) {
        this.roleWhiteList = roleWhiteList;
    }

    @Override
    public String getRoleBlackList() {
        return roleBlackList;
    }

    @Override
    public void setRoleBlackList(String roleBlackList) {
        this.roleBlackList = roleBlackList;
    }

    @Override
    public String getPermissionWhiteList() {
        return permissionWhiteList;
    }

    @Override
    public void setPermissionWhiteList(String permissionWhiteList) {
        this.permissionWhiteList = permissionWhiteList;
    }

    @Override
    public String getPermissionBlackList() {
        return permissionBlackList;
    }

    @Override
    public void setPermissionBlackList(String permissionBlackList) {
        this.permissionBlackList = permissionBlackList;
    }

}
