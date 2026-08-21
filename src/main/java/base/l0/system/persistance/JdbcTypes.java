package base.l0.system.persistance;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Converts what a JDBC driver actually returns into what the code wanted.
 *
 * <p>A native query hands back {@code Object}, and which class that is depends on the driver, the
 * column type, and whether the value came through a view — the MariaDB driver returns
 * {@code java.sql.Timestamp} for a TIMESTAMP in one query and {@code java.time.LocalDateTime} for
 * the same column reached another way. A direct cast therefore compiles, passes whichever test was
 * written first, and throws in the path nobody exercised.
 *
 * <p>This exists because that has now happened three times in this codebase, in three different
 * query classes, and the third one was a live endpoint with no test behind it. Native queries
 * convert temporal columns through here.
 */
public final class JdbcTypes {

    private JdbcTypes() {
    }

    public static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case LocalDate date -> date;
            case java.sql.Date date -> date.toLocalDate();
            case LocalDateTime dateTime -> dateTime.toLocalDate();
            case Timestamp timestamp -> timestamp.toLocalDateTime().toLocalDate();
            case null, default -> null;
        };
    }

    /**
     * @implNote a {@code LocalDateTime} carries no zone, and the driver produced it by reading a
     *           column the server holds in UTC and rendering it in the session's zone — so the
     *           session zone is what turns it back into an instant. {@code Timestamp} already knows.
     */
    public static Instant toInstant(Object value) {
        return switch (value) {
            case Timestamp timestamp -> timestamp.toInstant();
            case Instant instant -> instant;
            case LocalDateTime dateTime -> dateTime.atZone(ZoneId.systemDefault()).toInstant();
            case null, default -> null;
        };
    }
}

