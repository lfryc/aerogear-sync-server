package org.jboss.aerogear.sync;

public class DefaultBackupShadowDocument<T, BackupRevision> implements BackupShadowDocument<T, BackupRevision> {

    private final BackupRevision version;
    private final ShadowDocument<T> shadow;

    public DefaultBackupShadowDocument(final BackupRevision version, final ShadowDocument<T> shadow) {
        this.version = version;
        this.shadow = shadow;
    }

    @Override
    public BackupRevision backupVersion() {
        return version;
    }

    @Override
    public ShadowDocument<T> shadow() {
        return shadow;
    }
}
