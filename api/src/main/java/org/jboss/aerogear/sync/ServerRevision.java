package org.jboss.aerogear.sync;

public class ServerRevision implements Revision<ServerRevision>, Comparable<ServerRevision> {

    public static final ServerRevision ZERO = new ServerRevision(0L);
    public static final ServerRevision ONE = new ServerRevision(1L);

    public static final ServerRevision SEEDED_VERSION = ONE;

    /**
     * TODO(lfryc): this should be reinspected, in original DefaultEdit, the default was undefined
     */
    public static final ServerRevision DEFAULT_VERSION = new ServerRevision(0L);

    private long version;

    public ServerRevision(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public ServerRevision increment() {
        return new ServerRevision(version + 1);
    }

    @Override
    public int compareTo(ServerRevision o) {
        return Long.compare(version, o.version);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (version ^ (version >>> 32));
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
        if (version != other.version)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ServerRevision [" + version + "]";
    }
}
