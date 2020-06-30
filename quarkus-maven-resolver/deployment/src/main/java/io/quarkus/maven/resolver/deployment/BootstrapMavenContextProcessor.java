package io.quarkus.maven.resolver.deployment;


import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.maven.resolver.MavenRepositorySystemProducer;
import io.quarkus.maven.resolver.BootstrapMavenContextProducer;
import io.quarkus.maven.resolver.BootstrapMavenContextRecorder;

class BootstrapMavenContextProcessor {

    private static final String FEATURE = "maven-resolver";

    @BuildStep
    FeatureBuildItem feature() {
    	System.out.println("QuarkusMavenResolverProcessor.feature");
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
    	System.out.println("QuarkusMavenResolverProcessor.registerBeans");
    	additionalBeans.produce(AdditionalBeanBuildItem.builder().addBeanClasses(MavenRepositorySystemProducer.class, BootstrapMavenContextProducer.class).build());
    }

    @BuildStep
    void registerRuntimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> resources) {
    	System.out.println("QuarkusMavenResolverProcessor.registerRuntimeInitializedClasses");
    	resources.produce(new RuntimeInitializedClassBuildItem("org.apache.maven.wagon.providers.http.HttpWagon"));
    	resources.produce(new RuntimeInitializedClassBuildItem("org.apache.maven.wagon.shared.http.AbstractHttpClientWagon"));
    }

    @BuildStep
    void extensionSslNativeSupport(BuildProducer<ExtensionSslNativeSupportBuildItem> ssl) {
    	System.out.println("QuarkusMavenResolverProcessor.extensionSslNativeSupport");
    	ssl.produce(new ExtensionSslNativeSupportBuildItem(FEATURE));
    }

    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> resources) {
    	System.out.println("QuarkusMavenResolverProcessor.registerForReflection");
    	resources.produce(new ReflectiveClassBuildItem(false, false, "org.apache.maven.wagon.providers.http.HttpWagon"));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordStaticInit(BootstrapMavenContextRecorder recorder, BeanContainerBuildItem beanContainer) {
    	System.out.println("QuarkusMavenResolverProcessor.recordStaticInit");
    	recorder.initMavenRepositorySystemProducer(beanContainer.getValue());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void recordRuntimeStaticInit(BootstrapMavenContextRecorder recorder, BeanContainerBuildItem beanContainer) {
    	System.out.println("QuarkusMavenResolverProcessor.recordRuntimeStaticInit");
    	recorder.initBootstrapMavenContextProducer(beanContainer.getValue());
    }
}
