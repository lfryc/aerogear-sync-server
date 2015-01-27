package org.jboss.aerogear.sync;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;

public final class Checksum {

    private static final String UTF_8 = Charset.forName("UTF-8").displayName();

    public static String checksum(final String content) {
        try {
            final MessageDigest md = MessageDigest.getInstance( "SHA1" );
            md.update(content.getBytes(UTF_8));
            return new BigInteger(1, md.digest()).toString(16);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
    }
}
