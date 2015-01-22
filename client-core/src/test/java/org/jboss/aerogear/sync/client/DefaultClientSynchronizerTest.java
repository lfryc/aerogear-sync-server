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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;

import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.ClientRevision;
import org.jboss.aerogear.sync.DefaultClientDocument;
import org.jboss.aerogear.sync.DefaultDocument;
import org.jboss.aerogear.sync.DefaultEdit;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.ServerRevision;
import org.jboss.aerogear.sync.ShadowDocument;
import org.junit.Before;
import org.junit.Test;

public class DefaultClientSynchronizerTest {

    private ClientSynchronizer<String> clientSynchronizer;

    @Before
    public void createDocuments() {
        clientSynchronizer = new DefaultClientSynchronizer();
    }

    @Test
    public void diff() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String original = "Do or do not, there is no try.";
        final String update = "Do or do not, there is no try!";
        final ShadowDocument<String> clientShadow = shadowDocument(documentId, clientId, original);

        final Edit edit = clientSynchronizer.clientDiff(newDoc(documentId, update), clientShadow);
        assertThat(edit.clientVersion().version(), is(0L));
        assertThat(edit.serverVersion().version(), is(0L));
        assertThat(edit.clientId(), is(clientId));
        assertThat(edit.diffs().size(), is(3));
        final List<Diff> diffs = edit.diffs();
        assertThat(diffs.get(0).operation(), is(Diff.Operation.UNCHANGED));
        assertThat(diffs.get(0).text(), equalTo("Do or do not, there is no try"));
        assertThat(diffs.get(1).operation(), is(Diff.Operation.DELETE));
        assertThat(diffs.get(1).text(), equalTo("!"));
        assertThat(diffs.get(2).operation(), is(Diff.Operation.ADD));
        assertThat(diffs.get(2).text(), equalTo("."));
    }

    @Test
    public void patchShadow() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String updatedVersion = "Do or do not, there is no try!";
        final ShadowDocument<String> clientShadow = shadowDocument(documentId, clientId, originalVersion);

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        final ShadowDocument<String> patchedShadow = clientSynchronizer.patchShadow(edit, clientShadow);
        assertThat(patchedShadow.document().content(), equalTo(updatedVersion));
    }

    @Test
    public void patchDocument() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String updatedVersion = "Do or do nothing, there is no try.";
        final ClientDocument<String> clientShadow = new DefaultClientDocument<String>(documentId, clientId, originalVersion);

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .unchanged("Do or do not")
                .add("hing")
                .unchanged(", there is no try.")
                .build();
        final ClientDocument<String> patchedDocument = clientSynchronizer.patchDocument(edit, clientShadow);
        assertThat(patchedDocument.content(), equalTo(updatedVersion));
    }

    private static ShadowDocument<String> shadowDocument(final String documentId,
                                                         final String clientId,
                                                         final String content) {
        final ClientDocument<String> clientDoc = new DefaultClientDocument<String>(documentId, clientId, content);
        return new DefaultShadowDocument<String>(ServerRevision.ZERO, ClientRevision.ZERO, clientDoc);
    }

    private static Document<String> newDoc(final String documentId, final String content) {
        return new DefaultDocument<String>(documentId, content);
    }
}
