package org.jboss.aerogear.sync;

import java.util.Queue;

/**
 * A DataStore implementation is responible for storing and serving data for a
 * Differential Synchronization implementation.
 *
 * @param <T> The type of the Document that this data store can handle.
 */
public interface DataStore<T> {

    /**
     * Saves a shadow document.
     *
     * @param shadowDocument the {@link ShadowDocument} to save.
     */
    void saveShadowDocument(ShadowDocument<T> shadowDocument);

    /**
     * Retrieves the {@link ShadowDocument} matching the passed-in document documentId.
     *
     * @param documentId the document id of the shadow document.
     * @param clientId the client for which to retrieve the shadow document.
     * @return {@link ShadowDocument} the shadow document matching the documentId.
     */
    ShadowDocument<T> getShadowDocument(String documentId, ServerRevision serverRevision, ClientRevision clientRevision);

    /**
     * Saves a backup shadow document
     *
     * @param backupShadow the {@link BackupShadowDocument} to save.
     */
    void saveBackupShadowDocument(BackupShadowDocument<T> backupShadow);

    /**
     * Retrieves the {@link BackupShadowDocument} matching the passed-in document documentId.
     *
     * @param documentId the document identifier of the backup shadow document.
     * @param clientId the client identifier for which to fetch the document.
     * @return {@link BackupShadowDocument} the backup shadow document matching the documentId.
     */
    BackupShadowDocument<T> getBackupShadowDocument(String documentId, ServerRevision serverRevision);

    /**
     * Saves an {@link Edit} to the data store.
     *
     * @param edit the edit to be saved.
     */
    void saveEdits(Edit edit);

    /**
     * Retreives the queue of {@link Edit}s for the specified document documentId.
     *
     * @param documentId the document identifier of the edit.
     * @param clientId the client identifier for which to fetch the document.
     * @return {@code Queue<Edits>} the edits for the document.
     */
    Queue<Edit> getEdits(String documentId, String clientId);

    /**
     * Removes the edit from the store.
     *
     * @param edit the edit to be removed.
     */
    void removeEdit(Edit edit);

    /**
     * Removes all edits for the specific client and document pair.
     *
     * @param documentId the document identifier of the edit.
     * @param clientId the client identifier.
     */
    void removeEdits(String documentId, String clientId);

}
