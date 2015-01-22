package org.jboss.aerogear.sync.server;

import org.jboss.aerogear.sync.PatchMessage;

/**
 * Represents a subscriber of patches.
 *
 * @param <T> type of the channel of this subscriber.
 */
public interface Subscriber<T> {

    /**
     * The client identifier of this subscriber
     *
     * @return {@code String} the subscribers/clients identifier
     */
    String clientId();

    /**
     * The channel that this subscriber uses to communicate.
     * <p>
     * This can be anything that the subscriber supports, for example it could be
     * object representing a connection, or a simple String representing the communication
     * channel.
     *
     * @return {@code T} the channel used to communicate.
     */
    T channel();

    /**
     * Is called when this subscribers underlying document has been patched, allowing
     * it to handle the {@link PatchMessage}.
     *
     * @param patchMessage the result of patching this subscribers underying document.
     */
    void patched(PatchMessage patchMessage);

//    ClientRevision clientVersion();
//
//    ServerRevision serverRevision();
}
