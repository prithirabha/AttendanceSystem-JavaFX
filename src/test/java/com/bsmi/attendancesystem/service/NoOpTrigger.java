package com.bsmi.attendancesystem.service;

import java.sql.Connection;
import org.h2.api.Trigger;

/**
 * A do-nothing trigger, used only to put a trigger into a test database so {@link BackupTest} can
 * check that a restored database carrying one is refused. The restore inspection counts triggers
 * without ever running them, so this class is never actually fired during the test.
 */
public class NoOpTrigger implements Trigger {
    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) {}
}
