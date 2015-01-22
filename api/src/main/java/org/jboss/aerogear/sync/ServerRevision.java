package org.jboss.aerogear.sync;

public class ServerRevision implements Revision {

    public static final ServerRevision ZERO = new ServerRevision(0L);
    public static final ServerRevision ONE = new ServerRevision(1L);

    private long version;

    public ServerRevision(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
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
}
