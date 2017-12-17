package com.mraof.minestuck.entity.consort;

import net.minecraft.entity.Entity;
import net.minecraft.util.text.TextFormatting;

public enum EnumConsort
{
	SALAMANDER(EntitySalamander.class, TextFormatting.YELLOW),
	TURTLE(EntityTurtle.class, TextFormatting.LIGHT_PURPLE),
	NAKAGATOR(EntityNakagator.class, TextFormatting.RED),
	IGUANA(EntityIguana.class, TextFormatting.AQUA);
	
	private final Class<? extends EntityConsort> consortClass;
	private final TextFormatting color;
	
	private EnumConsort(Class<? extends EntityConsort> consort, TextFormatting color)
	{
		consortClass = consort;
		this.color = color;
	}
	
	public boolean isConsort(Entity consort)
	{
		return consortClass.isInstance(consort);
	}
	
	public TextFormatting getColor()
	{
		return color;
	}
	
	public Class<? extends EntityConsort> getConsortClass()
	{
		return consortClass;
	}
	
	public enum MerchantType
	{
		NONE,
		SHADY,
		FOOD,
	}
}