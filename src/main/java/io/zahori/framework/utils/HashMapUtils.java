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

import java.util.HashMap;
import java.util.Map;

public class HashMapUtils {

    private Map<String, Object> map;

    static final String CHAR_SPLIT = "\\.";

    public HashMapUtils() {
        this.map = new HashMap<>();
    }

    public HashMapUtils(Map<String, Object> paramMap) {
        this.map = new HashMap<>();
        this.map = paramMap;
    }

    public Object getValue(String string) {
        String[] levels = string.split(CHAR_SPLIT);

        Object object;
        if (levels.length > 1) {
            object = this.map.get(levels[0]);

            int i = 1;
            while (i < (levels.length - 1)) {
                object = ((HashMap<String, Object>) object).get(levels[i]);
                i++;
            }

            return ((HashMap<String, Object>) object).get(levels[i]);

        } else {
            if (levels.length == 1) {
                return this.map.get(string);
            } else {
                return null;
            }
        }
    }

    public Map<String, Object> getHashMap() {
        return this.map;
    }

    public void setHashMap(Map<String, Object> paramMap) {
        this.map = paramMap;
    }

}
