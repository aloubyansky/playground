package org.acme.quarkus.extension.deployment;

import org.acme.CommonBean;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class AcmeQuarkusExtensionProcessor {

    private static final String FEATURE = "acme-quarkus-extension";

    @BuildStep
    FeatureBuildItem feature() {
        CommonBean.common("arg");
        return new FeatureBuildItem(FEATURE);
    }

}
