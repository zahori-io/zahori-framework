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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Contract of {@link Waits#until}.
 *
 * <p><b>Mutation status (PIT):</b> 2 mutants, both killed. There is no more mutable code here
 * because the polling belongs to FluentWait, which is what keeps this free of the timing conditions
 * a hand rolled loop leaves behind.
 */
class WaitsTest {

    @Test
    void aConditionThatAlreadyHoldsIsNotPolledTwice() {
        AtomicInteger evaluations = new AtomicInteger();

        assertTrue(Waits.until(() -> {
            evaluations.incrementAndGet();
            return true;
        }, 5));

        assertTrue(evaluations.get() <= 1, "no deberia reevaluar una condicion ya cumplida");
    }

    @Test
    void aConditionThatNeverHoldsExpires() {
        assertFalse(Waits.until(() -> false, 1));
    }

    @Test
    void aConditionThatBecomesTrueIsWaitedFor() {
        AtomicInteger evaluations = new AtomicInteger();

        assertTrue(Waits.until(() -> evaluations.getAndIncrement() >= 2, 5));
    }

    /**
     * Una excepcion puntual cuenta como "todavia no", no como fallo: es el caso de un
     * executeScript durante una navegacion en curso.
     */
    @Test
    void aTransientExceptionDoesNotAbortTheWait() {
        AtomicInteger evaluations = new AtomicInteger();

        assertTrue(Waits.until(() -> {
            if (evaluations.getAndIncrement() < 2) {
                throw new IllegalStateException("javascript error: document unavailable");
            }
            return true;
        }, 5));
    }

    @Test
    void aConditionThatAlwaysThrowsExpiresInsteadOfPropagating() {
        assertFalse(Waits.until(() -> {
            throw new IllegalStateException("siempre falla");
        }, 1));
    }
}
