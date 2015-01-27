package org.jboss.aerogear.sync;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ClientRevision implements Revision<ClientRevision>, Comparable<ClientRevision> {

//    public static final ClientRevision ZERO = new ClientRevision(0L);
//    public static final ClientRevision ONE = new ClientRevision(1L);

    /**
     * TODO(lfryc): needs comment from Dan Bevenius what exactly SEEDED version (originally from ServerSyncEngine) is good for
     */
//    public static final ClientRevision SEEDED_VERSION = new ClientRevision(-1);

    /**
     * TODO(lfryc): this should be reinspected, in original DefaultEdit, the default was undefined
     */
//    public static final ClientRevision DEFAULT_VERSION = new ClientRevision(0L);

    private static ConcurrentHashMap<String, Long> order = new ConcurrentHashMap<String, Long>();
    private static AtomicLong counter = new AtomicLong(Long.MIN_VALUE);

    private String version;

    public ClientRevision(String version) {
        order.put(version, counter.get());
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ClientRevision increment(Document<?> document) {
        return initialRevision(document);
    }

    @Override
    public int compareTo(ClientRevision o) {
//        throw new UnsupportedOperationException("TBD");
        return Long.compare(order.get(version), order.get(o.version));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClientRevision other = (ClientRevision) obj;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ClientRevision [" + version + "]";
    }

    public static ClientRevision initialRevision(Document<?> document) {
        String content = (String) document.content();
        if (content == null) {
            content = "";
        }
        return new ClientRevision(Checksum.checksum(content));
    }
}
