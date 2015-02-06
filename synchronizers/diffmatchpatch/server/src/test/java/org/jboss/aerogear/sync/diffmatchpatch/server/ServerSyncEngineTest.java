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
package org.jboss.aerogear.sync.diffmatchpatch.server;

import org.jboss.aerogear.sync.BackupShadowDocument;
import org.jboss.aerogear.sync.ClientDocument;
import org.jboss.aerogear.sync.DefaultClientDocument;
import org.jboss.aerogear.sync.DefaultDocument;
import org.jboss.aerogear.sync.DefaultShadowDocument;
import org.jboss.aerogear.sync.Document;
import org.jboss.aerogear.sync.PatchMessage;
import org.jboss.aerogear.sync.ShadowDocument;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchDiff;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchDiff.Operation;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchEdit;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchMessage;
import org.jboss.aerogear.sync.server.ServerInMemoryDataStore;
import org.jboss.aerogear.sync.server.ServerSyncEngine;
import org.jboss.aerogear.sync.server.Subscriber;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.Queue;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerSyncEngineTest {

    private ServerInMemoryDataStore<String, DiffMatchPatchEdit> dataStore;
    private ServerSyncEngine<String, DiffMatchPatchEdit> engine;
    private final Subscriber<String> subscriber = mock(Subscriber.class);

    @Before
    public void setup() {
        dataStore = new ServerInMemoryDataStore<String, DiffMatchPatchEdit>();
        engine = new ServerSyncEngine<String, DiffMatchPatchEdit>(new DiffMatchPatchServerSynchronizer(), dataStore);
        when(subscriber.clientId()).thenReturn("client1");
    }

    @Test
    public void addDocument() {
        final String documentId = "1234";
        final PatchMessage<DiffMatchPatchEdit> patchMessage = engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        assertThat(patchMessage.edits().isEmpty(), is(false));
        assertThat(patchMessage.edits().peek().diff().diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(patchMessage.edits().peek().diff().diffs().peek().text(), is("Mr. Rosen"));
    }

    @Test
    public void addDocumentNullContentAndNoPreExistingData() {
        final String documentId = "1234";
        final PatchMessage<DiffMatchPatchEdit> patchMessage = engine.addSubscriber(subscriber, doc(documentId, null));
        assertThat(patchMessage.edits().isEmpty(), is(true));
    }

    @Test
    public void addDocumentNullContentWithPreExistingData() {
        final String documentId = "1234";
        engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        final PatchMessage<DiffMatchPatchEdit> patchMessage = engine.addSubscriber(subscriber, doc(documentId, null));
        assertThat(patchMessage.edits().isEmpty(), is(false));
        assertThat(patchMessage.edits().peek().diff().diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(patchMessage.edits().peek().diff().diffs().peek().text(), is("Mr. Rosen"));
    }

    @Test
    public void addDocumentWithPreExistingData() {
        final String documentId = "1234";
        engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        final PatchMessage<DiffMatchPatchEdit> patchMsg = engine.addSubscriber(subscriber, doc(documentId, "Some new content"));
        final Queue<DiffMatchPatchEdit> edits = patchMsg.edits();
        assertThat(edits.size(), is(1));
        final LinkedList<DiffMatchPatchDiff> diffs = edits.peek().diff().diffs();
        assertThat(diffs.get(0).operation(), is(Operation.DELETE));
        assertThat(diffs.get(0).text(), is("Some"));
        assertThat(diffs.get(1).operation(), is(Operation.ADD));
        assertThat(diffs.get(1).text(), is("Mr."));
        assertThat(diffs.get(2).operation(), is(Operation.UNCHANGED));
        assertThat(diffs.get(2).text(), is(" "));
        assertThat(diffs.get(3).operation(), is(Operation.DELETE));
        assertThat(diffs.get(3).text(), is("new c"));
        assertThat(diffs.get(4).operation(), is(Operation.ADD));
        assertThat(diffs.get(4).text(), is("R"));
        assertThat(diffs.get(5).operation(), is(Operation.UNCHANGED));
        assertThat(diffs.get(5).text(), is("o"));
        assertThat(diffs.get(6).operation(), is(Operation.DELETE));
        assertThat(diffs.get(6).text(), is("nt"));
        assertThat(diffs.get(7).operation(), is(Operation.ADD));
        assertThat(diffs.get(7).text(), is("s"));
        assertThat(diffs.get(8).operation(), is(Operation.UNCHANGED));
        assertThat(diffs.get(8).text(), is("en"));
        assertThat(diffs.get(9).operation(), is(Operation.DELETE));
        assertThat(diffs.get(9).text(), is("t"));
    }

    @Test
    public void addDocumentVerifyShadows() throws Exception {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().id(), equalTo(documentId));
        assertThat(shadowDocument.document().clientId(), equalTo(clientId));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(0L));
        assertThat(shadowDocument.document().content(), equalTo(originalVersion));

        final BackupShadowDocument<String> backupShadow = dataStore.getBackupShadowDocument(documentId, clientId);
        assertThat(backupShadow.version(), is(0L));
        assertThat(backupShadow.shadow(), equalTo(shadowDocument));
    }

    @Test
    public void diff() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final DiffMatchPatchEdit edit = engine.diff(documentId, subscriber.clientId());
        assertThat(edit.serverVersion(), is(0L));
        assertThat(edit.clientVersion(), is(0L));
        assertThat(edit.diff().diffs().size(), is(1));
        assertThat(edit.diff().diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(edit.diff().diffs().peek().text(), equalTo(originalVersion));
    }

    @Test
    public void patch() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String updatedVersion = "{\"name\": \"Mr.Rosen\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final DiffMatchPatchEdit edit = DiffMatchPatchEdit.withChecksum("bogus")
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        engine.patch(patchMessage(documentId, subscriber.clientId(), edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().id(), equalTo(documentId));
        assertThat(shadowDocument.document().clientId(), equalTo(subscriber.clientId()));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.document().content(), equalTo(updatedVersion));

        final BackupShadowDocument<String> backupShadow = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadow.shadow().document().content(), equalTo(updatedVersion));
        assertThat(backupShadow.version(), is(0L));
    }

    @Test
    public void patchVersionAlreadyOnServer() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String updatedVersion = "{\"name\": \"Mr.Rosen\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final DiffMatchPatchEdit edit = DiffMatchPatchEdit.withChecksum("bogus")
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        engine.patch(patchMessage(documentId, subscriber.clientId(), edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().id(), equalTo(documentId));
        assertThat(shadowDocument.document().clientId(), equalTo(subscriber.clientId()));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.document().content(), equalTo(updatedVersion));

        final BackupShadowDocument<String> backupShadow = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadow.version(), is(0L));
        assertThat(backupShadow.shadow().document().content(), equalTo(updatedVersion));
    }

    @Test
    public void patchMultipleVersions() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String secondVersion = "{\"name\": \"Mr.Poon\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final DiffMatchPatchEdit edit1 = DiffMatchPatchEdit.withChecksum("bogus")
                .clientVersion(0)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        final DiffMatchPatchEdit edit2 = DiffMatchPatchEdit.withChecksum("bogus")
                // after the first diff on the client, the shadow client version will have been incremented
                // and the following diff will use that shadow, hence the incremented client version here.
                .clientVersion(1)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Rosen\"")
                .add("\"Mr.Poon\"")
                .unchanged("}")
                .build();
        engine.patch(patchMessage(documentId, subscriber.clientId(), edit1, edit2));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().content(), equalTo(secondVersion));
        assertThat(shadowDocument.clientVersion(), is(2L));
        assertThat(shadowDocument.serverVersion(), is(0L));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(secondVersion));
        assertThat(backupShadowDocument.version(), is(0L));
    }

    @Test
    public void patchRevertToBackup() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\", \"age\": \"30\"}";
        final String secondVersion = "{\"name\": \"Mr.Rosen\", \"age\": \"30\"}";
        final String serverChangedVersion = "{\"name\": \"Mr.RosenRosen\", \"age\": \"35\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final DiffMatchPatchEdit firstEdit = DiffMatchPatchEdit.withChecksum("bogus")
                .clientVersion(0)  // this patch was based on client version 0
                .serverVersion(0)  // this patch was based on server version 0
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged(", \"age\": \"30\"}")
                .build();

        engine.patch(patchMessage(documentId, subscriber.clientId(), firstEdit));

        final Document<String> document = dataStore.getDocument(documentId);
        assertThat(document.content(), equalTo(secondVersion));
        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().content(), equalTo(secondVersion));
        // client version is updated when the patch is applied to the server side shadow. This will have happend on
        // the client side after the diff was taken. So both client and server are now identical.
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.serverVersion(), is(0L));
        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(secondVersion));
        assertThat(backupShadowDocument.version(), is(0L));

        // simulate an server side diff that would update the shadow by incrementing the server version. The situation
        // here would be that the server has done a diff, which increments the shadows server version, but the patch
        // message was dropped somewhere on route to the client.
        // first, store the new updates to the server document.
        dataStore.updateDocument(doc(documentId, serverChangedVersion));
        assertThat(dataStore.getDocument(documentId).content(), equalTo(serverChangedVersion));
        // next, store the updated shadow document for the client.
        dataStore.saveShadowDocument(shadowDoc(documentId, subscriber.clientId(), 1L, 1L, serverChangedVersion));
        assertThat(dataStore.getShadowDocument(documentId, subscriber.clientId()).document().content(), equalTo(serverChangedVersion));

        final DiffMatchPatchEdit secondEdit = DiffMatchPatchEdit.withChecksum("bogus")
                // this is to simulate an earlier version coming from the client, which means that the client never
                // got version 1 that the server sent. Remember that we are simulating this using the previous
                // saveShadowDocument call above which set the server version to 1.
                .serverVersion(0) // this edit is based on server version 0
                .clientVersion(1)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Poon\"")
                .unchanged(", \"age\": \"30\"}")
                .build();

        engine.patch(patchMessage(documentId, subscriber.clientId(), firstEdit, secondEdit));

        final ShadowDocument<String> patchedShadow = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(patchedShadow.document().content(), equalTo(serverChangedVersion));
        // a patch on the shadow will increment the client version so that it matches the shadow on the client. Remember
        // on the client side the shadow version is updated after the diff is taken.
        assertThat(patchedShadow.clientVersion(), is(2L));
        assertThat(patchedShadow.serverVersion(), is(0L));

        // the last step of the patch process is to copy the the shadow to the backup
        final BackupShadowDocument<String> patchedBackupShadow = dataStore.getBackupShadowDocument(documentId, subscriber.clientId());
        assertThat(patchedBackupShadow.version(), is(0L));
        assertThat(patchedBackupShadow.shadow(), equalTo(patchedShadow));

        // the edit stack should be cleared now as we have reverted to a previous version.
        final Queue<DiffMatchPatchEdit> edits = dataStore.getEdits(documentId, subscriber.clientId());
        assertThat(edits.isEmpty(), is(true));

        // when a client document is restored the servers version will be prefered and the changes from the client
        // diffed against it.
        final Document<String> finalDocument = dataStore.getDocument(documentId);
        assertThat(finalDocument.content(), equalTo(serverChangedVersion));
    }

    private static PatchMessage<DiffMatchPatchEdit> patchMessage(final String docId, final String clientId, DiffMatchPatchEdit... edit) {
        return new DiffMatchPatchMessage(docId, clientId, new LinkedList<DiffMatchPatchEdit>(asList(edit)));
    }

    private static ShadowDocument<String> shadowDoc(final String docId,
                                                    final String clientId,
                                                    final long serverVersion,
                                                    final long clientVersion,
                                                    final String content) {
        return new DefaultShadowDocument<String>(serverVersion, clientVersion, clientDoc(docId, clientId, content));
    }

    private static ClientDocument<String> clientDoc(final String docId, final String clientId, final String content) {
        return new DefaultClientDocument<String>(docId, clientId, content);
    }

    private static Document<String> doc(final String docId, final String content) {
        return new DefaultDocument<String>(docId, content);
    }

}
