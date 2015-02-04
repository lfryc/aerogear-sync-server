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

import com.fasterxml.jackson.databind.JsonNode;
import org.jboss.aerogear.sync.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ServerSyncEngine is responsible for driving the main differential synchronization algorithm.
 * <p>
 * During construction the engine gets injected with an instance of {@link ServerSynchronizer}
 * which takes care of diff/patching operations, and an instance of {@link ServerDataStore} for
 * storing data.
 * <p>
 * A synchronizer in AeroGear is a module that serves two purposes which are closely related. One, is to provide
 * storage for the data type, and the second is to provide the patching algorithm to be used on that data type.
 * The name synchronizer is because they take care of the synchronization part of the Differential Synchronization
 * algorithm. For example, one synchronizer might support plain text while another supports JSON Objects as the
 * content of documents being stored. But a patching algorithm used for plain text might not be appropriate for JSON
 * Objects.
 * <p>
 *
 * To construct a server that uses the JSON Patch you would use the following code:
 * <pre>
 * {@code
 * final JsonPatchServerSynchronizer synchronizer = new JsonPatchServerSynchronizer();
 * final ServerInMemoryDataStore<JsonNode, JsonPatchEdit> dataStore = new ServerInMemoryDataStore<JsonNode, JsonPatchEdit>();
 * final ServerSyncEngine<JsonNode, JsonPatchEdit> syncEngine = new ServerSyncEngine<JsonNode, JsonPatchEdit>(synchronizer, dataStore);
 * }</pre>
 *
 * @param <T> The data type data that this implementation can handle.
 * @param <S> The type of {@link Edit}s that this implementation can handle.
 */
public class ServerSyncEngine<T, S extends Edit<? extends Diff>> {

    private static final Logger logger = LoggerFactory.getLogger(ServerSyncEngine.class);
    private static final int SEEDED_CLIENT_VERSION = -1;
    private static final int SEEDED_SERVER_VERSION = 1;
    private static final ConcurrentHashMap<String, Set<Subscriber<?>>> subscribers =
            new ConcurrentHashMap<String, Set<Subscriber<?>>>();
    private final ServerSynchronizer<T, S> synchronizer;
    private final ServerDataStore<T, S> dataStore;

    /**
     * Sole constructor.
     *
     * @param synchronizer an instance of {@link ServerSynchronizer} that will take care for the diff/patching
     * @param dataStore an instance of {@link ServerDataStore} to store the document/objects
     */
    public ServerSyncEngine(final ServerSynchronizer<T, S> synchronizer, final ServerDataStore<T, S> dataStore) {
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
     * @param document   the document that the subscriber subscribes to. Will be added to the underlying
     *                   datastore if it does not already exist in the datastore.
     * @return {@link PatchMessage} for the {@link Document}. Will either be an PatchMessage with an empty
     *                   diff, if this is the initial addition of the document, or if the document already
     *                   exists in the underlying datastore the patch message will contain a diff to bring
     *                   the document up to date.
     */
    public PatchMessage<S> addSubscriber(final Subscriber<?> subscriber, final Document<T> document) {
        final PatchMessage<S> patchMessage = addDocument(document, subscriber.clientId());
        connectSubscriber(subscriber, document.id());
        return patchMessage;
    }

    /**
     * Connects a subscriber to an already existing document.
     *
     * @param subscriber the {@link Subscriber} to add
     * @param documentId the id of the document that the subscriber wants to subscribe.
     */
    public void connectSubscriber(final Subscriber<?> subscriber, final String documentId) {
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

    /**
     * Removes the specified {@link Subscriber}.
     *
     * @param subscriber the {@link Subscriber} to remove
     * @param documentId the document id that the subscriber subscribes to
     */
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

    /**
     * Returns all the subscribers for the specified document.
     *
     * @param documentId the id of the document for which all subscribers should be returned.
     * @return {@code Set} all the {@link Subscriber}s
     */
    public Set<Subscriber<?>> getSubscribers(final String documentId) {
        return subscribers.get(documentId);
    }

    /**
     * Performs the server side diff which is performed when the server document is modified.
     * The produced {@link Edit} can be sent to the client for patching the client side document.
     *
     * @param documentId the document in question.
     * @param clientId the clientId for whom we should perform a diff and create edits for.
     * @return {@link Edit} The server edits, or updates, that were generated by this diff .
     */
    public S diff(final String documentId, final String clientId) {
        final Document<T> document = getDocument(documentId);
        final S edit = serverDiffs(document, clientId);
        diffPatchShadow(getShadowDocument(documentId, clientId), edit);
        return edit;
    }

    /**
     * Performs the server side patching for a specific client.
     *
     * @param patchMessage the changes made by a client.
     * @return {@link PatchMessage} to allow method chaining
     */
    public PatchMessage<S> patch(final PatchMessage<S> patchMessage) {
        final ShadowDocument<T> patchedShadow = patchShadow(patchMessage);
        updateDocument(patchDocument(patchedShadow));
        saveBackupShadow(patchedShadow);
        return patchMessage;
    }

    /**
     * Performs the server side patching for a specific client and updates
     * all subscribers to the patched document.
     *
     * @param patchMessage the changes made by a client.
     */
    public void notifySubscribers(final PatchMessage<S> patchMessage) {
        final S peek = patchMessage.edits().peek();
        if (peek == null) {
            // edits could be null as a client is allowed to send an patch message
            // that only contains an acknowledgement that it has received a specific
            // version from the server.
            return;
        }
        final String documentId = patchMessage.documentId();
        final Set<Subscriber<?>> subscribers1 = getSubscribers(documentId);
        for (Subscriber<?> subscriber: subscribers1) {
            final PatchMessage<?> patchMessage1 = getPatchMessage(documentId, subscriber.clientId());
            logger.debug("Sending to [" + subscriber.clientId() + "] : " + patchMessage1);
            subscriber.patched(patchMessage1);
        }
    }

    /**
     * Creates a {link PatchMessage} by parsing the passed-in json.
     *
     * @param json the json representation of a {@code PatchMessage}
     * @return {@link PatchMessage} the created {code PatchMessage}
     */
    public PatchMessage<S> patchMessageFromJson(final String json) {
        return synchronizer.patchMessageFromJson(json);
    }

    /**
     * Converts the {@link JsonNode} into a {@link Document} instance.
     *
     * @param json the {@link JsonNode} to convert
     * @return {@link Document} the document representing the contents of the {@link JsonNode} instance.
     */
    public Document<T> documentFromJson(final JsonNode json) {
        return synchronizer.documentFromJson(json);
    }

    /**
     * Returns the {@link PatchMessage} for the specified documentId and clientId.
     *
     * @param documentId the document identifier
     * @param clientId the client identifier
     * @return {@link PatchMessage} for the current document/client combination
     */
    public PatchMessage<S> getPatchMessage(final String documentId, final String clientId) {
        diff(documentId, clientId);
        return synchronizer.createPatchMessage(documentId, clientId, dataStore.getEdits(documentId, clientId));
    }

    private PatchMessage<S> addDocument(final Document<T> document, final String clientId) {
        if (document.content() == null) {
            final Document<T> existingDoc = getDocument(document.id());
            if (existingDoc == null) {
                return synchronizer.createPatchMessage(document.id(), clientId, emptyQueue());
            } else {
                final ShadowDocument<T> shadow = addShadowForClient(document.id(), clientId);
                logger.debug("Document with id [" + document.id() + "] already exists.");
                final S edit = serverDiff(shadow.document(), seededShadowFrom(shadow, document));
                updateDocument(patchDocument(shadow));
                return synchronizer.createPatchMessage(document.id(), clientId, asQueue(edit));
            }
        }
        final boolean newDoc = saveDocument(document);
        final ShadowDocument<T> shadow = addShadowForClient(document.id(), clientId);
        if (newDoc) {
            final S edit = serverDiff(shadow.document(), incrementServerVersion(shadow));
            return synchronizer.createPatchMessage(document.id(), clientId, asQueue(edit));
        } else {
            logger.debug("Document with id [" + document.id() + "] already exists.");
            final S edit = serverDiff(shadow.document(), seededShadowFrom(shadow, document));
            return synchronizer.createPatchMessage(document.id(), clientId, asQueue(edit));
        }
    }

    private ShadowDocument<T> seededShadowFrom(final ShadowDocument<T> shadow, final Document<T> doc) {
        final Document<T> document = doc.content() == null ? getDocument(doc.id()) : doc;
        final ClientDocument<T> clientDoc = newClientDocument(doc.id(), shadow.document().clientId(), document.content());
        return new DefaultShadowDocument<T>(SEEDED_SERVER_VERSION, SEEDED_CLIENT_VERSION, clientDoc);
    }

    private void diffPatchShadow(final ShadowDocument<T> shadow, final S edit) {
        saveShadow(synchronizer.patchShadow(edit, shadow));
    }

    private ShadowDocument<T> addShadowForClient(final String documentId, final String clientId) {
        return addShadow(documentId, clientId, 0L);
    }

    private ShadowDocument<T> addShadow(final String documentId, final String clientId, final long clientVersion) {
        final Document<T> document = getDocument(documentId);
        final ClientDocument<T> clientDocument = newClientDocument(documentId, clientId, document.content());
        final ShadowDocument<T> shadowDocument = newShadowDoc(0, clientVersion, clientDocument);
        saveShadow(shadowDocument);
        saveBackupShadow(shadowDocument);
        return shadowDocument;
    }

    private S clientDiffs(final Document<T> document, final ShadowDocument<T> shadow) {
        return clientDiff(document, shadow);
    }

    private S serverDiffs(final Document<T> document, final String clientId) {
        final String documentId = document.id();
        final ShadowDocument<T> shadow = getShadowDocument(documentId, clientId);
        final S newEdit = serverDiff(document, shadow);
        saveEdits(newEdit, documentId, clientId);
        saveShadow(incrementServerVersion(shadow));
        return newEdit;
    }

    private ShadowDocument<T> patchShadow(final PatchMessage<S> patchMessage) {
        final String documentId = patchMessage.documentId();
        final String clientId = patchMessage.clientId();
        ShadowDocument<T> shadow = getShadowDocument(documentId, clientId);
        final Iterator<S> iterator = patchMessage.edits().iterator();
        while (iterator.hasNext()) {
            final S edit = iterator.next();
            if (droppedServerPacket(edit, shadow)) {
                shadow = restoreBackup(shadow, edit);
                continue;
            }
            if (hasClientUpdate(edit, shadow)) {
                discardEdit(edit, documentId, clientId, iterator);
                continue;
            }
            if (allVersionMatch(edit, shadow)) {
                final ShadowDocument<T> patchedShadow = synchronizer.patchShadow(edit, shadow);
                shadow = saveShadowAndRemoveEdit(incrementClientVersion(patchedShadow), edit);
            }
        }
        return shadow;
    }

    private ShadowDocument<T> restoreBackup(final ShadowDocument<T> shadow,
                                            final S edit) {
        final String documentId = shadow.document().id();
        final String clientId = shadow.document().clientId();
        final BackupShadowDocument<T> backup = getBackupShadowDocument(documentId, clientId);
        if (serverVersionMatch(backup, edit)) {
            final ShadowDocument<T> patchedShadow = synchronizer.patchShadow(edit, backup.shadow());
            dataStore.removeEdits(documentId, clientId);
            return saveShadow(incrementClientVersion(patchedShadow));
        } else {
            throw new IllegalStateException(backup + " server version does not match version of " + edit.serverVersion());
        }
    }

    private void discardEdit(final S edit, final String documentId, final String clientId, final Iterator<S> iterator) {
        dataStore.removeEdit(edit, documentId, clientId);
        iterator.remove();
    }

    private ShadowDocument<T> saveShadowAndRemoveEdit(final ShadowDocument<T> shadow, final S edit) {
        dataStore.removeEdit(edit, shadow.document().id(), shadow.document().clientId());
        return saveShadow(shadow);
    }

    private boolean serverVersionMatch(final BackupShadowDocument<T> backup, final S edit) {
        return backup.version() == edit.serverVersion();
    }

    private boolean droppedServerPacket(final S edit, final ShadowDocument<T> shadowDocument) {
        return edit.serverVersion() < shadowDocument.serverVersion();
    }

    private boolean hasClientUpdate(final S edit, final ShadowDocument<T> shadowDocument) {
        return edit.clientVersion() < shadowDocument.clientVersion();
    }

    private boolean allVersionMatch(final S edit, final ShadowDocument<T> shadowDocument) {
        return edit.serverVersion() == shadowDocument.serverVersion()
                && edit.clientVersion() == shadowDocument.clientVersion();
    }

    private Document<T> patchDocument(final ShadowDocument<T> shadowDocument) {
        final Document<T> document = getDocument(shadowDocument.document().id());
        final S edit = clientDiffs(document, shadowDocument);
        final Document<T> patched = synchronizer.patchDocument(edit, document);
        saveDocument(patched);
        logger.info("Patched Document [" + patched.id() + "] content: " + patched.content());
        return patched;
    }

    private Document<T> getDocument(final String documentId) {
        return dataStore.getDocument(documentId);
    }

    private ClientDocument<T> newClientDocument(final String documentId, final String clientId, final T content) {
        return new DefaultClientDocument<T>(documentId, clientId, content);
    }

    private ShadowDocument<T> getShadowDocument(final String documentId, final String clientId) {
        return dataStore.getShadowDocument(documentId, clientId);
    }

    private BackupShadowDocument<T> getBackupShadowDocument(final String documentId, final String clientId) {
        return dataStore.getBackupShadowDocument(documentId, clientId);
    }

    private S clientDiff(final Document<T> doc, final ShadowDocument<T> shadow) {
        return synchronizer.clientDiff(doc, shadow);
    }

    private S serverDiff(final Document<T> doc, final ShadowDocument<T> shadow) {
        return synchronizer.serverDiff(doc, shadow);
    }

    private void saveEdits(final S edit, final String documentId, final String clientId) {
        dataStore.saveEdits(edit, documentId, clientId);
    }

    private ShadowDocument<T> incrementClientVersion(final ShadowDocument<T> shadow) {
        final long clientVersion = shadow.clientVersion() + 1;
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
        dataStore.saveBackupShadowDocument(new DefaultBackupShadowDocument<T>(newShadow.serverVersion(), newShadow));
    }

    private boolean saveDocument(final Document<T> document) {
        return dataStore.saveDocument(document);
    }

    private void updateDocument(final Document<T> document) {
        dataStore.updateDocument(document);
    }

    private Queue<S> emptyQueue() {
        return new LinkedList<S>();
    }

    private Queue<S> asQueue(final S edit) {
        return new LinkedList<S>(Collections.singleton(edit));
    }

}
