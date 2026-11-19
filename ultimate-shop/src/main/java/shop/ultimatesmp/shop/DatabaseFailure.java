package shop.ultimatesmp.shop;

import java.sql.SQLException;
import java.util.ArrayList;

final class DatabaseFailure {
    private DatabaseFailure() {}

    static SQLException during(String stage, SQLException error) {
        return new StageFailure(stage, error);
    }

    static String describe(Throwable error) {
        var details = new ArrayList<String>();
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            String detail = error.getClass().getSimpleName();
            if (error instanceof StageFailure failure) {
                detail += " at " + failure.stage;
            } else if (error instanceof SQLException sql) {
                String state = sql.getSQLState();
                detail += " SQL code " + sql.getErrorCode()
                        + (state != null && state.matches("[A-Z0-9]{5}") ? ", state " + state : "");
            }
            details.add(detail);
        }
        return String.join(" -> ", details);
    }

    private static final class StageFailure extends SQLException {
        private final String stage;

        private StageFailure(String stage, SQLException cause) {
            super(stage, cause.getSQLState(), cause.getErrorCode(), cause);
            this.stage = stage;
        }
    }
}
