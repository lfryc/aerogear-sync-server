package org.jboss.aerogear.sync;

public class ClientRevision implements Revision, Comparable<ClientRevision> {

    public static final ClientRevision ZERO = new ClientRevision(0L);
    public static final ClientRevision ONE = new ClientRevision(1L);

    private long version;

    public ClientRevision(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public int compareTo(ClientRevision o) {
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
        ClientRevision other = (ClientRevision) obj;
        if (version != other.version)
            return false;
        return true;
    }
}
