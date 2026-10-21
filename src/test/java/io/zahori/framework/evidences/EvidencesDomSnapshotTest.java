package io.zahori.framework.evidences;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EvidencesDomSnapshotTest {
    @Test
    void savesTheNativeTreeAsXmlAndTheBrowserOneAsHtml() {
        Assertions.assertEquals(".xml", Evidences.snapshotExtension(true));
        Assertions.assertEquals(".html", Evidences.snapshotExtension(false));
    }

    @Test
    void stripsScriptsFromTheBrowserDom() {
        String dom = "<body><div class='journey-summary-card'>MAD</div><script src='main.js'></script><script>var a = 1;</script></body>";
        String content = Evidences.snapshotContent(dom, false);
        Assertions.assertFalse(content.contains("<script"), "el script es lo que engorda el volcado");
        Assertions.assertTrue(content.contains("journey-summary-card"), "no puede perderse un solo selector");
        Assertions.assertTrue(content.contains("MAD"));
    }

    @Test
    void stripsMultiLineScripts() {
        String dom = "<body><button data-jto='btn'>x</button>\n<script>\nvar a = 1;\nvar b = 2;\n</script>\n</body>";
        String content = Evidences.snapshotContent(dom, false);
        Assertions.assertFalse(content.contains("var a"));
        Assertions.assertTrue(content.contains("data-jto='btn'"));
    }

    @Test
    void leavesTheNativeTreeUntouched() {
        String tree = "<XCUIElementTypeOther name='Numero de tarjeta'><XCUIElementTypeButton/></XCUIElementTypeOther>";
        Assertions.assertEquals(tree, Evidences.snapshotContent(tree, true));
    }

    @Test
    void keepsADomWithoutScriptsAsItIs() {
        String dom = "<body><refx-manage-booking-identification-cont/></body>";
        Assertions.assertEquals(dom, Evidences.snapshotContent(dom, false));
    }
}
