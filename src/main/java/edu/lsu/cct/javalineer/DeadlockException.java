package edu.lsu.cct.javalineer;

/**
 * Thrown by {@link Pool#join} when the pool has gone idle while the awaited future is still incomplete: no task is
 * left that could complete it, so waiting would hang forever. The report (also written to stderr) describes the
 * pending state.
 */
public class DeadlockException extends IllegalStateException {
    private final String report;

    DeadlockException(String report) {
        super("Javalineer: deadlock: the pool is idle and the awaited future can never complete (report on stderr)");
        this.report = report;
    }

    /**
     * @return The report of the pending state, as written to stderr.
     */
    public String getReport() {
        return report;
    }
}
