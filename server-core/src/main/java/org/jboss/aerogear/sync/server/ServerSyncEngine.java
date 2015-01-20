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
package org.jboss.aerogear.sync.server;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.DefaultBackupShadowDocument;
import org.jboss.aerogear.sync.DefaultClientDocument;
import org.jboss.aerogear.sync.DefaultPatchMessage;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ShadowDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server side of the differential synchronization implementation.
 *
 * @param <T> The type of document that this implementation can handle.
 */
public class ServerSyncEngine<T> {

    private static final Logger logger = LoggerFactory.getLogger(ServerSyncEngine.class);
    private static final int SEEDED_CLIENT_VERSION = -1;
    private static final int SEEDED_SERVER_VERSION = 1;
    private static final LinkedList<Edit> EMPTY_EDITS = new LinkedList<Edit>();
    private static final ConcurrentHashMap<String, Set<Subscriber<?>>> subscribers =
            new ConcurrentHashMap<String, Set<Subscriber<?>>>();
    private final ServerSynchronizer<T> synchronizer;
    private final ServerDataStore<T> dataStore;

    public ServerSyncEngine(final ServerSynchronizer<T> synchronizer, final ServerDataStore<T> dataStore) {
        this.synchronizer = synchronizer;
        this.dataStore = dataStore;
    }

    /**
     * Adds a subscriber for the specified document.
     *
     * A server does not create a new document itself, this would be created by a client
     * and a first revision is added to this synchronization engine by this method call.
     *
     * @param subscriber the subscriber to add
     * @param document the document that the subscriber subscribes to. Will be added to the underlying
     *                 datastore if it does not already exist in the datastore.
     */
    public PatchMessage addSubscriber(final Subscriber<?> subscriber, final Document<T> document) {
        addSubscriber(subscriber, document.id());
        return addDocument(document, subscriber.clientId());
    }

    private PatchMessage addDocument(final Document<T> document, final String clientId) {
        if (document.content() == null) {
            final Document<T> existingDoc = dataStore.getDocument(document.id());
            if (existingDoc == null) {
                return new DefaultPatchMessage(document.id(), clientId, EMPTY_EDITS);
            } else {
                final ShadowDocument<T> shadow = addShadowForClient(document.id(), clientId);
                logger.debug("Document with id [" + document.id() + "] already exists.");
                final Edit edit = synchronizer.serverDiff(shadow.document(), seededShadowFrom(shadow, document));
                dataStore.updateDocument(patchDocument(shadow));
                return new DefaultPatchMessage(document.id(), clientId, new LinkedList<Edit>(Collections.singleton(edit)));
            }
        }
        final boolean newDoc = dataStore.initDocument(document);
        final ShadowDocument<T> shadow = addShadowForClient(document.id(), clientId);
        if (newDoc) {
            final Edit edit = synchronizer.serverDiff(shadow.document(), incrementServerVersion(shadow));
            return new DefaultPatchMessage(document.id(), clientId, new LinkedList<Edit>(Collections.singleton(edit)));
        } else {
            logger.debug("Document with id [" + document.id() + "] already exists.");
            final Edit edit = synchronizer.serverDiff(shadow.document(), seededShadowFrom(shadow, document));
            return new DefaultPatchMessage(document.id(), clientId, new LinkedList<Edit>(Collections.singleton(edit)));
        }
    }

    /**
     * Adds a subscriber to an already existing document.
     *
     * @param subscriber the {@link Subscriber} to add
     * @param documentId the id of the document that the subscriber wants to subscribe.
     */
    public void addSubscriber(final Subscriber<?> subscriber, final String documentId) {
        final Set<Subscriber<?>> newSub = Collections.newSetFromMap(new ConcurrentHashMap<Subscriber<?>, Boolean>());
        newSub.add(subscriber);
        while(true) {
            final Set<Subscriber<?>> currentClients = subscribers.get(documentId);
            if (currentClients == null) {
                final Set<Subscriber<?>> previous = subscribers.putIfAbsent(documentId, newSub);
                if (previous != null) {
                    newSub.addAll(previous);
                    if (subscribers.replace(documentId, previous, newSub)) {
                        break;
                    }
                }
            } else {
                newSub.addAll(currentClients);
                if (subscribers.replace(documentId, currentClients, newSub)) {
                    break;
                }
            }
        }
    }

    public void removeSubscriber(final Subscriber<?> subscriber, final String documentId) {
        while (true) {
            final Set<Subscriber<?>> currentClients = subscribers.get(documentId);
            if (currentClients == null || currentClients.isEmpty()) {
                break;
            }
            final Set<Subscriber<?>> newClients = Collections.newSetFromMap(new ConcurrentHashMap<Subscriber<?>, Boolean>());
            newClients.addAll(currentClients);
            final boolean removed = newClients.remove(subscriber);
            if (removed) {
                if (subscribers.replace(documentId, currentClients, newClients)) {
                    break;
                }
            }
        }
    }

    public Set<Subscriber<?>> subscribers(final String documentId) {
        return subscribers.get(documentId);
    }

    private ShadowDocument<T> seededShadowFrom(final ShadowDocument<T> shadow, final Document<T> doc) {
        final Document<T> document = doc.content() == null ? dataStore.getDocument(doc.id()) : doc;
        final ClientDocument<T> clientDoc = new DefaultClientDocument<T>(doc.id(), shadow.document().clientId(), document.content());
        return new DefaultShadowDocument<T>(SEEDED_SERVER_VERSION, SEEDED_CLIENT_VERSION, clientDoc);
    }

    /**
     * Performs the server side diff which is performed when the server document is modified.
     * The produced {@link Edit} can be sent to the client for patching the client side documents.
     *
     * @param documentId the document in question.
     * @param clientId the clientId for whom we should perform a diff and create edits for.
     * @return {@link Edit} The server edits, or updates, that were generated by this diff .
     */
    public Edit diff(final String documentId, final String clientId) {
        final Document<T> document = dataStore.getDocument(documentId);
        final Edit edit = serverDiffs(document, clientId);
        diffPatchShadow(dataStore.getShadowDocument(documentId, clientId), edit);
        return edit;
    }

    /**
     * Performs the server side patching for a specific client.
     *
     * @param patchMessage the changes made by a client.
     * @return {@link PatchMessage} to allow method chaining
     */
    public PatchMessage patch(final PatchMessage patchMessage) {
        final ShadowDocument<T> patchedShadow = patchShadow(patchMessage);
        dataStore.updateDocument(patchDocument(patchedShadow));
        dataStore.saveBackupShadowDocument(newBackupShadow(patchedShadow));
        return patchMessage;
    }

    /**
     * Performs the server side patching for a specific client and updates
     * all subscribers to the patched document.
     *
     * @param patchMessage the changes made by a client.
     */
    public void patchAndNotifySubscribers(final PatchMessage patchMessage) {
        notifySubscribers(patch(patchMessage));
    }

    private void notifySubscribers(final PatchMessage clientPatchMessage) {
        final Edit peek = clientPatchMessage.edits().peek();
        if (peek == null) {
            // edits could be null as a client is allowed to send an patch message
            // that only contains an acknowledgement that it has received a specific
            // version from the server.
            return;
        }
        final String documentId = peek.documentId();
        final Set<Subscriber<?>> subscribers = subscribers(documentId);
        for (Subscriber<?> subscriber: subscribers) {
            final PatchMessage patchMessage = diffs(documentId, subscriber.clientId());
            logger.debug("Sending to [" + subscriber.clientId() + "] : " + patchMessage);
            subscriber.patched(patchMessage);
        }
    }

    public PatchMessage diffs(final String documentId, final String clientId) {
        diff(documentId, clientId);
        return new DefaultPatchMessage(documentId, clientId, dataStore.getEdits(documentId, clientId));
    }

    private void diffPatchShadow(final ShadowDocument<T> shadow, final Edit edit) {
        dataStore.saveShadowDocument(synchronizer.patchShadow(edit, shadow));
    }

    private ShadowDocument<T> addShadowForClient(final String documentId, final String clientId) {
        return addShadow(documentId, clientId, 0L);
    }

    private ShadowDocument<T> addShadow(final String documentId, final String clientId, final long clientVersion) {
        final Document<T> document = dataStore.getDocument(documentId);
        final ClientDocument<T> clientDocument = new DefaultClientDocument<T>(documentId, clientId, document.content());
        final ShadowDocument<T> shadowDocument = new DefaultShadowDocument<T>(0, clientVersion, clientDocument);
        dataStore.saveShadowDocument(shadowDocument);
        dataStore.saveBackupShadowDocument(newBackupShadow(shadowDocument));
        return shadowDocument;
    }

    private Edit clientDiffs(final Document<T> document, final ShadowDocument<T> shadow) {
        return synchronizer.clientDiff(document, shadow);
    }

    private Edit serverDiffs(final Document<T> document, final String clientId) {
        final ShadowDocument<T> shadow = dataStore.getShadowDocument(document.id(), clientId);
        final Edit newEdit = synchronizer.serverDiff(document, shadow);
        dataStore.saveEdits(newEdit);
        dataStore.saveShadowDocument(incrementServerVersion(shadow));
        return newEdit;
    }

    private ShadowDocument<T> patchShadow(final PatchMessage patchMessage) {
        ShadowDocument<T> shadow = dataStore.getShadowDocument(patchMessage.documentId(), patchMessage.clientId());
        final Iterator<Edit> iterator = patchMessage.edits().iterator();
        while (iterator.hasNext()) {
            final Edit edit = iterator.next();
            if (droppedServerPacket(edit, shadow)) {
                shadow = restoreBackup(shadow, edit);
                continue;
            }
            if (hasClientUpdate(edit, shadow)) {
                // discard edit
                dataStore.removeEdit(edit);
                iterator.remove();
                continue;
            }
            if (allVersionMatch(edit, shadow)) {
                // save shadow and remove edit
                final ShadowDocument<T> patchedShadow = synchronizer.patchShadow(edit, shadow);
                dataStore.removeEdit(edit);
                shadow = incrementClientVersion(patchedShadow);
                dataStore.saveShadowDocument(shadow);
            }
        }
        return shadow;
    }

    private boolean droppedServerPacket(final Edit edit, final ShadowDocument<T> shadowDocument) {
        return edit.serverVersion() < shadowDocument.serverVersion();
    }

    private boolean hasClientUpdate(final Edit edit, final ShadowDocument<T> shadowDocument) {
        return edit.clientVersion() < shadowDocument.clientVersion();
    }

    private boolean allVersionMatch(final Edit edit, final ShadowDocument<T> shadowDocument) {
        return edit.serverVersion() == shadowDocument.serverVersion()
                && edit.clientVersion() == shadowDocument.clientVersion();
    }

    private ShadowDocument<T> restoreBackup(final ShadowDocument<T> shadow,
                                            final Edit edit) {
        final BackupShadowDocument<T> backup = dataStore.getBackupShadowDocument(edit.documentId(), edit.clientId());
        if (serverVersionMatch(backup, edit)) {
            final ShadowDocument<T> patchedShadow = synchronizer.patchShadow(edit,
                    new DefaultShadowDocument<T>(backup.backupVersion(), shadow.clientVersion(), backup.shadow().document()));
            dataStore.removeEdits(edit.documentId(), edit.clientId());
            ShadowDocument<T> newShadow = incrementClientVersion(patchedShadow);
            dataStore.saveShadowDocument(newShadow);
            return newShadow;
        } else {
            throw new IllegalStateException(backup + " server version does not match version of " + edit.serverVersion());
        }
    }

    private boolean serverVersionMatch(final BackupShadowDocument<T> backup, final Edit edit) {
        return backup.backupVersion() == edit.serverVersion();
    }

    private Document<T> patchDocument(final ShadowDocument<T> shadowDocument) {
        final Document<T> document = dataStore.getDocument(shadowDocument.document().id());
        final Edit edit = clientDiffs(document, shadowDocument);
        final Document<T> patched = synchronizer.patchDocument(edit, document);
        dataStore.updateDocument(patched);
        logger.info("Patched Document [" + patched.id() + "] content: " + patched.content());
        return patched;
    }

    private ShadowDocument<T> incrementClientVersion(final ShadowDocument<T> shadow) {
        final long clientVersion = shadow.clientVersion() + 1;
        return new DefaultShadowDocument<T>(shadow.serverVersion(), clientVersion, shadow.document());
    }

    private ShadowDocument<T> incrementServerVersion(final ShadowDocument<T> shadow) {
        final long serverVersion = shadow.serverVersion() + 1;
        return new DefaultShadowDocument<T>(serverVersion, shadow.clientVersion(), shadow.document());
    }

    private DefaultBackupShadowDocument<T> newBackupShadow(final ShadowDocument<T> shadow) {
        return new DefaultBackupShadowDocument<T>(shadow.serverVersion(), shadow);
    }
}
