package net.onelitefeather.skygridng.persistence;

/**
 * Records that the async database warm-up has finished or failed. Schedulers that touch the databases wait for it,
 * so the first database call of a season tick never runs the Flyway migration of a cold pool. Set once per enable.
 */
public final class DatabaseWarmUpGate {

    private volatile boolean warmedUp;

    public void markWarmedUp() {
        warmedUp = true;
    }

    public boolean isWarmedUp() {
        return warmedUp;
    }
}
