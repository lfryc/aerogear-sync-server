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

public class ConflictException extends Exception {

    private final Document requested;
    private final Document latest;

    public ConflictException(final Document requested, final Document latest) {
        this(requested, latest, null, null);
    }

    public ConflictException(final Document requested, final Document latest, final Throwable cause) {
        this(requested, latest, null, cause);
    }

    public ConflictException(final Document document, final Document latest, final String message, final Throwable cause) {
        super(message, cause);
        this.requested = document;
        this.latest = latest;
    }

    public Document requested() {
        return requested;
    }

    public Document latest() {
        return latest;
    }


}
