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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.AbstractDriverOptions;

/**
 * Interfaz Driver que define las operaciones esenciales para los drivers en el sistema Zahori.
 * Proporciona metodos para obtener una instancia de WebDriver y configuraciones de opciones del driver.
 */
public interface Driver {

    /**
     * Obtiene una instancia de WebDriver configurada y lista para ser utilizada.
     * Este metodo debe ser implementado para proporcionar la instancia especifica de WebDriver basada en los navegadores y configuraciones proporcionadas.
     *
     * @param browsers Objeto Browsers que contiene la configuracion especifica para el driver.
     * @return Una instancia de WebDriver configurada segun los parametros proporcionados.
     */
    WebDriver getDriver(Browsers browsers);

    /**
     * Obtiene las opciones de configuracion especificas para el driver.
     * Este metodo permite la configuracion personalizada de las opciones del driver, como tiempos de espera, capacidades y mas.
     *
     * @param browsers Objeto Browsers que contiene la configuracion especifica para las opciones del driver.
     * @return Una instancia de AbstractDriverOptions configurada segun los parametros proporcionados.
     */
    AbstractDriverOptions<?> getOptions(Browsers browsers);
}
