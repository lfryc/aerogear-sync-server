package org.jboss.aerogear.sync;

public class ClientRevision implements Revision {

    private long version;

    public ClientRevision(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
    }
}
