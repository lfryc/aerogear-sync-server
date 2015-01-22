package org.jboss.aerogear.sync.server;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Iterator;
import java.util.Queue;

import org.jboss.aerogear.sync.DefaultEdit;
import org.jboss.aerogear.sync.Edit;
import org.junit.Test;

public class ServerInMemoryDataStoreTest {

    @Test
    public void getEdits() {
        final String documentId = "12345";
        final String clientId = "client1";
        final ServerInMemoryDataStore dataStore = new ServerInMemoryDataStore();
        final Edit editOne = DefaultEdit.withDocumentId(documentId).clientId(clientId).clientVersion(0).build();
        final Edit editTwo = DefaultEdit.withDocumentId(documentId).clientId(clientId).clientVersion(1).build();
        dataStore.saveEdits(editOne);
        dataStore.saveEdits(editTwo);
        final Queue<Edit> edits = dataStore.getEdits(documentId, clientId);
        assertThat(edits.size(), is(2));
        final Iterator<Edit> iterator = edits.iterator();
        assertThat(iterator.next().clientVersion().version(), is(0L));
        assertThat(iterator.next().clientVersion().version(), is(1L));
    }
}
