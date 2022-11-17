package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;

public interface AbstractFactory<T> {
    T create(Browsers browsers) ;
}
