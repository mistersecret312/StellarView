package net.povstalec.stellarview.client.render.space_objects;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.api.client.StellarViewRendering;
import net.povstalec.stellarview.api.common.space_objects.SpaceObject;
import net.povstalec.stellarview.api.common.space_objects.TexturedObject;
import net.povstalec.stellarview.client.render.LightEffects;
import net.povstalec.stellarview.client.resourcepack.ResourcepackReloadListener;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.util.*;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Optional;

public abstract class TexturedObjectRenderer<T extends TexturedObject> extends SpaceObjectRenderer<T>
{
	public static final float DEFAULT_DISTANCE = 100.0F;
	
	public TexturedObjectRenderer(T texturedObject)
	{
		super(texturedObject);
	}
	
	//============================================================================================
	//*****************************************Rendering******************************************
	//============================================================================================
	
	@Override
	public void render(ViewCenter viewCenter, ClientLevel level, float partialTicks, PoseStack stack, Camera camera,
								Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog, BufferBuilder bufferbuilder,
								Vector3f parentVector, AxisRotation parentRotation)
	{
		Vector3f positionVector = getPosition(viewCenter, parentRotation, viewCenter.ticks(), partialTicks).add(parentVector); // Handles orbits 'n stuff
		
		// Add parent vector to current coords
		SpaceCoords coords = spaceCoords().add(positionVector);
		
		// Subtract coords of this from View Center coords to get relative coords
		SphericalCoords sphericalCoords = coords.skyPosition(level, viewCenter, partialTicks, true);
		
		lastDistance = sphericalCoords.r;
		sphericalCoords.r = DEFAULT_DISTANCE;
		
		double childRenderDistance = renderedObject.getFadeOutHandler().getMaxChildRenderDistance().toKm();
		if(childRenderDistance > lastDistance)
		{
			for(SpaceObjectRenderer child : children)
			{
				// Render child behind the parent
				if(child.lastDistance >= this.lastDistance)
					child.render(viewCenter, level, partialTicks, stack, camera, projectionMatrix, isFoggy, setupFog, bufferbuilder, positionVector, axisRotation());
			}
		}
		
		// If the object isn't the same we're viewing everything from and it isn't too far away, render it
		if(!viewCenter.objectEquals(this))
			renderTextureLayers(viewCenter, level, camera, bufferbuilder, stack.last().pose(), sphericalCoords, viewCenter.ticks(), lastDistance, partialTicks);
		
		if(childRenderDistance > lastDistance)
		{
			for(SpaceObjectRenderer child : children)
			{
				// Render child in front of the parent
				if(child.lastDistance < this.lastDistance)
					child.render(viewCenter, level, partialTicks, stack, camera, projectionMatrix, isFoggy, setupFog, bufferbuilder, positionVector, axisRotation());
			}
		}
	}
	
	
	public static void renderOnSphere(Color.FloatRGBA rgba, Color.FloatRGBA secondaryRGBA, ResourceLocation texture, UV.Quad uv,
									  ClientLevel level, Camera camera, BufferBuilder bufferbuilder, Matrix4f lastMatrix, SphericalCoords sphericalCoords,
									  long ticks, double distance, float partialTicks, float brightness, float size, float rotation, boolean shouldBlend)
	{
		Vector3f corner00 = new Vector3f(size, DEFAULT_DISTANCE, size);
		Vector3f corner10 = new Vector3f(-size, DEFAULT_DISTANCE, size);
		Vector3f corner11 = new Vector3f(-size, DEFAULT_DISTANCE, -size);
		Vector3f corner01 = new Vector3f(size, DEFAULT_DISTANCE, -size);
		
		Quaterniond quaternionX = new Quaterniond().rotateY(sphericalCoords.theta);
		quaternionX.mul(new Quaterniond().rotateX(sphericalCoords.phi));
		quaternionX.mul(new Quaterniond().rotateY(rotation));
		
		quaternionX.transform(corner00);
		quaternionX.transform(corner10);
		quaternionX.transform(corner11);
		quaternionX.transform(corner01);
		
		if(shouldBlend)
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		else
			RenderSystem.defaultBlendFunc();
		
		RenderSystem.setShaderColor(rgba.red() * secondaryRGBA.red(), rgba.green() * secondaryRGBA.green(), rgba.blue() * secondaryRGBA.blue(), brightness * rgba.alpha() * secondaryRGBA.alpha());
		
		RenderSystem.setShaderTexture(0, texture);
		bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

		ViewCenter center = StellarViewRendering.getViewCenter(level.dimension().location());
		int phase = 0;
		if(center != null && uv.hasPhaseHandling())
		{
			Optional<ResourceKey<SpaceObject>> preferredStar = center.getPreferredStar();
			if(preferredStar.isPresent())
			{
				SpaceObject star = ResourcepackReloadListener.ReloadListener.getSpaceObject(preferredStar.get().location());
				if(star != null)
				{
					SphericalCoords starSpherical = star.getCoords().skyPosition(level, center, partialTicks, true);

					Vector3d starPos = starSpherical.toCartesianD();
					Vector3d planetPos = sphericalCoords.toCartesianD();

					Vector3d planetLight = starPos.sub(planetPos);

					Vector3d planetObserver = planetPos.negate();

					double dot = planetLight.dot(planetObserver);

					double magMoonToSun = planetLight.length();
					double magMoonToEarth = planetObserver.length();

					double angleRad = Math.acos(dot / (magMoonToSun * magMoonToEarth));

					double angleDeg = Math.toDegrees(angleRad);
					Vector3d cross = planetObserver.cross(planetLight);

					if(cross.dot(new Vector3d(0, 1, 0)) < 0)
						angleDeg = -angleDeg;

					// Normalize to [0, 360)
					angleDeg = (angleDeg + 360.0) % 360.0;

					// Rotate so that 0° = new (Sun behind), 180° = full
					// If your current 0° corresponds to full, shift by 180°:
					angleDeg = (angleDeg + 180.0) % 360.0;

					// Map to phase index
					int phases = uv.getPhaseHandler().columns() * uv.getPhaseHandler().rows();
					phase = (int) Math.floor((angleDeg / 360.0) * phases);
				}
			}
		}
		bufferbuilder.vertex(lastMatrix, corner00.x, corner00.y, corner00.z).uv(uv.topRight().u(phase), uv.topRight().v(phase)).endVertex();
		bufferbuilder.vertex(lastMatrix, corner10.x, corner10.y, corner10.z).uv(uv.bottomRight().u(phase), uv.bottomRight().v(phase)).endVertex();
		bufferbuilder.vertex(lastMatrix, corner11.x, corner11.y, corner11.z).uv(uv.bottomLeft().u(phase), uv.bottomLeft().v(phase)).endVertex();
		bufferbuilder.vertex(lastMatrix, corner01.x, corner01.y, corner01.z).uv(uv.topLeft().u(phase), uv.topLeft().v(phase)).endVertex();
		
		BufferUploader.drawWithShader(bufferbuilder.end());
		
		RenderSystem.defaultBlendFunc();
	}

	/**
	 * Method for rendering an individual texture layer, override to change details of how this object's texture layers are rendered
	 * @param textureLayer
	 * @param level
	 * @param bufferbuilder
	 * @param lastMatrix
	 * @param sphericalCoords
	 * @param ticks
	 * @param distance
	 * @param partialTicks
	 */
	protected void renderTextureLayer(TextureLayer textureLayer, ViewCenter viewCenter, ClientLevel level, Camera camera, BufferBuilder bufferbuilder,
									  Matrix4f lastMatrix, SphericalCoords sphericalCoords, double fade, long ticks, double distance, float partialTicks)
	{
		if(textureLayer.rgba().alpha() <= 0)
			return;
		
		float size = (float) textureLayer.mulSize(renderedObject.distanceSize(distance));

		if(size < textureLayer.minSize()) {
			if (textureLayer.clampAtMinSize())
				size = (float) textureLayer.minSize();
			else
				return;
		}
		else if(size > textureLayer.maxSize()) {
			if (textureLayer.clampAtMaxSize())
				size = (float) textureLayer.maxSize();
			else
				return;
		}
		
		renderOnSphere(textureLayer.rgba(), Color.FloatRGBA.DEFAULT, textureLayer.texture(), textureLayer.uv(),
				level, camera, bufferbuilder, lastMatrix, sphericalCoords,
				ticks, distance, partialTicks, LightEffects.dayBrightness(viewCenter, size, ticks, level, camera, partialTicks) * (float) fade,
				size, (float) textureLayer.rotation(), textureLayer.shoulBlend());
	}
	
	protected void renderTextureLayers(ViewCenter viewCenter, ClientLevel level, Camera camera, BufferBuilder bufferbuilder, Matrix4f lastMatrix, SphericalCoords sphericalCoords, long ticks, double distance, float partialTicks)
	{
		double fade = renderedObject.fadeOut(distance);
		
		if(fade <= 0)
			return;
		
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		
		for(TextureLayer textureLayer : renderedObject.getTextureLayers())
		{
			renderTextureLayer(textureLayer, viewCenter, level, camera, bufferbuilder, lastMatrix, sphericalCoords, fade, ticks, distance, partialTicks);
		}
	}
}
