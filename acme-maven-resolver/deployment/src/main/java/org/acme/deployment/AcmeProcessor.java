package org.acme.deployment;

import org.acme.MyValueProducer;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class AcmeProcessor {

    private static final String FEATURE = "acme";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
    
    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> buildProducer) {
    	buildProducer.produce(AdditionalBeanBuildItem.builder()
    			.addBeanClass(MyValueProducer.class)
    			.build());
    }
}
