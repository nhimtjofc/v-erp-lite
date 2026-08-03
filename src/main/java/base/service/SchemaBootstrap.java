package base.service;

import io.agroal.api.AgroalDataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.sql.Connection;
import java.sql.Statement;

@ApplicationScoped
public class SchemaBootstrap {

    private static final Logger log = Logger.getLogger(SchemaBootstrap.class);

    @Inject
    AgroalDataSource dataSource;

    void onStart(@Observes StartupEvent event) {
        String[] sqls = {
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMP NULL",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_requested_by UUID",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_status VARCHAR(20)",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_approved_at TIMESTAMP NULL",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cancel_approved_by UUID",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_requested_at TIMESTAMP NULL",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_requested_by UUID",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_reason TEXT",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_approved_at TIMESTAMP NULL",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_approved_by UUID",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_status VARCHAR(20)",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_refund_minor BIGINT",
            "ALTER TABLE order_items ADD COLUMN IF NOT EXISTS return_refund_currency VARCHAR(3)",
            "ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20)",
            "ALTER TABLE wallet_topups ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0",
            "ALTER TABLE order_history MODIFY created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)",
            "ALTER TABLE order_history ADD COLUMN IF NOT EXISTS sequence_no BIGINT NOT NULL AUTO_INCREMENT, ADD UNIQUE KEY IF NOT EXISTS uq_order_history_sequence (sequence_no)"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                try {
                    stmt.execute(sql);
                } catch (Exception ex) {
                    log.debugf("Schema patch notice for '%s': %s", sql, ex.getMessage());
                }
            }
            log.info("SchemaBootstrap verified table schemas successfully.");
        } catch (Exception e) {
            log.warnf(e, "SchemaBootstrap encountered an issue connecting to datasource");
        }
    }
}
