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
package org.jboss.aerogear.sync;

import java.util.LinkedList;

import org.jboss.aerogear.sync.Diff.Operation;

public class DefaultEdit implements Edit {

    private final String clientId;
    private final String documentId;
    private final ClientRevision clientVersion;
    private final ServerRevision serverVersion;
    private final String checksum;
    private final LinkedList<Diff> diffs;

    private DefaultEdit(final Builder builder) {
        clientId = builder.clientId;
        documentId = builder.documentId;
        clientVersion = builder.clientVersion;
        serverVersion = builder.serverVersion;
        checksum = builder.checksum;
        diffs = builder.diffs;
    }

    @Override
    public String clientId() {
        return clientId;
    }

    @Override
    public String documentId() {
        return documentId;
    }

    @Override
    public ClientRevision clientVersion() {
        return clientVersion;
    }

    @Override
    public ServerRevision serverVersion() {
        return serverVersion;
    }

    @Override
    public String checksum() {
        return checksum;
    }

    @Override
    public LinkedList<Diff> diffs() {
        return diffs;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((clientId == null) ? 0 : clientId.hashCode());
        result = prime * result + ((clientVersion == null) ? 0 : clientVersion.hashCode());
        result = prime * result + ((diffs == null) ? 0 : diffs.hashCode());
        result = prime * result + ((documentId == null) ? 0 : documentId.hashCode());
        result = prime * result + ((serverVersion == null) ? 0 : serverVersion.hashCode());
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
        DefaultEdit other = (DefaultEdit) obj;
        if (clientId == null) {
            if (other.clientId != null)
                return false;
        } else if (!clientId.equals(other.clientId))
            return false;
        if (clientVersion == null) {
            if (other.clientVersion != null)
                return false;
        } else if (!clientVersion.equals(other.clientVersion))
            return false;
        if (diffs == null) {
            if (other.diffs != null)
                return false;
        } else if (!diffs.equals(other.diffs))
            return false;
        if (documentId == null) {
            if (other.documentId != null)
                return false;
        } else if (!documentId.equals(other.documentId))
            return false;
        if (serverVersion == null) {
            if (other.serverVersion != null)
                return false;
        } else if (!serverVersion.equals(other.serverVersion))
            return false;
        return true;
    }

    public static Builder withDocumentId(final String documentId) {
        return new Builder(documentId);
    }

    public static class Builder {

        private final String documentId;
        private String clientId;
        private ServerRevision serverVersion;
        private ClientRevision clientVersion;
        private String checksum;
        private final LinkedList<Diff> diffs = new LinkedList<Diff>();

        public static Builder withDocumentId(final String documentId) {
            return new Builder(documentId);
        }

        private Builder(final String documentId) {
            this.documentId = documentId;
        }

        public Builder clientId(final String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder serverVersion(final ServerRevision serverVersion) {
            this.serverVersion = serverVersion;
            return this;
        }

        public Builder clientVersion(final ClientRevision clientVersion) {
            this.clientVersion = clientVersion;
            return this;
        }

        public Builder unchanged(final String text) {
            diffs.add(new DefaultDiff(Operation.UNCHANGED, text));
            return this;
        }

        public Builder add(final String text) {
            diffs.add(new DefaultDiff(Operation.ADD, text));
            return this;
        }

        public Builder delete(final String text) {
            diffs.add(new DefaultDiff(Operation.DELETE, text));
            return this;
        }

        public Builder diff(final Diff diff) {
            diffs.add(diff);
            return this;
        }

        public Builder diffs(final LinkedList<Diff> diffs) {
            this.diffs.addAll(diffs);
            return this;
        }

        public Builder checksum(final String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Edit build() {
            if (clientId == null) {
                throw new IllegalArgumentException("clientId must not be null");
            }
            return new DefaultEdit(this);
        }
    }
}
