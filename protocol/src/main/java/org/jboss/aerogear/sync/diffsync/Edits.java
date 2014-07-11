package org.jboss.aerogear.sync.diffsync;

import java.util.Queue;

/**
 * Represents a stack of edits.
 */
public interface Edits {

    /**
     * Identifies the client that this edit instance belongs to.
     *
     * @return {@code String} the client identifier.
     */
    String clientId();

    /**
     * Identifies the document that this edit is related to
     *
     * @return {@code String} the document documentId.
     */
    String documentId();

    /**
     * The individual {@link Edit}s.
     *
     * @return {@code Queue<Edit>} the individual edits.
     */
    Queue<Edit> edits();

}
