package org.jboss.aerogear.diffsync.client;

import org.jboss.aerogear.diffsync.ClientDocument;
import org.jboss.aerogear.diffsync.DataStore;
import org.jboss.aerogear.diffsync.Document;

/**
 * A client side DataStore implementation is responsible for storing and serving data for a
 * Differential Synchronization implementation.
 *
 * @param <T> The type of the Document that this data store can handle.
 */
public interface ClientDataStore<T> extends DataStore<T> {

    /**
     * Saves a client document.
     *
     * @param document the {@link ClientDocument} to save.
     */
    void saveClientDocument(ClientDocument<T> document);

    /**
     * Retrieves the {@link Document} matching the passed-in document documentId.
     *
     * @param documentId the document identifier of the document.
     * @param clientId the client identifier for which to fetch the document.
     * @return {@link ClientDocument} the document matching the documentId.
     */
    ClientDocument<T> getClientDocument(String documentId, String clientId);

}
