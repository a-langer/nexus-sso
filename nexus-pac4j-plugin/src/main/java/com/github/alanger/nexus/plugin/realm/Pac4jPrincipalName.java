package com.github.alanger.nexus.plugin.realm;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.github.alanger.shiroext.realm.IPrincipalName;
import com.github.alanger.shiroext.realm.IUserPrefix;

import org.pac4j.core.profile.AnonymousProfile;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.util.CommonHelper;

/**
 * Moved from shiro-ext library for recompile with
 * {@link org.pac4j.core.profile.UserProfile} as interface instead of class.
 * 
 * @since buji-pac4j:5.0.0
 * @since Nexus:3.70.0
 * @see https://github.com/bujiio/buji-pac4j/blob/master/src/main/java/io/buji/pac4j/subject/Pac4jPrincipal.java
 */
public class Pac4jPrincipalName implements Principal, Serializable, IUserPrefix, IPrincipalName {

    private final List<? extends UserProfile> profiles;
    private final boolean byName;
    private String userPrefix = "";
    private String principalNameAttribute;

    public Pac4jPrincipalName(final List<? extends UserProfile> profiles) {
        this(profiles, null, false);
    }

    public Pac4jPrincipalName(final List<? extends UserProfile> profiles, String principalNameAttribute,
            boolean byName) {
        this.profiles = profiles;
        this.principalNameAttribute = CommonHelper.isBlank(principalNameAttribute) ? null
                : principalNameAttribute.trim();
        this.byName = byName;
    }

    public Pac4jPrincipalName(final List<? extends UserProfile> profiles, String principalNameAttribute) {
        this.profiles = profiles;
        this.principalNameAttribute = CommonHelper.isBlank(principalNameAttribute) ? null
                : principalNameAttribute.trim();
        this.byName = !CommonHelper.isBlank(principalNameAttribute);
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
    public String getPrincipalNameAttribute() {
        return principalNameAttribute;
    }

    @Override
    public void setPrincipalNameAttribute(String principalNameAttribute) {
        this.principalNameAttribute = principalNameAttribute;
    }

    public boolean isByName() {
        return byName;
    }

    public UserProfile getProfile() {
        return flatIntoOneProfile(this.profiles).get();
    }

    // Compatibility with buji-pac4j 4.1.1
    public static <U extends UserProfile> Optional<U> flatIntoOneProfile(final Collection<U> profiles) {
        final Optional<U> profile = profiles.stream().filter(p -> p != null && !(p instanceof AnonymousProfile))
                .findFirst();
        if (profile.isPresent()) {
            return profile;
        } else {
            return profiles.stream().filter(Objects::nonNull).findFirst();
        }
    }

    public List<? extends UserProfile> getProfiles() {
        return this.profiles;
    }

    /**
     * Equals by string for compatibility with internal ApiKeyStore implementation
     * 
     * @since 3.70.1-02 - Equals by string
     * @see org.sonatype.nexus.internal.security.apikey.ApiKeyStoreImpl#principalMatches
     */
    @Override
    public boolean equals(Object o) {
        if (o instanceof String)
            return ((String) o).equals(getName());
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        final Pac4jPrincipalName that = (Pac4jPrincipalName) o;
        return profiles != null ? profiles.equals(that.profiles) : that.profiles == null;
    }

    @Override
    public int hashCode() {
        return profiles != null ? profiles.hashCode() : 0;
    }

    @Override
    public String getName() {
        final UserProfile profile = this.getProfile();
        if (null == principalNameAttribute) {
            return profile.getId();
        }
        final Object attrValue = profile.getAttribute(principalNameAttribute);
        return (null == attrValue) ? null : getUserPrefix() + String.valueOf(attrValue).replaceAll("(^\\[)|(\\]$)", "");
    }

    @Override
    public String toString() {
        if (isByName()) {
            String name = getName();
            return name != null ? name : getUserPrefix() + getProfile().getId();
        } else {
            return CommonHelper.toNiceString(this.getClass(), "profiles", getProfiles());
        }
    }

}
