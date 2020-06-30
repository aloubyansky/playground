package io.quarkus.maven.resolver;

import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class BootstrapMavenContextRecorder {

    public void initBootstrapMavenContextProducer(BeanContainer beanContainer) {
		System.out.println("MavenArtifactResolverRecorder.initBootstrapMavenContextProducer");
    	beanContainer.instance(BootstrapMavenContextProducer.class);
    }

    public void initMavenRepositorySystemProducer(BeanContainer beanContainer) {
		System.out.println("MavenArtifactResolverRecorder.initMavenRepositorySystemProducer");
		beanContainer.instance(MavenRepositorySystemProducer.class);
    }
}
