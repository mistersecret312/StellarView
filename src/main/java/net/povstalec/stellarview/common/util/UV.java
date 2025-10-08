package net.povstalec.stellarview.common.util;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.StellarView;

public class UV
{
	public static final String U = "u";
	public static final String V = "v";
	
	@Nullable
	private UV.PhaseHandler phaseHandler;
	
	private final float u;
	private final float v;
	
	public static final Codec<UV> CODEC = RecordCodecBuilder.create(instance -> instance.group(
    		Codec.FLOAT.fieldOf(U).forGetter(UV::u),
    		Codec.FLOAT.fieldOf(V).forGetter(UV::v)
			).apply(instance, UV::new));
	
	public UV(@Nullable UV.PhaseHandler phaseHandler, float u, float v)
	{
		if(phaseHandler != null && phaseHandler.doPhases())
			this.phaseHandler = phaseHandler;
		
		this.u = u;
		this.v = v;
	}
	
	public UV(float u, float v)
	{
		this(null, u, v);
	}
	
	public float u()
	{
		return u;
	}
	
	public float u(int phase)
	{
		return phaseHandler != null ? (u + phaseHandler.u(phase)) / phaseHandler.columns() : u;
	}
	
	public float v()
	{
		return v;
	}
	
	public float v(int phase)
	{
		return phaseHandler != null ? (v + phaseHandler.v(phase)) / phaseHandler.rows() : v;
	}

	public boolean hasPhaseHandler()
	{
		return phaseHandler != null;
	}
	
	//============================================================================================
	//*************************************Saving and Loading*************************************
	//============================================================================================
	
	public CompoundTag serialize()
	{
		CompoundTag tag = new CompoundTag();
		tag.putFloat(U, u);
		tag.putFloat(V, v);
		
		return tag;
	}
	
	public static UV deserialize(CompoundTag tag, UV.PhaseHandler phaseHandler)
	{
		return new UV(phaseHandler, tag.getFloat(U), tag.getFloat(V));
	}
	
	
	
	public static class Quad
	{
		public static final String PHASE_HANDLER = "phase_handler";
		
		public static final String TOP_LEFT = "top_left";
		public static final String BOTTOM_LEFT = "bottom_left";
		public static final String BOTTOM_RIGHT = "bottom_right";
		public static final String TOP_RIGHT = "top_right";
		
		public static final String FLIP_UV = "flip_uv";
		
		public static final Quad DEFAULT_QUAD_UV = new Quad(false);
		
		private final UV.PhaseHandler phaseHandler;
		
		private final UV topLeft;
		private final UV bottomLeft;
		private final UV bottomRight;
		private final UV topRight;
		
		private final boolean flipped;
		
		public Quad(UV.PhaseHandler phaseHandler, UV topLeft, UV bottomLeft, UV bottomRight, UV topRight, boolean flipped)
		{
			this.phaseHandler = phaseHandler;
			
			if(flipped)
			{
				this.topLeft = topRight;
				this.bottomLeft = bottomRight;
				this.bottomRight = bottomLeft;
				this.topRight = topLeft;
			}
			else
			{
				this.topLeft = topLeft;
				this.bottomLeft = bottomLeft;
				this.bottomRight = bottomRight;
				this.topRight = topRight;
			}
			
			this.flipped = flipped;
		}
		
		public Quad(UV.PhaseHandler phaseHandler, UV topLeft, UV bottomLeft, UV bottomRight, UV topRight)
		{
			this(phaseHandler, topLeft, bottomLeft, bottomRight, topRight, false);
		}
		
		public Quad(UV.PhaseHandler phaseHandler, float topLeftU, float topLeftV, float bottomRightU, float bottomRightV, boolean flipped)
		{
			this(phaseHandler, new UV(phaseHandler, topLeftU, topLeftV), new UV(phaseHandler, topLeftU, bottomRightV), new UV(phaseHandler, bottomRightU, bottomRightV), new UV(phaseHandler, bottomRightU, topLeftV), flipped);
		}
		
		public Quad(UV.PhaseHandler phaseHandler, float topLeftU, float topLeftV, float bottomRightU, float bottomRightV)
		{
			this(phaseHandler, topLeftU, topLeftV, bottomRightU, bottomRightV, false);
		}
		
		public static final Codec<UV.Quad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
	    		PhaseHandler.CODEC.optionalFieldOf("phase_handler", PhaseHandler.DEFAULT_PHASE_HANDLER).forGetter((quad) -> quad.phaseHandler),
	    		Codec.FLOAT.optionalFieldOf("u_start", 0F).forGetter((quad) -> quad.topLeft.u()),
	    		Codec.FLOAT.optionalFieldOf("v_start", 0F).forGetter((quad) -> quad.topLeft.v()),
	    		Codec.FLOAT.optionalFieldOf("u_end", 1F).forGetter((quad) -> quad.bottomRight.u()),
	    		Codec.FLOAT.optionalFieldOf("v_end", 1F).forGetter((quad) -> quad.bottomRight.v()),
	    		Codec.BOOL.optionalFieldOf("flip_uv", false).forGetter((quad) -> quad.flipped)
				).apply(instance, UV.Quad::new));
		
		// Phase dependant Quad UV
		public Quad(UV.PhaseHandler phaseHandler, boolean flipped)
		{
			this(phaseHandler, 0, 0, 1, 1, flipped);
		}
		
		// Full quad
		public Quad(boolean flipped)
		{
			this(UV.PhaseHandler.DEFAULT_PHASE_HANDLER, new UV(0, 0), new UV(0, 1), new UV(1, 1), new UV(1, 0), flipped);
		}
		
		public UV topLeft()
		{
			return topLeft;
		}
		
		public UV bottomLeft()
		{
			return bottomLeft;
		}
		
		public UV bottomRight()
		{
			return bottomRight;
		}
		
		public UV topRight()
		{
			return topRight;
		}

		public boolean hasPhaseHandling()
		{
			return phaseHandler.doPhases();
		}

		public PhaseHandler getPhaseHandler()
		{
			return phaseHandler;
		}

		//============================================================================================
		//*************************************Saving and Loading*************************************
		//============================================================================================
		
		public CompoundTag serialize()
		{
			CompoundTag tag = new CompoundTag();
			tag.put(PHASE_HANDLER, phaseHandler.serialize());
			
			tag.put(TOP_LEFT, topLeft.serialize());
			tag.put(BOTTOM_LEFT, bottomLeft.serialize());
			tag.put(BOTTOM_RIGHT, bottomRight.serialize());
			tag.put(TOP_RIGHT, topRight.serialize());
			
			tag.putBoolean(FLIP_UV, flipped);
			
			return tag;
		}
		
		public static Quad deserialize(CompoundTag tag)
		{
			PhaseHandler phaseHandler = PhaseHandler.deserialize(tag.getCompound(PHASE_HANDLER));
			
			return new Quad(phaseHandler, UV.deserialize(tag.getCompound(TOP_LEFT), phaseHandler), UV.deserialize(tag.getCompound(BOTTOM_LEFT), phaseHandler),
					UV.deserialize(tag.getCompound(BOTTOM_RIGHT), phaseHandler), UV.deserialize(tag.getCompound(TOP_RIGHT), phaseHandler));
		}
	}
	
	public static class PhaseHandler
	{
		public static final String COLUMNS = "columns";
		public static final String ROWS = "rows";

		private final int columns;
		private final int rows;
		
		private final int totalPhases;

		private final boolean doPhases;
		
		public static final PhaseHandler DEFAULT_PHASE_HANDLER = new PhaseHandler(1, 1);
		
		public static final Codec<PhaseHandler> CODEC = RecordCodecBuilder.create(instance -> instance.group(
	    		Codec.INT.fieldOf("columns").forGetter((phaseHandler) -> phaseHandler.columns),
	    		Codec.INT.fieldOf("rows").forGetter((phaseHandler) -> phaseHandler.rows)
				).apply(instance, PhaseHandler::new));
	    
	    public PhaseHandler(int columns, int rows)
	    {
            this.columns = columns;
			this.rows = rows;
			
			this.totalPhases = columns * rows;

			this.doPhases = this.totalPhases != 1;
	    }
	    
	    public int phase(long ticks)
	    {
			return 0;
	    }
	    
	    public int u(int phase)
	    {
	    	return phase % columns;
	    }
	    
	    public int v(int phase)
	    {
	        return phase / columns % rows;
	    }
	    
	    public int rows()
	    {
	    	return rows;
	    }
	    
	    public int columns()
	    {
	        return columns;
	    }
	    
	    public boolean doPhases()
	    {
	    	return doPhases;
	    }
		
		//============================================================================================
		//*************************************Saving and Loading*************************************
		//============================================================================================
		
		public CompoundTag serialize()
		{
			CompoundTag tag = new CompoundTag();
			tag.putInt(COLUMNS, columns);
			tag.putInt(ROWS, rows);
			
			return tag;
		}
		
		public static PhaseHandler deserialize(CompoundTag tag)
		{
			return new PhaseHandler(tag.getInt(COLUMNS), tag.getInt(ROWS));
		}
	}
}
