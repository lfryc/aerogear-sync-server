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

import java.util.Iterator;
import java.util.Observable;
import java.util.Queue;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.ClientRevision;
import org.jboss.aerogear.sync.DefaultBackupShadowDocument;
import org.jboss.aerogear.sync.DefaultPatchMessage;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ServerRevision;
import org.jboss.aerogear.sync.ShadowDocument;

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
        dataStore.saveClientDocument(document);
        DefaultShadowDocument<T> newShadow = new DefaultShadowDocument<T>(new ServerRevision(0), new ClientRevision(0), document);
        dataStore.saveShadowDocument(newShadow);
        dataStore.saveBackupShadowDocument(newBackupShadow(newShadow));
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
    public PatchMessage diff(final ClientDocument<T> document, ClientRevision clientRevision, ServerRevision serverRevision) {
        final ShadowDocument<T> shadow = dataStore.getShadowDocument(document.id(), clientRevision, serverRevision);
        final Edit edit = clientSynchronizer.serverDiff(document, shadow);
        dataStore.saveEdits(edit);
        final ShadowDocument<T> patchedShadow = diffPatchShadow(shadow, edit);
        dataStore.saveShadowDocument(incrementClientVersion(patchedShadow));
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
        dataStore.saveBackupShadowDocument(newBackupShadow(patchedShadow));
    }

    private ShadowDocument<T> diffPatchShadow(final ShadowDocument<T> shadow, final Edit edit) {
        return clientSynchronizer.patchShadow(edit, shadow);
    }

    private PatchMessage getPendingEdits(final String documentId, final String clientId) {
        Queue<Edit> edits = dataStore.getEdits(documentId, clientId);
        return new DefaultPatchMessage(documentId, clientId, edits);
    }

    private ShadowDocument<T> patchShadow(final PatchMessage patchMessage) {
        ShadowDocument<T> shadow = dataStore.getShadowDocument(patchMessage.documentId(), patchMessage.edits().peek().clientVersion(), patchMessage.edits().peek().serverVersion());
        final Iterator<Edit> iterator = patchMessage.edits().iterator();
        while (iterator.hasNext()) {
            final Edit edit = iterator.next();
            if (clientPacketDropped(edit, shadow)) {
                shadow = restoreBackup(shadow, edit);
                continue;
            }
            if (hasServerVersion(edit, shadow)) {
                dataStore.removeEdit(edit);
                iterator.remove();
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

    private ShadowDocument<T> saveShadowAndRemoveEdit(final ShadowDocument<T> shadow, final Edit edit) {
        dataStore.removeEdit(edit);
        dataStore.saveShadowDocument(shadow);
        return shadow;
    }

    private static boolean isSeedVersion(final Edit edit) {
        return edit.clientVersion() == -1;
    }

    private ShadowDocument<T> restoreBackup(final ShadowDocument<T> shadow,
                                            final Edit edit) {
        final BackupShadowDocument<T, ClientRevision> backup = dataStore.getBackupShadowDocument(edit.documentId(), edit.clientVersion());
        if (clientVersionMatch(edit, backup)) {
            final ShadowDocument<T> patchedShadow = clientSynchronizer.patchShadow(edit,
                    new DefaultShadowDocument<T>(backup.backupVersion(), shadow.clientVersion(), backup.shadow().document()));
            dataStore.removeEdits(edit.documentId(), edit.clientId());
            ShadowDocument<T> newShadow = incrementServerVersion(patchedShadow);
            dataStore.removeEdit(edit);
            dataStore.saveShadowDocument(newShadow);
            return newShadow;
        } else {
            throw new IllegalStateException("Backup version [" + backup.backupVersion() + "] does not match edit client version [" + edit.clientVersion() + ']');
        }
    }

    private boolean clientVersionMatch(final Edit edit, final BackupShadowDocument<T> backup) {
        return edit.clientVersion() == backup.backupVersion();
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
        final Edit edit = clientSynchronizer.clientDiff(document, shadowDocument);
        final ClientDocument<T> patched = clientSynchronizer.patchDocument(edit, document);
        dataStore.saveClientDocument(patched);
        dataStore.saveBackupShadowDocument(newBackupShadow(shadowDocument));
        setChanged();
        notifyObservers(patched);
        return patched;
    }

    private ShadowDocument<T> incrementClientVersion(final ShadowDocument<T> shadow) {
        final long clientVersion = shadow.clientVersion() + 1;
        return new DefaultShadowDocument<T>(shadow.serverVersion(), clientVersion, shadow.document());
    }

    private ShadowDocument<T> withClientVersion(final ShadowDocument<T> shadow, final long clientVersion) {
        return new DefaultShadowDocument<T>(shadow.serverVersion(), clientVersion, shadow.document());
    }

    private ShadowDocument<T> incrementServerVersion(final ShadowDocument<T> shadow) {
        final long serverVersion = shadow.serverVersion() + 1;
        return new DefaultShadowDocument<T>(serverVersion, shadow.clientVersion(), shadow.document());
    }

    private DefaultBackupShadowDocument<T, ClientRevision> newBackupShadow(final ShadowDocument<T> shadow) {
        return new DefaultBackupShadowDocument<T, ClientRevision>(shadow.clientVersion(), shadow);
    }
}
