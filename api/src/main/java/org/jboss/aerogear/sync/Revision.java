package org.jboss.aerogear.sync;

public interface Revision<T> {

    String version();

    T increment(Document<?> document);
}
