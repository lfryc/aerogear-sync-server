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

/**
 * A shadow document for each client will exist on the client side and also on the server side.
 *
 * A shadow document is update after a successful patch/merge as been performed. A patch/merge
 * is done with specific versions on the client and server document which are stored by instances
 * of this class.
 *
 * @param <T> The type of the Document that this instance shadows.
 */
public interface ShadowDocument<T> {

    /**
     * Represents the latest server version that the this shadow document was based on.
     *
     * @return {@code long} the server version.
     */
    ServerRevision serverVersion();

    /**
     * Represents the latest client version that this shadow document was based on.
     *
     * @return {@code long} the client version.
     */
    ClientRevision clientVersion();

    /**
     * The document itself.
     *
     * @return T the document.
     */
    ClientDocument<T> document();

}
