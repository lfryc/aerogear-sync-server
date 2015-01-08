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
 * A backup of the ShadowDocument.
 *
 * @param <T> The type of the Document that this instance backups.
 */
public interface BackupShadowDocument<T> {

    /**
     * Represents the version of this backup shadow.
     *
     * @return {@code long} the server version.
     */
    long backupVersion();

    /**
     * The {@link ShadowDocument} that this instance is backing up.
     *
     * @return {@link ShadowDocument} that this instance is backing up.
     */
    ShadowDocument<T> shadow();

}
