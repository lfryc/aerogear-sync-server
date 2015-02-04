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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.DefaultBackupShadowDocument;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ShadowDocument;

import java.util.Iterator;
import java.util.Observable;
import java.util.Queue;


/**
 * The ClientSyncEngine is responsible for driving client side of the differential synchronization algorithm.
 * <p>
 * During construction the engine gets injected with an instance of {@link ClientSynchronizer}
 * which takes care of diff/patching operations, and an instance of {@link ClientDataStore} for
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
 * final ClientInMemoryDataStore<JsonNode, JsonPatchEdit> dataStore = new ClientInMemoryDataStore<JsonNode, JsonPatchEdit>();
 * final ClientSyncEngine<JsonNode, JsonPatchEdit> syncEngine = new ClientSyncEngine<JsonNode, JsonPatchEdit>(synchronizer, dataStore);
 * }</pre>
 *
 * @param <T> The data type data that this implementation can handle.
 * @param <S> The type of {@link Edit}s that this implementation can handle.
 */
public class ClientSyncEngine<T, S extends Edit<? extends Diff>> extends Observable {

    private static final  ObjectMapper OM = new ObjectMapper();
    private final ClientSynchronizer<T, S> clientSynchronizer;
    private final ClientDataStore<T, S> dataStore;

    public ClientSyncEngine(final ClientSynchronizer<T, S> clientSynchronizer, final ClientDataStore<T, S> dataStore) {
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
    public PatchMessage<S> diff(final ClientDocument<T> document) {
        final String documentId = document.id();
        final String clientId = document.clientId();
        final ShadowDocument<T> shadow = getShadowDocument(documentId, clientId);
        final S edit = serverDiff(document, shadow);
        saveEdits(edit, documentId, clientId);
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
    public void patch(final PatchMessage<S> patchMessage) {
        final ShadowDocument<T> patchedShadow = patchShadow(patchMessage);
        patchDocument(patchedShadow);
        saveBackupShadow(patchedShadow);
    }

    /**
     * Creates a {link PatchMessage} by parsing the passed-in json.
     *
     * @param json the json representation of a {@code PatchMessage}
     * @return {@link PatchMessage} the created {code PatchMessage}
     */
    public PatchMessage<S> patchMessageFromJson(final String json) {
        return clientSynchronizer.patchMessageFromJson(json);
    }

    /**
     * Converts the {@link ClientDocument} into a JSON {@code String} representation.
     *
     * @param document the {@link ClientDocument} to convert
     * @return {@code String} the JSON String representation of the document.
     */
    public String documentToJson(final ClientDocument<T> document) {
        final ObjectNode objectNode = OM.createObjectNode();
        objectNode.put("msgType", "add");
        objectNode.put("id", document.id());
        objectNode.put("clientId", document.clientId());
        clientSynchronizer.addContent(document.content(), objectNode, "content");
        return objectNode.toString();
    }

    /**
     * Creates a new {@link PatchMessage} with the with the type of {@link Edit} that this
     * synchronizer can handle.
     *
     * @param documentId the document identifier for the {@code PatchMessage}
     * @param clientId the client identifier for the {@code PatchMessage}
     * @param edits the {@link Edit}s for the {@code PatchMessage}
     * @return {@link PatchMessage} the created {code PatchMessage}
     */
    public PatchMessage<S> createPatchMessage(final String documentId, final String clientId, final Queue<S> edits) {
        return clientSynchronizer.createPatchMessage(documentId, clientId, edits);
    }

    private ShadowDocument<T> diffPatchShadow(final ShadowDocument<T> shadow, final S edit) {
        return clientSynchronizer.patchShadow(edit, shadow);
    }

    private ShadowDocument<T> patchShadow(final PatchMessage<S> patchMessage) {
        final String documentId = patchMessage.documentId();
        final String clientId = patchMessage.clientId();
        ShadowDocument<T> shadow = getShadowDocument(documentId, clientId);
        final Iterator<S> iterator = patchMessage.edits().iterator();
        while (iterator.hasNext()) {
            final S edit = iterator.next();
            if (clientPacketDropped(edit, shadow)) {
                shadow = restoreBackup(shadow, edit);
                continue;
            }
            if (hasServerVersion(edit, shadow)) {
                discardEdit(edit, documentId, clientId, iterator);
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

    private boolean isSeedVersion(final S edit) {
        return edit.clientVersion() == -1;
    }

    private ShadowDocument<T> restoreBackup(final ShadowDocument<T> shadow,
                                            final S edit) {
        final String documentId = shadow.document().id();
        final String clientId = shadow.document().clientId();
        final BackupShadowDocument<T> backup = getBackupShadowDocument(documentId, clientId);
        if (clientVersionMatch(edit, backup)) {
            final ShadowDocument<T> patchedShadow = clientSynchronizer.patchShadow(edit, backup.shadow());
            dataStore.removeEdits(documentId, clientId);
            return saveShadow(incrementServerVersion(patchedShadow), edit);
        } else {
            throw new IllegalStateException("Backup version [" + backup.version() + "] does not match edit client version [" + edit.clientVersion() + ']');
        }
    }

    private boolean clientVersionMatch(final S edit, final BackupShadowDocument<T> backup) {
        return edit.clientVersion() == backup.version();
    }

    private ShadowDocument<T> saveShadowAndRemoveEdit(final ShadowDocument<T> shadow, final S edit) {
        dataStore.removeEdit(edit, shadow.document().id(), shadow.document().clientId());
        return saveShadow(shadow);
    }

    private ShadowDocument<T> saveShadow(final ShadowDocument<T> shadow, final S edit) {
        dataStore.removeEdit(edit, shadow.document().id(), shadow.document().clientId());
        return saveShadow(shadow);
    }

    private void discardEdit(final S edit, final String documentId, final String clientId, final Iterator<S> iterator) {
        dataStore.removeEdit(edit, documentId, clientId);
        iterator.remove();
    }

    private boolean allVersionsMatch(final S edit, final ShadowDocument<T> shadow) {
        return edit.serverVersion() == shadow.serverVersion() && edit.clientVersion() == shadow.clientVersion();
    }

    private boolean clientPacketDropped(final S edit, final ShadowDocument<T> shadow) {
        return edit.clientVersion() < shadow.clientVersion() && !isSeedVersion(edit);
    }
    
    private boolean hasServerVersion(final S edit, final ShadowDocument<T> shadow) {
        return edit.serverVersion() < shadow.serverVersion();
    }

    private Document<T> patchDocument(final ShadowDocument<T> shadowDocument) {
        final ClientDocument<T> clientDocument = getClientDocumentForShadow(shadowDocument);
        final S edit = clientDiff(clientDocument, shadowDocument);
        final ClientDocument<T> patched = patchDocument(edit, clientDocument);
        saveDocument(patched);
        saveBackupShadow(shadowDocument);
        setChanged();
        notifyObservers(patched);
        return patched;
    }

    private ClientDocument<T> patchDocument(final S edit, final ClientDocument<T> clientDocument) {
        return clientSynchronizer.patchDocument(edit, clientDocument);
    }

    private ClientDocument<T> getClientDocumentForShadow(final ShadowDocument<T> shadow) {
        return dataStore.getClientDocument(shadow.document().id(), shadow.document().clientId());
    }

    private ShadowDocument<T> getShadowDocument(final String documentId, final String clientId) {
        return dataStore.getShadowDocument(documentId, clientId);
    }

    private BackupShadowDocument<T> getBackupShadowDocument(final String documentId, final String clientId) {
        return dataStore.getBackupShadowDocument(documentId, clientId);
    }

    private PatchMessage<S> getPendingEdits(final String documentId, final String clientId) {
        return clientSynchronizer.createPatchMessage(documentId, clientId, dataStore.getEdits(documentId, clientId));
    }

    private S clientDiff(final ClientDocument<T> doc, final ShadowDocument<T> shadow) {
        return clientSynchronizer.clientDiff(shadow, doc);
    }
    
    private S serverDiff(final ClientDocument<T> doc, final ShadowDocument<T> shadow) {
        return clientSynchronizer.serverDiff(doc, shadow);
    }

    private void saveEdits(final S edit, final String documentId, final String clientId) {
        dataStore.saveEdits(edit, documentId, clientId);
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
