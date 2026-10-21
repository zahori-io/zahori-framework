package io.zahori.framework.core;

import io.zahori.model.process.CaseExecution;
import io.zahori.model.process.Step;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.Test;

class BaseProcessAttachmentsTest {
    private static final String EVIDENCE_PATH = "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/";
    private final BaseProcess baseProcess = new BaseProcess() {
        public void run(TestContext testContext, CaseExecution caseExecution) {
        }
    };

    @Test
    void keepsEveryAttachmentOfTheStep() {
        Step step = new Step();
        step.addAttachment(evidence("Step_9_1.jpg"));
        step.addAttachment(evidence("Step_9_1.html"));
        this.baseProcess.updateStepAttachmentUrl(step, "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/");
        ((ListAssert)Assertions.assertThat(step.getAttachments()).describedAs("el volcado del DOM se perdia al convertir las rutas", new Object[0]))
            .containsExactly(
                new String[]{"1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/Step_9_1.jpg", "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/Step_9_1.html"}
            );
    }

    @Test
    void turnsAbsolutePathsIntoEvidenceRelativeOnes() {
        Step step = new Step();
        step.addAttachment(evidence("Step_1_1.jpg"));
        this.baseProcess.updateStepAttachmentUrl(step, "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/");
        Assertions.assertThat(step.getAttachments()).containsExactly(new String[]{"1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/Step_1_1.jpg"});
    }

    @Test
    void doesNotInventAttachments() {
        Step step = new Step();
        this.baseProcess.updateStepAttachmentUrl(step, "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/");
        Assertions.assertThat(step.getAttachments()).isEmpty();
    }

    @Test
    void convertsSubstepAttachmentsToo() {
        Step substep = new Step();
        substep.addAttachment(evidence("Step_2_3.jpg"));
        Step step = new Step();
        step.addAttachment(evidence("Step_2_1.jpg"));
        step.addSubStep(substep);
        this.baseProcess.updateStepsAttachmentUrls(List.of(step), "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/");
        Assertions.assertThat(step.getAttachments()).containsExactly(new String[]{"1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/Step_2_1.jpg"});
        ((ListAssert)Assertions.assertThat(substep.getAttachments()).describedAs("el subpaso se quedaba con la ruta absoluta", new Object[0]))
            .containsExactly(new String[]{"1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/Step_2_3.jpg"});
    }

    @Test
    void toleratesAnEmptyStepList() {
        this.baseProcess.updateStepsAttachmentUrls(List.of(), "1/1/AirEuropa WEB/TCDX20/osx/SAFARI/20260921/");
    }

    // Ruta del sistema en el que corre el proceso: con C:\ fijo el test falla en Linux y macOS
    private static String evidence(String fileName) {
        return Paths.get(System.getProperty("java.io.tmpdir"), "evidencias", fileName).toString();
    }
}
