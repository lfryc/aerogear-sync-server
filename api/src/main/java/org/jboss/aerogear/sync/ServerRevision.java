package org.jboss.aerogear.sync;

public class ServerRevision implements Revision {

    private long version;

    public ServerRevision(long version) {
        this.version = version;
    }

    @Override
    public long version() {
        return version;
    }
}
