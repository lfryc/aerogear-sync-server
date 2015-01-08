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

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.ShadowDocument;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class ServerInMemoryDataStore implements ServerDataStore<String> {

    private static final Queue<Edit> EMPTY_QUEUE = new LinkedList<Edit>();
    private final ConcurrentMap<String, Document<String>> documents = new ConcurrentHashMap<String, Document<String>>();
    private final ConcurrentMap<Id, ShadowDocument<String>> shadows = new ConcurrentHashMap<Id, ShadowDocument<String>>();
    private final ConcurrentMap<Id, BackupShadowDocument<String>> backups = new ConcurrentHashMap<Id, BackupShadowDocument<String>>();
    private final ConcurrentHashMap<Id, Queue<Edit>> pendingEdits = new ConcurrentHashMap<Id, Queue<Edit>>();

    @Override
    public void saveShadowDocument(final ShadowDocument<String> shadowDocument) {
        shadows.put(id(shadowDocument.document()), shadowDocument);
    }

    @Override
    public ShadowDocument<String> getShadowDocument(final String documentId, final String clientId) {
        return shadows.get(id(documentId, clientId));
    }

    @Override
    public void saveBackupShadowDocument(final BackupShadowDocument<String> backupShadow) {
        backups.put(id(backupShadow.shadow().document()), backupShadow);
    }

    @Override
    public BackupShadowDocument<String> getBackupShadowDocument(final String documentId, final String clientId) {
        return backups.get(id(documentId, clientId));
    }

    @Override
    public boolean initDocument(final Document<String> document) {
        return documents.putIfAbsent(document.id(), document) == null;
    }

    @Override
    public void updateDocument(final Document<String> document) {
        documents.put(document.id(), document);
    }

    @Override
    public Document<String> getDocument(final String documentId) {
        return documents.get(documentId);
    }

    @Override
    public void saveEdits(final Edit edit) {
        final Id id = id(edit.documentId(), edit.clientId());
        final Queue<Edit> newEdits = new ConcurrentLinkedQueue<Edit>();
        while (true) {
            final Queue<Edit> currentEdits = pendingEdits.get(id);
            if (currentEdits == null) {
                newEdits.add(edit);
                final Queue<Edit> previous = pendingEdits.putIfAbsent(id, newEdits);
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
                newEdits.add(edit);
                if (pendingEdits.replace(id, currentEdits, newEdits)) {
                    break;
                }
            }
        }
    }

    @Override
    public void removeEdit(final Edit edit) {
        final Id id = id(edit.documentId(), edit.clientId());
        while (true) {
            final Queue<Edit> currentEdits = pendingEdits.get(id);
            if (currentEdits == null) {
                break;
            }
            final Queue<Edit> newEdits = new ConcurrentLinkedQueue<Edit>();
            newEdits.addAll(currentEdits);
            for (Iterator<Edit> iter = newEdits.iterator(); iter.hasNext();) {
                final Edit oldEdit = iter.next();
                if (oldEdit.clientVersion() <= edit.clientVersion()) {
                    iter.remove();
                }
            }
            if (pendingEdits.replace(id, currentEdits, newEdits)) {
                break;
            }
        }
    }

    @Override
    public Queue<Edit> getEdits(final String documentId, final String clientId) {
        final Queue<Edit> edits = pendingEdits.get(id(documentId, clientId));
        if (edits == null) {
            return EMPTY_QUEUE;
        }
        return edits;
    }

    @Override
    public void removeEdits(final String documentId, final String clientId) {
        pendingEdits.remove(id(documentId, clientId));
    }

    private static Id id(final ClientDocument<String> document) {
        return id(document.id(), document.clientId());
    }

    private static Id id(final String documentId, final String clientId) {
        return new Id(documentId, clientId);
    }

    private static class Id {

        private final String clientId;
        private final String documentId;

        Id(final String documentId, final String clientId) {
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
            return documentId != null ? documentId.equals(id.documentId) : id.documentId == null;
        }

        @Override
        public int hashCode() {
            int result = clientId != null ? clientId.hashCode() : 0;
            result = 31 * result + (documentId != null ? documentId.hashCode() : 0);
            return result;
        }
    }
}
