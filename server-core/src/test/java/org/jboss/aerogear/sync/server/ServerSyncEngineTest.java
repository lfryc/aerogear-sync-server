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

import org.jboss.aerogear.sync.*;
import org.jboss.aerogear.sync.Diff.Operation;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.Queue;

import static java.util.Arrays.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ServerSyncEngineTest {

    private ServerInMemoryDataStore dataStore;
    private ServerSyncEngine<String> engine;
    private final Subscriber<?> subscriber = mock(Subscriber.class);

    @Before
    public void setup() {
        dataStore = new ServerInMemoryDataStore();
        engine = new ServerSyncEngine<String>(new DefaultServerSynchronizer(), dataStore);
        when(subscriber.clientId()).thenReturn("client1");
    }

    @Test
    public void addDocument() {
        final String documentId = "1234";
        final PatchMessage patchMessage = engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        assertThat(patchMessage.edits().isEmpty(), is(false));
        assertThat(patchMessage.edits().peek().diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(patchMessage.edits().peek().diffs().peek().text(), is("Mr. Rosen"));
    }

    @Test
    public void addDocumentNullContentAndNoPreExistingData() {
        final String documentId = "1234";
        final PatchMessage patchMessage = engine.addSubscriber(subscriber, doc(documentId, null));
        assertThat(patchMessage.edits().isEmpty(), is(true));
    }

    @Test
    public void addDocumentNullContentWithPreExistingData() {
        final String documentId = "1234";
        engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        final PatchMessage patchMessage = engine.addSubscriber(subscriber, doc(documentId, null));
        assertThat(patchMessage.edits().isEmpty(), is(false));
        assertThat(patchMessage.edits().peek().diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(patchMessage.edits().peek().diffs().peek().text(), is("Mr. Rosen"));
    }

    @Test
    public void addDocumentWithPreExistingData() {
        final String documentId = "1234";
        engine.addSubscriber(subscriber, doc(documentId, "Mr. Rosen"));
        final PatchMessage patchMsg = engine.addSubscriber(subscriber, doc(documentId, "Some new content"));
        final Queue<Edit> edits = patchMsg.edits();
        assertThat(edits.size(), is(1));
        final LinkedList<Diff> diffs = edits.peek().diffs();
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
        assertThat(backupShadow.backupVersion(), is(0L));
        assertThat(backupShadow.shadow(), equalTo(shadowDocument));
    }

    @Test
    public void diff() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final Edit edit = engine.diff(documentId, subscriber.clientId());
        assertThat(edit.documentId(), equalTo(documentId));
        assertThat(edit.clientId(), equalTo(subscriber.clientId()));
        assertThat(edit.serverVersion(), is(0L));
        assertThat(edit.clientVersion(), is(0L));
        assertThat(edit.diffs().size(), is(1));
        assertThat(edit.diffs().peek().operation(), is(Operation.UNCHANGED));
        assertThat(edit.diffs().peek().text(), equalTo(originalVersion));
    }

    @Test
    public void patch() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String updatedVersion = "{\"name\": \"Mr.Rosen\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        engine.patch(edits(documentId, subscriber.clientId(), edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().id(), equalTo(documentId));
        assertThat(shadowDocument.document().clientId(), equalTo(subscriber.clientId()));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.document().content(), equalTo(updatedVersion));

        final BackupShadowDocument<String> backupShadow = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadow.shadow().document().content(), equalTo(updatedVersion));
        assertThat(backupShadow.backupVersion(), is(0L));
    }

    @Test
    public void patchVersionAlreadyOnServer() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String updatedVersion = "{\"name\": \"Mr.Rosen\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final Edit edit = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        engine.patch(edits(documentId, subscriber.clientId(), edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().id(), equalTo(documentId));
        assertThat(shadowDocument.document().clientId(), equalTo(subscriber.clientId()));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.document().content(), equalTo(updatedVersion));

        final BackupShadowDocument<String> backupShadow = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadow.backupVersion(), is(0L));
        assertThat(backupShadow.shadow().document().content(), equalTo(updatedVersion));
    }

    @Test
    public void patchMultipleVersions() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String secondVersion = "{\"name\": \"Mr.Poon\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final Edit edit1 = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                .clientVersion(0)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();
        final Edit edit2 = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                // after the first diff on the client, the shadow client version will have been incremented
                // and the following diff will use that shadow, hence the incremented client version here.
                .clientVersion(1)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Rosen\"")
                .add("\"Mr.Poon\"")
                .unchanged("}")
                .build();
        engine.patch(edits(documentId, subscriber.clientId(), edit1, edit2));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().content(), equalTo(secondVersion));
        assertThat(shadowDocument.clientVersion(), is(2L));
        assertThat(shadowDocument.serverVersion(), is(0L));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(secondVersion));
        assertThat(backupShadowDocument.backupVersion(), is(0L));
    }

    @Test
    public void patchRevertToBackup() {
        final String documentId = "1234";
        final String originalVersion = "{\"name\": \"Mr.Babar\"}";
        final String secondVersion = "{\"name\": \"Mr.Rosen\"}";
        final String thirdVersion = "{\"name\": \"Mr.Poon\"}";
        engine.addSubscriber(subscriber, doc(documentId, originalVersion));

        final Edit firstEdit = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                .clientVersion(0)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Babar\"")
                .add("\"Mr.Rosen\"")
                .unchanged("}")
                .build();

        engine.patch(edits(documentId, subscriber.clientId(), firstEdit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(shadowDocument.document().content(), equalTo(secondVersion));
        assertThat(shadowDocument.clientVersion(), is(1L));
        assertThat(shadowDocument.serverVersion(), is(0L));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId,
                subscriber.clientId());
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(secondVersion));
        assertThat(backupShadowDocument.backupVersion(), is(0L));

        // simulate an server side diff that would update the server side client shadow.
        dataStore.saveShadowDocument(shadowDoc(documentId, subscriber.clientId(), 1L, 1L, thirdVersion));

        final Edit secondEdit = DefaultEdit.withDocumentId(documentId)
                .clientId(subscriber.clientId())
                .clientVersion(1)
                .serverVersion(0)
                .unchanged("{\"name\": ")
                .delete("\"Mr.Rosen\"")
                .add("\"Mr.Poon\"")
                .unchanged("}")
                .build();

        engine.patch(edits(documentId, subscriber.clientId(), firstEdit, secondEdit));

        final ShadowDocument<String> secondShadow = dataStore.getShadowDocument(documentId, subscriber.clientId());
        assertThat(secondShadow.document().content(), equalTo(thirdVersion));
        // client version would have been incremented on the client side during the post diff processing.
        assertThat(secondShadow.clientVersion(), is(2L));
        assertThat(secondShadow.serverVersion(), is(0L));

        final Queue<Edit> edits = dataStore.getEdits(documentId, subscriber.clientId());
        assertThat(edits.isEmpty(), is(true));
    }

    private static PatchMessage edits(final String docId, final String clientId, Edit... edit) {
        return new DefaultPatchMessage(docId, clientId, new LinkedList<Edit>(asList(edit)));
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
