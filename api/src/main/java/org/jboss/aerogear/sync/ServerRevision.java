package org.jboss.aerogear.sync;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


public class ServerRevision implements Revision<ServerRevision>, Comparable<ServerRevision> {

//    public static final ServerRevision ZERO = new ServerRevision(0L);
//    public static final ServerRevision ONE = new ServerRevision(1L);

//    public static final ServerRevision SEEDED_VERSION = ONE;

    /**
     * TODO(lfryc): this should be reinspected, in original DefaultEdit, the default was undefined
     */
//    public static final ServerRevision DEFAULT_VERSION = new ServerRevision(0L);

    private static ConcurrentHashMap<String, Long> order = new ConcurrentHashMap<String, Long>();
    private static AtomicLong counter = new AtomicLong(Long.MIN_VALUE);

    private String version;

    public ServerRevision(String version) {
        order.put(version, counter.get());
        this.version = version;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public ServerRevision increment(Document<?> document) {
        return initialRevision(document);
    }

    @Override
    public int compareTo(ServerRevision o) {
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
        ServerRevision other = (ServerRevision) obj;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ServerRevision [" + version + "]";
    }

    public static ServerRevision initialRevision(Document<?> document) {
        String content = (String) document.content();
        if (content == null) {
            content = "";
        }
        return new ServerRevision(Checksum.checksum(content));
    }
}
