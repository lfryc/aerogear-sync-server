package org.jboss.aerogear.sync;

public interface Revision<T> {

    long version();

    T increment();
}
