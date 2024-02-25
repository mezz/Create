package com.simibubi.create.content.contraptions.render;

import org.apache.commons.lang3.tuple.Pair;

import com.jozufozu.flywheel.api.event.BeginFrameEvent;
import com.jozufozu.flywheel.api.event.ReloadLevelRendererEvent;
import com.jozufozu.flywheel.api.event.RenderStageEvent;
import com.jozufozu.flywheel.backend.gl.error.GlError;
import com.jozufozu.flywheel.lib.transform.TransformStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllMovementBehaviours;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.contraptions.behaviour.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper;
import com.simibubi.create.foundation.utility.WorldAttached;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(Dist.CLIENT)
public class ContraptionRenderDispatcher {

	private static WorldAttached<ContraptionRenderingWorld<?>> WORLDS = new WorldAttached<>(SBBContraptionManager::new);

	/**
	 * Reset a contraption's renderer.
	 *
	 * @param contraption The contraption to invalidate.
	 * @return true if there was a renderer associated with the given contraption.
	 */
	public static boolean invalidate(Contraption contraption) {
		Level level = contraption.entity.level();

		return WORLDS.get(level)
			.invalidate(contraption);
	}

	public static void tick(Level world) {
		if (Minecraft.getInstance()
			.isPaused())
			return;

		WORLDS.get(world)
			.tick();
	}

	public static void beginFrame(BeginFrameEvent event) {
		WORLDS.get(event.getContext()
						.level())
			.beginFrame(event);
	}

	public static void renderLayer(RenderStageEvent event) {
		WORLDS.get(event.getLevel())
			.renderLayer(event);

		GlError.pollAndThrow(() -> "contraption layer: " + event.getStage());
	}

	public static void onRendererReload(ReloadLevelRendererEvent event) {
		reset();
	}

	public static void renderFromEntity(AbstractContraptionEntity entity, Contraption contraption,
		MultiBufferSource buffers) {
		Level world = entity.level();

		ContraptionRenderInfo renderInfo = WORLDS.get(world)
			.getRenderInfo(contraption);
		ContraptionMatrices matrices = renderInfo.getMatrices();

		// something went wrong with the other rendering
		if (!matrices.isReady())
			return;

		VirtualRenderWorld renderWorld = renderInfo.renderWorld;

		renderBlockEntities(world, renderWorld, contraption, matrices, buffers);

		if (buffers instanceof MultiBufferSource.BufferSource)
			((MultiBufferSource.BufferSource) buffers).endBatch();

		renderActors(world, renderWorld, contraption, matrices, buffers);
	}

	public static VirtualRenderWorld setupRenderWorld(Level world, Contraption c) {
		ContraptionWorld contraptionWorld = c.getContraptionWorld();

		BlockPos origin = c.anchor;
		int minBuildHeight = contraptionWorld.getMinBuildHeight();
		int height = contraptionWorld.getHeight();
		VirtualRenderWorld renderWorld = new VirtualRenderWorld(world, minBuildHeight, height, origin) {
			@Override
			public boolean supportsVisualization() {
				return canInstance();
			}
		};

		renderWorld.setBlockEntities(c.presentBlockEntities.values());
		for (StructureTemplate.StructureBlockInfo info : c.getBlocks()
			.values())
			// Skip individual lighting updates to prevent lag with large contraptions
			// FIXME 1.20 this '0' used to be Block.UPDATE_SUPPRESS_LIGHT, yet VirtualRenderWorld didn't actually parse the flags at all
			renderWorld.setBlock(info.pos(), info.state(), 0);

		renderWorld.runLightEngine();
		return renderWorld;
	}

	public static void renderBlockEntities(Level world, VirtualRenderWorld renderWorld, Contraption c,
		ContraptionMatrices matrices, MultiBufferSource buffer) {
		BlockEntityRenderHelper.renderBlockEntities(world, renderWorld, c.getSpecialRenderedBEs(),
			matrices.getModelViewProjection(), matrices.getLight(), buffer);
	}

	protected static void renderActors(Level world, VirtualRenderWorld renderWorld, Contraption c,
		ContraptionMatrices matrices, MultiBufferSource buffer) {
		PoseStack m = matrices.getModel();

		for (Pair<StructureTemplate.StructureBlockInfo, MovementContext> actor : c.getActors()) {
			MovementContext context = actor.getRight();
			if (context == null)
				continue;
			if (context.world == null)
				context.world = world;
			StructureTemplate.StructureBlockInfo blockInfo = actor.getLeft();

			MovementBehaviour movementBehaviour = AllMovementBehaviours.getBehaviour(blockInfo.state());
			if (movementBehaviour != null) {
				if (c.isHiddenInPortal(blockInfo.pos()))
					continue;
				m.pushPose();
				TransformStack.of(m)
					.translate(blockInfo.pos());
				movementBehaviour.renderInContraption(context, renderWorld, matrices, buffer);
				m.popPose();
			}
		}
	}

	public static int getLight(Level world, float lx, float ly, float lz) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		float block = 0, sky = 0;
		float offset = 1 / 8f;

		for (float zOffset = offset; zOffset >= -offset; zOffset -= 2 * offset)
			for (float yOffset = offset; yOffset >= -offset; yOffset -= 2 * offset)
				for (float xOffset = offset; xOffset >= -offset; xOffset -= 2 * offset) {
					pos.set(lx + xOffset, ly + yOffset, lz + zOffset);
					block += world.getBrightness(LightLayer.BLOCK, pos) / 8f;
					sky += world.getBrightness(LightLayer.SKY, pos) / 8f;
				}

		return LightTexture.pack((int) block, (int) sky);
	}

	public static int getContraptionWorldLight(MovementContext context, VirtualRenderWorld renderWorld) {
		return LevelRenderer.getLightColor(renderWorld, context.localPos);
	}

	public static void reset() {

	}

	public static boolean canInstance() {
		return false;
	}
}
