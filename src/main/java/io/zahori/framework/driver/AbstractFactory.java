package io.zahori.framework.driver;

/*-
 * #%L
 * zahori-framework
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2021 - 2022 PANEL SISTEMAS INFORMATICOS,S.L
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

import io.zahori.framework.driver.browserfactory.Browsers;

/**
 * Interfaz generica AbstractFactory que define el contrato para las fabricas en el sistema Zahori.
 * Esta interfaz utiliza un enfoque generico para permitir una amplia variedad de implementaciones concretas.
 *
 * @param <T> El tipo de objeto que esta fabrica producira.
 */
public interface AbstractFactory<T> {

    /**
     * Metodo para crear una instancia del tipo T.
     * Este metodo debe ser implementado por las clases concretas para proporcionar la logica de creacion especifica.
     *
     * @param browsers Objeto Browsers que proporciona la configuracion necesaria para la creacion del objeto.
     * @return Una nueva instancia del tipo T.
     */
    T create(Browsers browsers);
}
