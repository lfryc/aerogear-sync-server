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
package org.jboss.aerogear.sync.diffsync.client;

import org.jboss.aerogear.sync.diffsync.BackupShadowDocument;
import org.jboss.aerogear.sync.diffsync.ClientDocument;
import org.jboss.aerogear.sync.diffsync.Edits;
import org.jboss.aerogear.sync.diffsync.ShadowDocument;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ClientInMemoryDataStore implements ClientDataStore<String> {

    private final ConcurrentMap<Id, ClientDocument<String>> documents = new ConcurrentHashMap<Id, ClientDocument<String>>();
    private final ConcurrentMap<Id, ShadowDocument<String>> shadows = new ConcurrentHashMap<Id, ShadowDocument<String>>();
    private final ConcurrentMap<Id, BackupShadowDocument<String>> backups = new ConcurrentHashMap<Id, BackupShadowDocument<String>>();
    private static final ConcurrentHashMap<Id, Set<Edits>> pendingEdits =
            new ConcurrentHashMap<Id, Set<Edits>>();

    @Override
    public void saveShadowDocument(final ShadowDocument<String> shadowDocument) {
        shadows.put(id(shadowDocument.document()), shadowDocument);
    }

    @Override
    public ShadowDocument<String> getShadowDocument(final String documentId, final String clientId) {
        return shadows.get(id(clientId, documentId));
    }

    @Override
    public void saveBackupShadowDocument(final BackupShadowDocument<String> backupShadow) {
        backups.put(id(backupShadow.shadow().document()), backupShadow);
    }

    @Override
    public BackupShadowDocument<String> getBackupShadowDocument(final String clientId, final String documentId) {
        return backups.get(id(clientId, documentId));
    }

    @Override
    public void saveClientDocument(final ClientDocument<String> document) {
        documents.put(id(document), document);
    }

    @Override
    public ClientDocument<String> getClientDocument(final String clientId, final String documentId) {
        return documents.get(id(clientId, documentId));
    }

    @Override
    public void saveEdits(final Edits edits) {
        final Id id = id(edits.clientId(), edits.documentId());
        final Set<Edits> newEdits = Collections.newSetFromMap(new ConcurrentHashMap<Edits, Boolean>());
        newEdits.add(edits);
        while (true) {
            final Set<Edits> currentEdits = pendingEdits.get(id);
            if (currentEdits == null) {
                final Set<Edits> previous = pendingEdits.putIfAbsent(id, newEdits);
                if (previous != null) {
                    newEdits.addAll(previous);
                    if (pendingEdits.replace(id, previous, newEdits)) {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                newEdits.addAll(currentEdits);
                if (pendingEdits.replace(id, currentEdits, newEdits)) {
                    break;
                }
            }
        }
    }

    @Override
    public void removeEdits(final Edits edits) {
        final Id id = id(edits.clientId(), edits.documentId());
        while (true) {
            final Set<Edits> currentEdits = pendingEdits.get(id);
            if (currentEdits != null || !currentEdits.isEmpty()) {
                final Set<Edits> newEdits = Collections.newSetFromMap(new ConcurrentHashMap<Edits, Boolean>());
                if (newEdits.addAll(currentEdits)) {
                    if (newEdits.remove(edits)) {
                        pendingEdits.replace(id, currentEdits, newEdits);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public Set<Edits> getEdits(final String clientId, final String documentId) {
        return pendingEdits.get(id(clientId, documentId));
    }

    private static Id id(final ClientDocument<String> document) {
        return id(document.clientId(), document.id());
    }

    private static Id id(final String clientId, final String documentId) {
        return new Id(clientId, documentId);
    }

    private static class Id {

        private final String clientId;
        private final String documentId;

        private Id(final String clientId, final String documentId) {
            this.clientId = clientId;
            this.documentId = documentId;
        }

        public String clientId() {
            return clientId;
        }

        public String getDocumentId() {
            return documentId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id)) {
                return false;
            }

            final Id id = (Id) o;

            if (clientId != null ? !clientId.equals(id.clientId) : id.clientId != null) {
                return false;
            }
            if (documentId != null ? !documentId.equals(id.documentId) : id.documentId != null) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = clientId != null ? clientId.hashCode() : 0;
            result = 31 * result + (documentId != null ? documentId.hashCode() : 0);
            return result;
        }
    }
}
