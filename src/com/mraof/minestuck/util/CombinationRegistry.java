package com.mraof.minestuck.util;

import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mraof.minestuck.Minestuck;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.oredict.OreDictionary;

public class CombinationRegistry {
	private static Hashtable<List<Object>, ItemStack> combRecipes = new Hashtable<List<Object>, ItemStack>();
	public static final boolean MODE_AND  = true;
	public static final boolean MODE_OR = false;
	
	/**
	 * Creates an entry for a result of combining the cards of two items. Used in the Punch Designix.
	 */
	public static void addCombination(ItemStack input1, ItemStack input2, boolean mode, ItemStack output) {
		addCombination(input1, input2, mode, true, true, output);
	}
	
	
	public static void addCombination(ItemStack input1, ItemStack input2, boolean mode, boolean useDamage1, boolean useDamage2, ItemStack output) {
		addCombination(input1.getItem(), useDamage1 ? input1.getItemDamage() : OreDictionary.WILDCARD_VALUE, input2.getItem(), useDamage2 ? input2.getItemDamage() : OreDictionary.WILDCARD_VALUE, mode, output);
	}
	
	/**
	 * input1 and input2 is an "Item" or a "String"
	 */
	public static void addCombination(Object input1, int damage1, Object input2, int damage2, boolean mode, ItemStack output)
	{
		if(input1.hashCode() >= input2.hashCode())
			combRecipes.put(Arrays.asList(input1, damage1, input2, damage2, mode), output);
		else combRecipes.put(Arrays.asList(input2, damage2, input1, damage1, mode), output);
	}
	
	/**
	 * Returns an entry for a result of combining the cards of two items. Used in the Punch Designix.
	 */
	public static ItemStack getCombination(ItemStack input1, ItemStack input2, boolean mode) {
		ItemStack item;
		if (input1 == null || input2 == null) {return null;}
		
		if((item = getCombination(input1.getItem(), input1.getItemDamage(), input2.getItem(), input2.getItemDamage(), mode)) == null)
		{
			String[] itemNames2 = getDictionaryNames(input2);
			
			for(String str2 : itemNames2)
				if((item = getCombination(input1.getItem(), input1.getItemDamage(), str2, input2.getItemDamage(), mode)) != null)
					return item;
			
			String[] itemNames1 = getDictionaryNames(input1);
			for(String str1 : itemNames1)
				if((item = getCombination(str1, input1.getItemDamage(), input2.getItem(), input2.getItemDamage(), mode)) != null)
					return item;
			
			for(String str1 : itemNames1)
				for(String str2 : itemNames2)
					if((item = getCombination(str1, input1.getItemDamage(), str2, input2.getItemDamage(), mode)) != null)
						return item;
		}
		
		if(item == null)
			if(input1.getItem().equals(Minestuck.blockStorage) && input1.getItemDamage() == 1)
				return mode?input1:input2;
			else if(input2.getItem().equals(Minestuck.blockStorage) && input2.getItemDamage() == 1)
				return mode?input2:input1;
		return item;
	}
	
	private static ItemStack getCombination(Object input1, int damage1, Object input2, int damage2, boolean mode)
	{
		ItemStack item;
		
		if(input1.hashCode() >= input2.hashCode())
		{
			if((item = combRecipes.get(Arrays.asList(input1, damage1, input2, damage2, mode))) != null);
			else if((item = combRecipes.get(Arrays.asList(input1, damage1, input2, OreDictionary.WILDCARD_VALUE, mode))) != null);
			else if((item = combRecipes.get(Arrays.asList(input1, OreDictionary.WILDCARD_VALUE, input2, damage2, mode))) != null);
			else item = combRecipes.get(Arrays.asList(input1, OreDictionary.WILDCARD_VALUE, input2, OreDictionary.WILDCARD_VALUE, mode));
		}
		else
		{
			if((item = combRecipes.get(Arrays.asList(input2, damage2, input1, damage1, mode))) != null);
			else if((item = combRecipes.get(Arrays.asList(input2, OreDictionary.WILDCARD_VALUE, input1, damage1, mode))) != null);
			else if((item = combRecipes.get(Arrays.asList(input2, damage2, input1, OreDictionary.WILDCARD_VALUE, mode))) != null);
			else item = combRecipes.get(Arrays.asList(input2, OreDictionary.WILDCARD_VALUE, input1, OreDictionary.WILDCARD_VALUE, mode));
		}
		
		return item;
	}
	
	protected static String[] getDictionaryNames(ItemStack stack)
	{
		int[] itemIDs = OreDictionary.getOreIDs(stack);
		String[] itemNames = new String[itemIDs.length];
		for(int i = 0; i < itemIDs.length; i++)
			itemNames[i] = OreDictionary.getOreName(itemIDs[i]);
		return itemNames;
	}
	
	public static Hashtable<List<Object>, ItemStack> getAllConversions() {
		return combRecipes;
	}
	
	private static final Gson STERIALIZER = new GsonBuilder().registerTypeAdapter(List.class, new RecipeDeserializer()).create();
	
	static int registerCombinationRecipes(Reader reader)
	{
		List<Recipe> recipes = STERIALIZER.fromJson(reader, List.class);
		
		if(recipes != null)
		{
			for(Recipe recipe : recipes)
				combRecipes.put(recipe.input, recipe.output);
			return recipes.size();
		}
		else return -1;
	}
	
	private static class RecipeDeserializer extends AlchemyRecipeHandler.RecipeDeserializer
	{
		@Override
		public Object deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
		{
			ArrayList<Recipe> list = new ArrayList<Recipe>();
			JsonObject jsonObject = json.getAsJsonObject();
			JsonArray recipes = jsonObject.get("recipes").getAsJsonArray();
			for(int i = 0; i < recipes.size(); i++)
			{
				JsonObject recipe = recipes.get(i).getAsJsonObject();
				
				String name1 = recipe.get("item1").getAsString();
				String name2 = recipe.get("item2").getAsString();
				
				Object item1 = getItem(name1), item2 = getItem(name2);
				int meta1 = getMeta(name1), meta2 = getMeta(name2);
				String modeName = recipe.get("mode").getAsString();
				String resultName = recipe.get("result").getAsString();
				ItemStack result = getItemStack(resultName);
				
				boolean mode;
				if(modeName.toLowerCase().equals("and"))
					mode = MODE_AND;
				else if(modeName.toLowerCase().equals("or"))
					mode = MODE_OR;
				else
				{
					FMLLog.warning("[Minestuck] Can't translate mode \"%s\" to either \"AND\" or \"OR\" for recipe \"%s %s %s -> %s\".", modeName, name1, modeName, name2, resultName);
					continue;
				}
				
				if(item1 == null || item2 == null)
				{
					Object[] formatting =  new String[]{item1 == null ? name1 : name2, name1, modeName, name2, resultName};
					FMLLog.warning("[Minestuck] Can't translate ingredient \"%s\" into an item stack for recipe \"%s %s %s -> %s\".", formatting);
					continue;
				}
				
				if(result == null)
				{
					FMLLog.warning("[Minestuck] Can't translate result \"%s\" into an item stack for recipe \"%s %s %s -> %s\".", resultName, name1, modeName, name2, resultName);
					continue;
				}
				
				list.add(new Recipe(Arrays.asList(item1, meta1, item2, meta2, mode), result));
			}
			
			return list;
		}
	}
	
	private static class Recipe
	{
		List<Object> input;
		ItemStack output;
		Recipe(List<Object> list, ItemStack stack)
		{
			input = list;
			output = stack;
		}
	}
}
