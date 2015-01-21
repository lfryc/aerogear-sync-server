package org.jboss.aerogear.sync.server;

import org.jboss.aerogear.sync.DataStore;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.ServerRevision;

/**
 * A server side DataStore implementation is responsible for storing and serving data for a
 * Differential Synchronization implementation.
 *
 * @param <T> The type of the Document that this data store can handle.
 */
public interface ServerDataStore<T> extends DataStore<T, ServerRevision> {

    /**
     * Saves a server document.
     *
     * @param document the {@link Document} to save.
     * @return {code boolean} true if the document was stored to the underlying store.
     */
    boolean initDocument(Document<T> document);

    /**
     * Updates a server document.
     *
     * @param document the {@link Document} to update.
     */
    void updateDocument(Document<T> document);

    /**
     * Retrieves the {@link Document} matching the passed-in document documentId.
     *
     * @param documentId the document identifier of the shadow document.
     * @return {@link Document} the document matching the documentId.
     */
    Document<T> getDocument(String documentId);
}
