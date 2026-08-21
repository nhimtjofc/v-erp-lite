package base.l0.system.persistance;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Recognises the one database error that is not a failure.
 *
 * <p>A find-then-insert cannot be made race-free by checking harder: two transactions can both read
 * "not there" before either writes, and the unique constraint is what actually decides. The loser's
 * exception therefore means "somebody else already did this", which for an idempotent operation is
 * success arriving by an unusual route.
 *
 * <p>Narrow on purpose. Treating every {@code PersistenceException} as a duplicate would convert a
 * genuine failure — a null in a NOT NULL column, a foreign key pointing nowhere — into a silent
 * re-read that returns whatever happens to be there.
 */
public final class DuplicateKeys {

    private DuplicateKeys() {
    }

    /** Whether {@code error}, or anything that caused it, is a unique or primary key violation. */
    public static boolean isDuplicate(Throwable error) {
        for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof SQLIntegrityConstraintViolationException violation) {
                // 23000 covers the integrity-constraint family; 1062 is MariaDB's duplicate entry.
                return violation.getErrorCode() == 1062
                        || "23000".equals(violation.getSQLState());
            }
            if (cursor instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            if (cursor.getCause() == cursor) {
                break;
            }
        }
        return false;
    }
}

