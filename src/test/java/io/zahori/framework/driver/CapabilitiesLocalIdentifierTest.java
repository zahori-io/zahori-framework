package io.zahori.framework.driver;

import io.zahori.framework.driver.browserfactory.Browsers;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class CapabilitiesLocalIdentifierTest {
    private static final String TUNNEL_ID = "294-301";
    private static final String KEY = "bstack:options.localIdentifier";

    private Browsers browsersWithTunnel(String tunnelId) {
        return new Browsers().withTunnelLocalIdentifier(tunnelId);
    }

    @Test
    void theSessionUsesTheLiveTunnelIdentifier() {
        String value = CapabilitiesBuilder.localIdentifierOf(this.browsersWithTunnel("294-301"), "bstack:options.localIdentifier", "294-999");
        ((AbstractStringAssert)Assertions.assertThat(value).describedAs("la sesion pedia el id del caso y el tunel era otro", new Object[0]))
            .isEqualTo("294-301");
    }

    @Test
    void keepsTheConfiguredValueWhenThereIsNoTunnel() {
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(this.browsersWithTunnel(null), "bstack:options.localIdentifier", "294-999"))
            .isEqualTo("294-999");
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(this.browsersWithTunnel("  "), "bstack:options.localIdentifier", "294-999"))
            .isEqualTo("294-999");
    }

    @Test
    void doesNotAlterAnyOtherCapability() {
        Browsers browsers = this.browsersWithTunnel("294-301");
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(browsers, "bstack:options.sessionName", "294-999")).isEqualTo("294-999");
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(browsers, "bstack:options.buildName", "android [294]")).isEqualTo("android [294]");
    }

    @Test
    void recognisesTheKeyWhateverItsPrefix() {
        Browsers browsers = this.browsersWithTunnel("294-301");
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(browsers, "android.bstack:options.localIdentifier", "x")).isEqualTo("294-301");
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(browsers, "localidentifier", "x")).isEqualTo("294-301");
    }

    @Test
    void toleratesAMissingBrowsers() {
        Assertions.assertThat(CapabilitiesBuilder.localIdentifierOf(null, "bstack:options.localIdentifier", "294-999")).isEqualTo("294-999");
    }
}
