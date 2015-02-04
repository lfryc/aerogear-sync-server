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

import org.jboss.aerogear.sync.*;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchDiff;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchDiff.Operation;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchEdit;
import org.jboss.aerogear.sync.diffmatchpatch.DiffMatchPatchMessage;
import org.jboss.aerogear.sync.diffmatchpatch.client.DiffMatchPatchClientSynchronizer;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ClientSyncEngineTest {

    private ClientSyncEngine<String, DiffMatchPatchEdit> engine;
    private ClientInMemoryDataStore<String, DiffMatchPatchEdit> dataStore;

    @Before
    public void setup() {
        dataStore = new ClientInMemoryDataStore<String, DiffMatchPatchEdit>();
        engine = new ClientSyncEngine<String, DiffMatchPatchEdit>(new DiffMatchPatchClientSynchronizer(), dataStore);
    }

    @Test
    public void addDocument() throws Exception {
        final String documentId = "1234";
        final String clientId = "client2";
        final String originalVersion = "{\"id\": 9999}";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(0L));
        assertThat(shadowDocument.document().content(), equalTo(originalVersion));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, clientId);
        assertThat(backupShadowDocument.version(), is(0L));
        assertThat(backupShadowDocument.shadow(), equalTo(shadowDocument));
    }
    
    @Test
    public void diff() {
        final String documentId = "1234";
        final String clientId = "client2";
        final String updatedVersion = "{\"id\": 9999}";
        final String shadowVersion = "{\"id\": 6666}";
        engine.addDocument(clientDoc(documentId, clientId, updatedVersion));

        final PatchMessage<DiffMatchPatchEdit> patchMessage = engine.diff(clientDoc(documentId, clientId, shadowVersion));
        assertThat(patchMessage.documentId(), equalTo(documentId));
        assertThat(patchMessage.clientId(), equalTo(clientId));
        assertThat(patchMessage.edits().size(), is(1));
        final DiffMatchPatchEdit edit = patchMessage.edits().peek();
        assertThat(edit.diff().diffs().size(), is(4));
        final LinkedList<DiffMatchPatchDiff> diffs = edit.diff().diffs();
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

        final PatchMessage<DiffMatchPatchEdit> patchMessage = patchMessage(documentId, clientId,
                DiffMatchPatchEdit.withChecksum("bogus")
                .serverVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build());
        engine.patch(patchMessage);

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion(), is(1L));
        assertThat(shadowDocument.clientVersion(), is(0L));

        final ClientDocument<String> document = dataStore.getClientDocument(documentId, clientId);
        assertThat(document.content(), equalTo("Do or do not, there is no try!"));
    }

    @Test
    public void patchVersionAlreadyOnClient() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final DiffMatchPatchEdit edit = DiffMatchPatchEdit.withChecksum("bogus")
                .serverVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(patchMessage(documentId, clientId, edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion(), is(1L));
        assertThat(shadowDocument.clientVersion(), is(0L));

        final ShadowDocument<String> shadowDocument2 = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument2.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument2.serverVersion(), is(1L));
        assertThat(shadowDocument2.clientVersion(), is(0L));
    }
    
    @Test
    public void patchVersionAlreadyOnServer() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final DiffMatchPatchEdit edit = DiffMatchPatchEdit.withChecksum("bogus")
                .clientVersion(-1)
                .serverVersion(1)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        engine.patch(patchMessage(documentId, clientId, edit, edit));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument.serverVersion(), is(0L));
        assertThat(shadowDocument.clientVersion(), is(0L));

        final ShadowDocument<String> shadowDocument2 = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument2.document().content(), equalTo("Do or do not, there is no try!"));
        assertThat(shadowDocument2.serverVersion(), is(0L));
        assertThat(shadowDocument2.clientVersion(), is(0L));
    }

    @Test
    public void patchMultipleVersions() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String finalVersion = "Do or do nothing, there is no try!";
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final DiffMatchPatchEdit edit1 = DiffMatchPatchEdit.withChecksum("bogus")
                .serverVersion(0)
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build();
        final DiffMatchPatchEdit edit2 = DiffMatchPatchEdit.withChecksum("bogus")
                .serverVersion(1)
                .unchanged("Do or do not")
                .add("hing")
                .unchanged(", there is no try!")
                .build();
        engine.patch(patchMessage(documentId, clientId, edit1, edit2));

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo(finalVersion));
        assertThat(shadowDocument.clientVersion(), is(0L));
        assertThat(shadowDocument.serverVersion(), is(2L));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, clientId);
        assertThat(backupShadowDocument.version(), is(0L));
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(finalVersion));
    }

    @Test
    public void patchRevertToBackup() {
        final String documentId = "1234";
        final String clientId = "client1";
        final String originalVersion = "Do or do not, there is no try.";
        final String secondVersion = "Do or do not, there is no try!";
        final String finalVersion = "Do or do nothing, there is no trying";

        // add the original document to the client engine. This will create a shadow document with client version 0,
        // and server version 0.
        engine.addDocument(clientDoc(documentId, clientId, originalVersion));

        final PatchMessage<DiffMatchPatchEdit> serverPatch = patchMessage(documentId, clientId,
                DiffMatchPatchEdit.withChecksum("bogus")
                .clientVersion(0)  // this patch was based on client version 0
                .serverVersion(0)  // this patch was based on server version 0
                .unchanged("Do or do not, there is no try")
                .delete(".")
                .add("!")
                .build());

        engine.patch(serverPatch);

        final ShadowDocument<String> shadowDocument = dataStore.getShadowDocument(documentId, clientId);
        assertThat(shadowDocument.document().content(), equalTo(secondVersion));
        // server version is incremented when the client shadow is patched
        assertThat(shadowDocument.serverVersion(), is(1L));
        // the client version is not updated until after a diff is taken, that is when the client is sending a patch
        // to the server, is taken so it will still be 0
        assertThat(shadowDocument.clientVersion(), is(0L));

        final BackupShadowDocument<String> backupShadowDocument = dataStore.getBackupShadowDocument(documentId, clientId);
        assertThat(backupShadowDocument.shadow().document().content(), equalTo(secondVersion));
        // the backup shadow on the client will still be 0 since this was the client version that the server handed
        assertThat(backupShadowDocument.version(), is(0L));

        // simulate an client side diff that would update the client shadow. The situation here would be that
        // the client has done a diff, which increments the shadows client version, but the patch message was dropped
        // some where on route to the server.
        dataStore.saveShadowDocument(shadowDoc(documentId, clientId, 1L, 1L, finalVersion));

        final PatchMessage<DiffMatchPatchEdit> serverPatch2 = patchMessage(documentId, clientId,
                DiffMatchPatchEdit.withChecksum("bogus")
                        // this is to simulate an earlier version coming from the server, which means that the server never
                        // got version 1 that the client sent. Remember that we are simulating this using the previous
                        // saveShadowDocument call above which set the client version to 1.
                        .clientVersion(0)
                        // this patch was based on server version 1. After this PatchMessage is sent this version would
                        // have been incremented on the server side. We have to take this into accound when asserting
                        // the server version after performing the patch operation.
                        .serverVersion(1)
                        .unchanged("Do or do not")
                        .add("hing")
                        .unchanged(", there is no try")
                        .delete("!")
                        .add("ing")
                        .build());

        // patch should now revert to version 0 and apply the edits
        engine.patch(serverPatch2);

        final ClientDocument<String> patchedDocument = dataStore.getClientDocument(documentId, clientId);
        assertThat(patchedDocument.content(), equalTo(finalVersion));

        final ShadowDocument<String> patchedShadow = dataStore.getShadowDocument(documentId, clientId);
        assertThat(patchedShadow.document().content(), equalTo(finalVersion));
        assertThat(patchedShadow.clientVersion(), is(0L));
        // server version should have been incremented since it would have been incremented on the server side after
        // the diff was taken and the PatchMessage sent over the wire.
        assertThat(patchedShadow.serverVersion(), is(2L));

        // the last step of the patch process is to copy the the shadow to the backup
        final BackupShadowDocument<String> patchedBackupShadow = dataStore.getBackupShadowDocument(documentId, clientId);
        assertThat(patchedBackupShadow.version(), is(0L));
        assertThat(patchedBackupShadow.shadow(), equalTo(patchedShadow));

        // the edit stack should be cleared now as we have reverted to a previous version. The client doc has been
        // updated and any new edits will be done on that version, and new patches sent to the server based on it.
        final Queue<DiffMatchPatchEdit> edits = dataStore.getEdits(documentId, clientId);
        assertThat(edits.isEmpty(), is(true));
    }

    private static ClientDocument<String> clientDoc(final String docId, final String clientId, final String content) {
        return new DefaultClientDocument<String>(docId, clientId, content);
    }

    private static PatchMessage<DiffMatchPatchEdit> patchMessage(final String docId, final String clientId, DiffMatchPatchEdit... edit) {
        return new DiffMatchPatchMessage(docId, clientId, new LinkedList<DiffMatchPatchEdit>(Arrays.asList(edit)));
    }

    private static ShadowDocument<String> shadowDoc(final String docId,
                                                    final String clientId,
                                                    final long serverVersion,
                                                    final long clientVersion,
                                                    final String content) {
        return new DefaultShadowDocument<String>(serverVersion, clientVersion, clientDoc(docId, clientId, content));
    }

}

