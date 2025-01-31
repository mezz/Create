package com.simibubi.create.foundation.block.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;
import net.minecraftforge.client.model.data.ModelProperty;

public class ColoredVertexModel extends BakedModelWrapper<BakedModel> {

	private static final ModelProperty<BlockPos> POSITION_PROPERTY = new ModelProperty<>();
	private IBlockVertexColor color;

	public ColoredVertexModel(BakedModel originalModel, IBlockVertexColor color) {
		super(originalModel);
		this.color = color;
	}

	@Override
	public IModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, IModelData tileData) {
		return new ModelDataMap.Builder().withInitial(POSITION_PROPERTY, pos).build();
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, Random rand, IModelData extraData) {
		List<BakedQuad> quads = super.getQuads(state, side, rand, extraData);
		if (quads.isEmpty())
			return quads;
		if (!extraData.hasProperty(POSITION_PROPERTY))
			return quads;
		BlockPos data = extraData.getData(POSITION_PROPERTY);
		quads = new ArrayList<>(quads);

		// Optifine might've rejigged vertex data
		VertexFormat format = DefaultVertexFormat.BLOCK;
		int colorIndex = 0;
		for (int elementId = 0; elementId < format.getElements().size(); elementId++) {
			VertexFormatElement element = format.getElements().get(elementId);
			if (element.getUsage() == VertexFormatElement.Usage.COLOR)
				colorIndex = elementId;
		}
		int colorOffset = format.getOffset(colorIndex) / 4;

		for (int i = 0; i < quads.size(); i++) {
			BakedQuad quad = quads.get(i);

			BakedQuad newQuad = QuadHelper.clone(quad);
			int[] vertexData = newQuad.getVertices();

			for (int vertex = 0; vertex < vertexData.length; vertex += format.getIntegerSize()) {
				float x = Float.intBitsToFloat(vertexData[vertex]);
				float y = Float.intBitsToFloat(vertexData[vertex + 1]);
				float z = Float.intBitsToFloat(vertexData[vertex + 2]);
				int color = this.color.getColor(x + data.getX(), y + data.getY(), z + data.getZ());
				vertexData[vertex + colorOffset] = color;
			}

			quads.set(i, newQuad);
		}
		return quads;
	}

}
