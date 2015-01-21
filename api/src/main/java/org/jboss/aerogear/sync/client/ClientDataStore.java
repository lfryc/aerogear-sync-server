package org.jboss.aerogear.sync.client;

import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.ClientRevision;
import org.jboss.aerogear.sync.DataStore;
import org.jboss.aerogear.sync.Document;

/**
 * A client side DataStore implementation is responsible for storing and serving data for a
 * Differential Synchronization implementation.
 *
 * @param <T> The type of the Document that this data store can handle.
 */
public interface ClientDataStore<T> extends DataStore<T, ClientRevision> {

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
