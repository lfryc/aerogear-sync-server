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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.ClientRevision;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.ServerRevision;
import org.jboss.aerogear.sync.ShadowDocument;

public class ClientInMemoryDataStore implements ClientDataStore<String> {

    private static final Queue<Edit> EMPTY_QUEUE = new LinkedList<Edit>();
    private final ConcurrentMap<Id, ClientDocument<String>> documents = new ConcurrentHashMap<Id, ClientDocument<String>>();
    private final ConcurrentMap<ShadowId, ShadowDocument<String>> shadows = new ConcurrentHashMap<ShadowId, ShadowDocument<String>>();
    private final ConcurrentMap<BackupId, BackupShadowDocument<String, ClientRevision>> backups = new ConcurrentHashMap<BackupId, BackupShadowDocument<String, ClientRevision>>();
    private final ConcurrentHashMap<Id, Queue<Edit>> pendingEdits = new ConcurrentHashMap<Id, Queue<Edit>>();

    @Override
    public void saveShadowDocument(final ShadowDocument<String> shadowDocument) {
        shadows.put(id(shadowDocument.document().id(), shadowDocument.clientVersion(), shadowDocument.serverVersion()), shadowDocument);
    }

    @Override
    public ShadowDocument<String> getShadowDocument(final String documentId, ClientRevision clientRevision, ServerRevision serverRevision) {
        return shadows.get(id(documentId, clientRevision, serverRevision));
    }

    @Override
    public void saveBackupShadowDocument(final BackupShadowDocument<String, ClientRevision> backupShadow) {
        backups.put(id(backupShadow.shadow().document().id(), backupShadow.backupVersion()), backupShadow);
    }

    @Override
    public BackupShadowDocument<String, ClientRevision> getBackupShadowDocument(final String documentId, ClientRevision clientRevision) {
        return backups.get(id(documentId, clientRevision));
    }

    @Override
    public void saveClientDocument(final ClientDocument<String> document) {
        documents.put(id(document), document);
    }

    @Override
    public ClientDocument<String> getClientDocument(final String documentId, final String clientId) {
        return documents.get(id(documentId, clientId));
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
                if (oldEdit.clientVersion().compareTo(edit.clientVersion()) <= 0) {
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

    private static ShadowId id(final String documentId, ClientRevision clientRevision, ServerRevision serverRevision) {
        return new ShadowId(documentId, clientRevision, serverRevision);
    }

    private static BackupId id(final String documentId, ClientRevision serverRevision) {
        return new BackupId(documentId, serverRevision);
    }

    private static class Id {

        private final String clientId;
        private final String documentId;

        private Id(final String documentId, final String clientId) {
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
    }private static class ShadowId {

        private final String documentId;
        private final ClientRevision clientRevision;
        private final ServerRevision serverRevision;

        public ShadowId(String documentId, ClientRevision clientRevision, ServerRevision serverRevision) {
            super();
            this.documentId = documentId;
            this.clientRevision = clientRevision;
            this.serverRevision = serverRevision;
        }

        public String getDocumentId() {
            return documentId;
        }

        public ClientRevision getClientRevision() {
            return clientRevision;
        }

        public ServerRevision getServerRevision() {
            return serverRevision;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((clientRevision == null) ? 0 : clientRevision.hashCode());
            result = prime * result + ((documentId == null) ? 0 : documentId.hashCode());
            result = prime * result + ((serverRevision == null) ? 0 : serverRevision.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ShadowId other = (ShadowId) obj;
            if (clientRevision == null) {
                if (other.clientRevision != null)
                    return false;
            } else if (!clientRevision.equals(other.clientRevision))
                return false;
            if (documentId == null) {
                if (other.documentId != null)
                    return false;
            } else if (!documentId.equals(other.documentId))
                return false;
            if (serverRevision == null) {
                if (other.serverRevision != null)
                    return false;
            } else if (!serverRevision.equals(other.serverRevision))
                return false;
            return true;
        }

    }

    private static class BackupId {

        private final String documentId;
        private final ClientRevision serverRevision;

        public BackupId(String documentId, ClientRevision serverRevision) {
            super();
            this.documentId = documentId;
            this.serverRevision = serverRevision;
        }

        public String getDocumentId() {
            return documentId;
        }

        public ClientRevision getServerRevision() {
            return serverRevision;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((documentId == null) ? 0 : documentId.hashCode());
            result = prime * result + ((serverRevision == null) ? 0 : serverRevision.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            BackupId other = (BackupId) obj;
            if (documentId == null) {
                if (other.documentId != null)
                    return false;
            } else if (!documentId.equals(other.documentId))
                return false;
            if (serverRevision == null) {
                if (other.serverRevision != null)
                    return false;
            } else if (!serverRevision.equals(other.serverRevision))
                return false;
            return true;
        }
    }
}
