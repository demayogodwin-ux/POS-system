package com.pos.db;

import java.sql.SQLException;

public final class DbErrors {
    private static final String UNIQUE_VIOLATION = "23505";

    private DbErrors() {
    }

    public static boolean isDuplicateKey(Throwable error) {
        return error instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState());
    }
}
