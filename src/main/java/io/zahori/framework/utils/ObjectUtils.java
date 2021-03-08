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
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.util.List;

import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.ValueChange;

import io.zahori.framework.core.TestContext;

public class ObjectUtils {

    private ObjectUtils() {
    }

    public static String equalsNotPrimitiveTypes(TestContext testContext, Object var1, Object var2) {
        Javers javers = JaversBuilder.javers().build();
        Diff diff = javers.compare(var1, var2);

        StringBuilder resultado = new StringBuilder();

        if (diff.hasChanges()) {
            resultado = processesDifferences(testContext, diff);
        }
        return resultado.toString();
    }

    private static StringBuilder processesDifferences(TestContext testContext, Diff diff) {
        StringBuilder resultado = new StringBuilder();

        List<ValueChange> listOfChanges = diff.getChangesByType(ValueChange.class);

        final String emptyValue = "\"\"";
        for (ValueChange change : listOfChanges) {
            String left = change.getLeft() != null ? change.getLeft().toString() : emptyValue;
            String right = change.getRight() != null ? change.getRight().toString() : emptyValue;
            resultado.append(testContext.getMessage(getKeyValueOfChange(change), left, right)).append("\n");
        }
        return resultado;
    }

    private static String getKeyValueOfChange(ValueChange change) {
        return change.getAffectedGlobalId().getTypeName().toLowerCase() + "." + change.getPropertyName().toLowerCase();
    }
}
