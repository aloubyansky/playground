package io.playground;

public interface DecomposedBomTransformer {

	DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom) throws BomDecomposerException;
}
