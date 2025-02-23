package com.github.alanger.nexus.plugin.datastore;

import javax.inject.Inject;
import javax.inject.Named;
import org.sonatype.nexus.crypto.LegacyCipherFactory; // since 3.75.1
import org.sonatype.nexus.crypto.LegacyCipherFactory.PbeCipher; // since 3.75.1
import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.Base64Variants;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class should be used if you need to search in database on the encrypted string.
 * 
 * @see org.sonatype.nexus.datastore.mybatis.MyBatisDataStore#prepare
 * @see org.sonatype.nexus.datastore.mybatis.handlers.EncryptedStringTypeHandler
 * @see org.sonatype.nexus.datastore.mybatis.MyBatisCipher
 * 
 * @see org.sonatype.nexus.crypto.secrets.EncryptDecryptService
 * @see org.sonatype.nexus.crypto.internal.PbeCipherFactory
 * @see org.sonatype.nexus.crypto.internal.PbeCipherFactory.PbeCipher
 */
@Named
public class EncryptedString {

    public static final Base64Variant BASE_64 = Base64Variants.getDefaultVariant();

    // Hidden bean org.sonatype.nexus.datastore.mybatis.MyBatisCipher
    private final PbeCipher databaseCipher;

    @Inject
    public EncryptedString(final LegacyCipherFactory pbeCipherFactory,
            @Named("${nexus.mybatis.cipher.password:-changeme}") final String password,
            @Named("${nexus.mybatis.cipher.salt:-changeme}") final String salt,
            @Named("${nexus.mybatis.cipher.iv:-0123456789ABCDEF}") final String iv) throws Exception {
        this.databaseCipher = pbeCipherFactory.create(password, salt, iv);
    }

    public final PbeCipher cipher() {
        return this.databaseCipher;
    }

    /**
     * Encrypt string using database cipher + Base64.
     */
    public final String encrypt(final String value) {
        return BASE_64.encode(cipher().encrypt(value.getBytes(UTF_8)));
    }

    /**
     * Decrypt string using Base64 + database cipher.
     */
    public final String decrypt(final String value) {
        return value != null ? new String(cipher().decrypt(BASE_64.decode(value)), UTF_8) : null;
    }

}
