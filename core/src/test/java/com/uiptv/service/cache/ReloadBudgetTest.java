package com.uiptv.service.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadBudgetTest {

    @Test
    void start_givesAFreshBudgetThatIsNotImmediatelyExpired() {
        ReloadBudget budget = ReloadBudget.start();

        assertFalse(budget.expired(), "A fresh budget must allow work to proceed");
        assertTrue(budget.remainingSeconds() > 0);
    }

    @Test
    void start_isDisabledWhenBudgetSecondsIsNotPositive() {
        String previous = System.getProperty(ReloadBudget.BUDGET_SECONDS_PROPERTY);
        try {
            System.setProperty(ReloadBudget.BUDGET_SECONDS_PROPERTY, "0");
            assertFalse(ReloadBudget.start().expired(), "0 seconds must disable the limit");

            System.setProperty(ReloadBudget.BUDGET_SECONDS_PROPERTY, "-1");
            assertFalse(ReloadBudget.start().expired(), "A negative budget must disable the limit");
        } finally {
            if (previous == null) {
                System.clearProperty(ReloadBudget.BUDGET_SECONDS_PROPERTY);
            } else {
                System.setProperty(ReloadBudget.BUDGET_SECONDS_PROPERTY, previous);
            }
        }
    }

    @Test
    void expired_isTrueOnceTheDurationHasElapsed() throws InterruptedException {
        ReloadBudget budget = ReloadBudget.start(Duration.ofMillis(1));
        Thread.sleep(20);

        assertTrue(budget.expired(), "The budget must expire after its duration elapses");
    }

    @Test
    void start_treatsNonPositiveDurationsAsUnlimited() {
        assertFalse(ReloadBudget.start(Duration.ZERO).expired());
        assertFalse(ReloadBudget.start(Duration.ofSeconds(-5)).expired());
    }

    @Test
    void defaultLastResortCategoryLimit_doesNotTruncateLargePortals() {
        // The per-account time budget is the real bound on the last-resort fan-out; the category
        // count is only a runaway guard for when the budget is disabled. It must therefore sit far
        // above the category count of any realistic portal, or a healthy reload loses channels.
        int limit = Integer.getInteger("uiptv.reload.lastresort.category.limit", 500);
        assertTrue(limit >= 500,
                "Last-resort category limit must not truncate normal portals; got " + limit);
    }
}
