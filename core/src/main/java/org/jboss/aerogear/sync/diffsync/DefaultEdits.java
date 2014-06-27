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
package org.jboss.aerogear.sync.diffsync;

import java.util.LinkedList;

public class DefaultEdits implements Edits {

    private final String clientId;
    private final String documentId;
    private final long version;
    private final String checksum;
    private final LinkedList<Diff> diffs;

    public DefaultEdits(final String clientId, final String documentId, final long version, final String checksum, final LinkedList<Diff> diffs) {
        this.clientId = clientId;
        this.documentId = documentId;
        this.version = version;
        this.checksum = checksum;
        this.diffs = diffs;
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
    public long version() {
        return version;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultEdits that = (DefaultEdits) o;

        if (version != that.version) return false;
        if (!checksum.equals(that.checksum)) return false;
        if (!clientId.equals(that.clientId)) return false;
        if (!diffs.equals(that.diffs)) return false;
        if (!documentId.equals(that.documentId)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = clientId.hashCode();
        result = 31 * result + documentId.hashCode();
        result = 31 * result + (int) (version ^ (version >>> 32));
        result = 31 * result + checksum.hashCode();
        result = 31 * result + diffs.hashCode();
        return result;
    }
}
