/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.sync.client;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.DefaultBackupShadowDocument;
import org.jboss.aerogear.sync.DefaultPatchMessage;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ShadowDocument;

import java.util.Iterator;
import java.util.Observable;

/**
 * The client side of the differential synchronization implementation.
 *
 * @param <T> The type of document that this implementation can handle.
 */
public class ClientSyncEngine<T> extends Observable { 

    private final ClientSynchronizer<T> clientSynchronizer;
    private final ClientDataStore<T> dataStore;

    public ClientSyncEngine(final ClientSynchronizer<T> clientSynchronizer, final ClientDataStore<T> dataStore) {
        this.clientSynchronizer = clientSynchronizer;
        this.dataStore = dataStore;
    }

    /**
     * Adds a new document to this sync engine.
     *
     * @param document the document to add.
     */
    public void addDocument(final ClientDocument<T> document) {
        saveDocument(document);
        saveBackupShadow(saveShadow(new DefaultShadowDocument<T>(0, 0, document)));
    }

    /**
     * Returns an {@link PatchMessage} which contains a diff against the engine's stored
     * shadow document and the passed-in document.
     *
     * There might be pending edits that represent edits that have not made it to the server
     * for some reason (for example packet drop). If a pending edit exits the contents (the diffs)
     * of the pending edit will be included in the returned Edits from this method.
     *
     * The returned {@link PatchMessage} instance is indended to be sent to the server engine
     * for processing.
     *
     * @param document the updated document.
     * @return {@link PatchMessage} containing the edits for the changes in the document.
     */
    public PatchMessage diff(final ClientDocument<T> document) {
        final ShadowDocument<T> shadow = getShadowDocument(document.id(), document.clientId());
        final Edit edit = serverDiff(document, shadow);
        saveEdits(edit);
        final ShadowDocument<T> patchedShadow = diffPatchShadow(shadow, edit);
        saveShadow(incrementClientVersion(patchedShadow));
        return getPendingEdits(document.id(), document.clientId());
    }

    /**
     * Patches the client side shadow with updates ({@link PatchMessage}) from the server.
     *
     * When updates happen on the server, the server will create an {@link PatchMessage} instance
     * by calling the server engines diff method. This {@link PatchMessage} instance will then be
     * sent to the client for processing which is done by this method.
     *
     * @param patchMessage the updates from the server.
     */
    public void patch(final PatchMessage patchMessage) {
        final ShadowDocument<T> patchedShadow = patchShadow(patchMessage);
        patchDocument(patchedShadow);
        saveBackupShadow(patchedShadow);
    }

    private ShadowDocument<T> diffPatchShadow(final ShadowDocument<T> shadow, final Edit edit) {
        return clientSynchronizer.patchShadow(edit, shadow);
    }


    private ShadowDocument<T> patchShadow(final PatchMessage patchMessage) {
        ShadowDocument<T> shadow = getShadowDocument(patchMessage.documentId(), patchMessage.clientId());
        final Iterator<Edit> iterator = patchMessage.edits().iterator();
        while (iterator.hasNext()) {
            final Edit edit = iterator.next();
            if (clientPacketDropped(edit, shadow)) {
                shadow = restoreBackup(shadow, edit);
                continue;
            }
            if (hasServerVersion(edit, shadow)) {
                discardEdit(edit, iterator);
                continue;
            }
            if (allVersionsMatch(edit, shadow) || isSeedVersion(edit)) {
                final ShadowDocument<T> patchedShadow = clientSynchronizer.patchShadow(edit, shadow);
                if (isSeedVersion(edit)) {
                    shadow = saveShadowAndRemoveEdit(withClientVersion(patchedShadow, 0), edit);
                } else {
                    shadow = saveShadowAndRemoveEdit(incrementServerVersion(patchedShadow), edit);
                }
            }
        }
        return shadow;
    }

    private static boolean isSeedVersion(final Edit edit) {
        return edit.clientVersion() == -1;
    }

    private ShadowDocument<T> restoreBackup(final ShadowDocument<T> shadow,
                                            final Edit edit) {
        final BackupShadowDocument<T> backup = getBackupShadowDocument(edit.documentId(), edit.clientId());
        if (clientVersionMatch(edit, backup)) {
            final ShadowDocument<T> patchedShadow = clientSynchronizer.patchShadow(edit,
                    newShadowDoc(backup.backupVersion(), shadow.clientVersion(), backup.shadow().document()));
            dataStore.removeEdits(edit.documentId(), edit.clientId());
            return saveShadow(incrementServerVersion(patchedShadow), edit);
        } else {
            throw new IllegalStateException("Backup version [" + backup.backupVersion() + "] does not match edit client version [" + edit.clientVersion() + ']');
        }
    }

    private boolean clientVersionMatch(final Edit edit, final BackupShadowDocument<T> backup) {
        return edit.clientVersion() == backup.backupVersion();
    }

    private ShadowDocument<T> saveShadowAndRemoveEdit(final ShadowDocument<T> shadow, final Edit edit) {
        dataStore.removeEdit(edit);
        return saveShadow(shadow);
    }

    private ShadowDocument<T> saveShadow(final ShadowDocument<T> shadow, final Edit edit) {
        dataStore.removeEdit(edit);
        return saveShadow(shadow);
    }

    private void discardEdit(final Edit edit, final Iterator<Edit> iterator) {
        dataStore.removeEdit(edit);
        iterator.remove();
    }

    private boolean allVersionsMatch(final Edit edit, final ShadowDocument<T> shadow) {
        return edit.serverVersion() == shadow.serverVersion() && edit.clientVersion() == shadow.clientVersion();
    }

    private boolean clientPacketDropped(final Edit edit, final ShadowDocument<T> shadow) {
        return edit.clientVersion() < shadow.clientVersion() && !isSeedVersion(edit);
    }
    
    private boolean hasServerVersion(final Edit edit, final ShadowDocument<T> shadow) {
        return edit.serverVersion() < shadow.serverVersion();
    }

    private Document<T> patchDocument(final ShadowDocument<T> shadowDocument) {
        final ClientDocument<T> document = dataStore.getClientDocument(shadowDocument.document().id(), shadowDocument.document().clientId());
        final Edit edit = clientDiff(document, shadowDocument);
        final ClientDocument<T> patched = clientSynchronizer.patchDocument(edit, document);
        saveDocument(patched);
        saveBackupShadow(shadowDocument);
        setChanged();
        notifyObservers(patched);
        return patched;
    }

    private ShadowDocument<T> getShadowDocument(final String documentId, final String clientId) {
        return dataStore.getShadowDocument(documentId, clientId);
    }

    private BackupShadowDocument<T> getBackupShadowDocument(final String documentId, final String clientId) {
        return dataStore.getBackupShadowDocument(documentId, clientId);
    }

    private PatchMessage getPendingEdits(final String documentId, final String clientId) {
        return new DefaultPatchMessage(documentId, clientId, dataStore.getEdits(documentId, clientId));
    }

    private Edit clientDiff(final ClientDocument<T> doc, final ShadowDocument<T> shadow) {
        return clientSynchronizer.clientDiff(doc, shadow);
    }
    
    private Edit serverDiff(final ClientDocument<T> doc, final ShadowDocument<T> shadow) {
        return clientSynchronizer.serverDiff(doc, shadow);
    }

    private void saveEdits(final Edit edit) {
        dataStore.saveEdits(edit);
    }

    private ShadowDocument<T> incrementClientVersion(final ShadowDocument<T> shadow) {
        final long clientVersion = shadow.clientVersion() + 1;
        return newShadowDoc(shadow.serverVersion(), clientVersion, shadow.document());
    }

    private ShadowDocument<T> withClientVersion(final ShadowDocument<T> shadow, final long clientVersion) {
        return newShadowDoc(shadow.serverVersion(), clientVersion, shadow.document());
    }

    private ShadowDocument<T> saveShadow(final ShadowDocument<T> newShadow) {
        dataStore.saveShadowDocument(newShadow);
        return newShadow;
    }

    private ShadowDocument<T> newShadowDoc(final long serverVersion, final long clientVersion, final ClientDocument<T> doc) {
        return new DefaultShadowDocument<T>(serverVersion, clientVersion, doc);
    }

    private ShadowDocument<T> incrementServerVersion(final ShadowDocument<T> shadow) {
        final long serverVersion = shadow.serverVersion() + 1;
        return newShadowDoc(serverVersion, shadow.clientVersion(), shadow.document());
    }

    private void saveBackupShadow(final ShadowDocument<T> newShadow) {
        dataStore.saveBackupShadowDocument(new DefaultBackupShadowDocument<T>(newShadow.clientVersion(), newShadow));
    }

    private void saveDocument(final ClientDocument<T> document) {
        dataStore.saveClientDocument(document);
    }

}
