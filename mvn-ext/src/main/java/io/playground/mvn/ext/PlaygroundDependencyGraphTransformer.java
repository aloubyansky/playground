package io.playground.mvn.ext;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.collection.DependencyGraphTransformationContext;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.graph.DependencyNode;

public class PlaygroundDependencyGraphTransformer implements DependencyGraphTransformer {

	private final DependencyGraphTransformer delegate;
	
	public PlaygroundDependencyGraphTransformer(DependencyGraphTransformer delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public DependencyNode transformGraph(DependencyNode node, DependencyGraphTransformationContext context)
			throws RepositoryException {
		System.out.println("START transforming graph of " + node.getArtifact());
		try {
			return delegate.transformGraph(node, context);
		} finally {
			System.out.println("FINISHED transforming graph of " + node.getArtifact());			
		}
	}
}
