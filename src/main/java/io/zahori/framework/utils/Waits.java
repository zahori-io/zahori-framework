package io.zahori.framework.utils;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 PANEL SISTEMAS INFORMATICOS,S.L
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/agpl-3.0.html>.
 * #L%
 */

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.openqa.selenium.support.ui.FluentWait;

/**
 * Waits for a condition that is not "an element appears" — several elements at once, a state read
 * with javascript, an application invariant.
 *
 * <p>{@code PageElement} covers the element-scoped waits. Everything else was being hand rolled by
 * the consumer projects as {@code while (...) { Pause.pause(); }} with a {@link Chronometer}, which
 * is where the uncapped loop, the cap that is never checked and the exception read as "condition
 * met" keep coming from.
 *
 * <p>Polling is delegated to Selenium's {@link FluentWait} instead of being written here, so there
 * is no sleep of our own to get wrong.
 */
public final class Waits {

    private static final long POLLING_MILLIS = 200L;

    private Waits() {
    }

    /**
     * Waits until a condition holds.
     *
     * <p>An exception thrown by the condition counts as "not yet" and the wait keeps trying, which
     * is the behaviour a transient failure needs — a javascript evaluation during a navigation, a
     * stale element mid-repaint. It never throws: the caller decides what an expired wait means.
     *
     * @param condition         the condition to wait for
     * @param maxSecondsWaiting how long to wait before giving up
     * @return {@code true} if the condition held before the timeout, {@code false} otherwise
     */
    public static boolean until(BooleanSupplier condition, int maxSecondsWaiting) {
        try {
            new FluentWait<>(condition)
                    .withTimeout(Duration.ofSeconds(maxSecondsWaiting))
                    .pollingEvery(Duration.ofMillis(POLLING_MILLIS))
                    .ignoring(Exception.class)
                    .until(BooleanSupplier::getAsBoolean);
            return true;
        } catch (final Exception e) {
            return false;
        }
    }
}
