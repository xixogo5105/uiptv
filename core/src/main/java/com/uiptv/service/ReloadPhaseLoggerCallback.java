package com.uiptv.service;

import com.uiptv.model.Account;

/**
 * Receives cache-reload progress messages together with the reload phase that produced them.
 *
 * <p>A single account reload walks several phases: live TV, then VOD categories, then series
 * categories. The phase is reported explicitly rather than being read back from the
 * {@link Account} instance, because that instance is owned by the caller and must not be mutated
 * by the reload. Consumers that need to label a message (for example "VOD Categories" versus
 * "Live TV Categories") use the phase passed in instead of inspecting shared account state.
 */
@FunctionalInterface
public interface ReloadPhaseLoggerCallback {

    /**
     * @param message the raw reload progress message
     * @param phase   the account action in effect when the message was produced; may be null
     */
    void log(String message, Account.AccountAction phase);
}
