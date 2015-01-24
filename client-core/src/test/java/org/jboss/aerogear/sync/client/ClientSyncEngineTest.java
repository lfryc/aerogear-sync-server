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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.ClientRevision;
import org.jboss.aerogear.sync.DefaultClientDocument;
import org.jboss.aerogear.sync.DefaultEdit;
import org.jboss.aerogear.sync.DefaultPatchMessage;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Diff;
import org.jboss.aerogear.sync.Diff.Operation;
import org.jboss.aerogear.sync.Edit;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ServerRevision;
import org.jboss.aerogear.sync.ShadowDocument;
import org.junit.Before;
import org.junit.Test;

public class ClientSyncEngineTest {

    private ClientSyncEngine<String> engine;
    private ClientInMemoryDataStore dataStore;

    @Before
    public void setup() {
        dataStore = new ClientInMemoryDataStore();
        engine = new ClientSyncEngine<String>(new DefaultClientSynchronizer(), dataStore);
    }

    @Test
    public void addDocument() throws Exception {
        final String documentId = "1234";
        final String clientId = "client2";
        final String originalVersion = "{\"id\": 9999}";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.serverVersion(), equalTo(new ServerRevision(0L)));
        assertThat(shadowDocument.clientVersion(), equalTo(new ClientRevision(0L)));
        assertThat(shadowDocument.document().content(), equalTo(originalVersion));

        final BackupShadowDocument<String, ClientRevision> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, ClientRevision.ZERO);
        assertThat(backupShadowDocument.backupVersion(), equalTo(new ClientRevision(0L)));
        assertThat(backupShadowDocument.shadow(), equalTo(shadowDocument));
    }

    @Test
    public void diff() {
        final String documentId = "1234";
        final String clientId = "client2";
        final String updatedVersion = "{\"id\": 9999}";
        final String shadowVersion = "{\"id\": 6666}";
        engine.addDocument(clientDoc(documentId, clientId, updatedVersion));


        final PatchMessage patchMessage = engine.diff(clientDoc(documentId, clientId, shadowVersion));
        assertThat(patchMessage.documentId(), equalTo(documentId));
        assertThat(patchMessage.clientId(), equalTo(clientId));
        assertThat(patchMessage.edits().size(), is(1));
        final Edit edit = patchMessage.edits().peek();
        assertThat(edit.diffs().size(), is(4));
        final LinkedList<Diff> diffs = edit.diffs();
        assertThat(diffs.size(), is(4));
        assertThat(diffs.get(0).operation(), is(Operation.UNCHANGED));
        assertThat(diffs.get(0).text(), is("{\"id\": "));
        assertThat(diffs.get(1).operation(), is(Operation.DELETE));
        assertThat(diffs.get(1).text(), is("9999"));
        assertThat(diffs.get(2).operation(), is(Operation.ADD));
        assertThat(diffs.get(2).text(), is("6666"));
        assertThat(diffs.get(3).operation(), is(Operation.UNCHANGED));
        assertThat(diffs.get(3).text(), is("}"));
    }

    @Test
    public void patch() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .serverVersion(ServerRevision.ZERO)
                .clientVersion(ClientRevision.ZERO)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(edits(documentId, clientId, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion(), equalTo(ServerRevision.ONE));
        assertThat(shadowDocument.clientVersion(), equalTo(ClientRevision.ZERO));

        final ClientDocument<String> document = dataStore.getClientDocument(documentId, clientId);
        assertThat(document.content(), equalTo("Do or do not, there is no try!"));
    }

    @Test
    public void patchVersionAlreadyOnClient() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .serverVersion(0)
                .clientVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(edits(documentId, clientId, edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion().version(), is(1L));
        assertThat(shadowDocument.clientVersion().version(), is(0L));

        final ShadowDocument<String> shadowDocument2 = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument2.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument2.serverVersion().version(), is(1L));
        assertThat(shadowDocument2.clientVersion().version(), is(0L));
    }

    @Test
    public void patchVersionAlreadyOnServer() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .clientVersion(-1)
                .serverVersion(1)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(edits(documentId, clientId, edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion().version(), is(0L));
        assertThat(shadowDocument.clientVersion().version(), is(0L));

        final ShadowDocument<String> shadowDocument2 = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument2.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument2.serverVersion().version(), is(0L));
        assertThat(shadowDocument2.clientVersion().version(), is(0L));
    }

    @Test
    public void patchMultipleVersions() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String finalVersion = "Do or do nothing, there is no try!";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final Edit edit1 = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .clientVersion(0)
                .serverVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        final Edit edit2 = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .clientVersion(0)
                .serverVersion(1)
                .unchanged("Do or do not")
                .add("hing")
                .unchanged(", there is no try!")
                .build();
        engine.patch(edits(documentId, clientId, edit1, edit2));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo(finalVersion));
        assertThat(shadowDocument.clientVersion().version(), is(0L));
        assertThat(shadowDocument.serverVersion().version(), is(2L));

        final BackupShadowDocument<String, ClientRevision> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, shadowDocument.clientVersion());
        assertThat(backupShadowDocument.backupVersion().version(), is(0L));
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(finalVersion));
    }

    @Test
    public void patchRevertToBackup() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String finalVersion = "Do or do nothing, there is no trying";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final Edit edit1 = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .clientVersion(0)
                .serverVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(edits(documentId, clientId, edit1));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.clientVersion().version(), is(0L));
        assertThat(shadowDocument.serverVersion().version(), is(1L));

        final BackupShadowDocument<String, ClientRevision> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, shadowDocument.clientVersion());
        assertThat(backupShadowDocument.backupVersion().version(), is(0L));

        // simulate an client side diff that would update the client shadow.
        dataStore.saveShadowDocument(shadowDoc(documentId, clientId, 1L, 1L, "Do or do nothing, there is not trying"));

        final Edit edit2 = DefaultEdit.withDocumentId(documentId)
                .clientId(clientId)
                .clientVersion(0)
                .serverVersion(1)
                .unchanged("Do or do not")
                .add("hing")
                .unchanged(", there is no try")
                .delete("!")
                .add("ing")
                .build();
        engine.patch(edits(documentId, clientId, edit2));

        final ShadowDocument<String> shadowDocument2 = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument2.document().content(), equalTo(finalVersion));
        assertThat(shadowDocument2.clientVersion().version(), is(0L));
        assertThat(shadowDocument2.serverVersion().version(), is(1L));

        final BackupShadowDocument<String, ClientRevision> backupShadowDocument2 = dataStore.getBackupShadowDocument(documentId, shadowDocument.clientVersion());
        assertThat(backupShadowDocument2.backupVersion().version(), is(0L));
        assertThat(backupShadowDocument2.shadow().document().content(), equalTo(finalVersion));

        final Queue<Edit> edits = dataStore.getEdits(documentId, clientId);
        assertThat(edits.isEmpty(), is(true));
    }

    private static ClientDocument<String> clientDoc(final String docId, final String clientId, final String content) {
        return new DefaultClientDocument<String>(docId, clientId, content);
    }

    private static PatchMessage edits(final String docId, final String clientId, Edit... edit) {
        return new DefaultPatchMessage(docId, clientId, new LinkedList<Edit>(Arrays.asList(edit)));
    }

    private static ShadowDocument<String> shadowDoc(final String docId,
                                                    final String clientId,
                                                    final long serverVersion,
                                                    final long clientVersion,
                                                    final String content) {
        return new DefaultShadowDocument<String>(new ServerRevision(serverVersion), new ClientRevision(clientVersion), clientDoc(docId, clientId, content));
    }

}

