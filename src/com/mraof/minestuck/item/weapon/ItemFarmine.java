package com.mraof.minestuck.item.weapon;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Random;

import com.mraof.minestuck.block.MinestuckBlocks;
import com.mraof.minestuck.util.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A "Farmine" tool is a tool that mines more blocks that just the one originally broken.
 * Other mods may refer to this as vein-mining or similar, but here it is called far-mining.
 * A farmining tool works mostly like a normal ItemWeapon: it has durability, attack damage, attack speed, and enchantability.
 * In addition, however, it has a Radius and Terminus.
 * The radius is the farthest distance between the initially-broken block and a block broken in the same mining operation. A value of -1 means no radius will come into play.
 * The terminus is the largest number of blocks that can be destroyed in a single farmining operation. Terminus is clamped to a minimum of 1.
 * 
 * Farmining tools will only break more of the same block broken originally.
 * Two blocks are considered the same if they have the same Block ID and drop the same item with the same damage.
 * Two blocks that are not normally considered the same will be considered the same if they have an association.
 * @author BenjaminK
 *
 */
public class ItemFarmine extends ItemWeapon
{
	private int radius;
	private int terminus;
	private HashSet<Block> farMineForbiddenBlocks = new HashSet<Block>();
	private HashMap<Block, HashSet<Block>> farMineEquivalencies = new HashMap<Block, HashSet<Block>>();
	
	public ItemFarmine(ToolMaterial material, int maxUses, double damageVsEntity, double weaponSpeed, int enchantability, String name, int r, int t)
	{
		super(material, maxUses, damageVsEntity, weaponSpeed, enchantability, name);
		redefineLimiters(r, t);
		initializeFarMineLists();
	}
	
	public ItemFarmine(int maxUses, double damageVsEntity, double weaponSpeed, int enchantability, String name, int r, int t)
	{
		this(ToolMaterial.IRON, maxUses, damageVsEntity, weaponSpeed, enchantability, name, r, t);
	}

	private void initializeFarMineLists()
	{
		farMineForbiddenBlocks.add(Blocks.OBSIDIAN);
		
		addAssociation(Blocks.REDSTONE_ORE, Blocks.LIT_REDSTONE_ORE);
		addAssociation(Blocks.FURNACE, Blocks.LIT_FURNACE);
		addAssociation(Blocks.REDSTONE_LAMP, Blocks.LIT_REDSTONE_LAMP);
		addAssociation(Blocks.DAYLIGHT_DETECTOR, Blocks.DAYLIGHT_DETECTOR_INVERTED);
		addAssociation(Blocks.DIRT, Blocks.GRASS);
		addAssociation(Blocks.DIRT, Blocks.MYCELIUM);
		addAssociation(Blocks.DIRT, Blocks.GRASS_PATH);
		addAssociation(Blocks.END_STONE, MinestuckBlocks.endGrass);
		
		addOneWayAssociation(Blocks.COBBLESTONE, Blocks.MONSTER_EGG);
		addOneWayAssociation(Blocks.STONE, Blocks.MONSTER_EGG);
		addOneWayAssociation(Blocks.STONEBRICK, Blocks.MONSTER_EGG);
	}
	
	/**
	* Called when a Block is destroyed using this Item. Returns true to trigger the "Use Item" statistic.
	* This is the method that performs farmining calculations and destruction.
	* @param stack The ItemStack being used to destroy the block. This should always be an instance of ItemFarmine.
	* This method also respects the presence of Silk Touch or Fortune on this ItemStack.
	* @param worldIn The world where the blocks being destroyed can be found.
	* @param blockState The state of the initial block being destroyed.
	* @param pos The position of the initial block being destroyed.
	* @param playerIn The player doing the actual destroying. This MUST be an instance of EntityPlayer or no farmining will occur!
	* @return Returns false if and only if the world is remote.
	*/
	@Override
	public boolean onBlockDestroyed(ItemStack stack, World worldIn, IBlockState blockState, BlockPos pos, EntityLivingBase playerIn)
	{
		if(worldIn.isRemote)
		{
			return false;
		}
		
		Comparator<Pair> comparator = new PairedIntComparator();
		PriorityQueue<Pair> candidates = new PriorityQueue<Pair>(comparator);
		Block block = blockState.getBlock();
		int fortuneLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.FORTUNE, stack);
		//Must define the drop using a predetermined seed to establish equivalency with random methods
		Item drop = block.getItemDropped(blockState, new Random(0), fortuneLevel); 
		int damageDrop = block.damageDropped(blockState);
		HashSet<Block> equals = farMineEquivalencies.get(block);
		if(equals==null) equals = new HashSet<Block>();	
		
		//If the tool can't harvest the block, or the player isn't actually a player, or the player is sneaking,
		//or the tool doesn't farmine, or it's one of those blocks that breaks instantly, don't farmine.
		if (!canHarvestBlock(blockState, stack) || !(playerIn instanceof EntityPlayer) || playerIn.isSneaking()
				|| terminus == 1 || radius==0 || Math.abs(blockState.getBlockHardness(worldIn, pos)) < 0.000000001)
		{
			if (!isDamageable())
				return true;
			else
				return super.onBlockDestroyed(stack, worldIn, blockState, pos, playerIn);
		}
		else
		{
			//If the block is unacceptable or there's a tool mismatch, cap out at a basic 3x3 area
			if (farMineForbiddenBlocks.contains(block)
					|| getDestroySpeed(stack, blockState) < getEfficiency())
			{
				candidates.add(new Pair(pos, 1));
			}
			else	//Otherwise, farmine normally
			{
				candidates.add(new Pair(pos, radius));
			}
		}
		
		//This set will contain all the blocks you'll want to break in the end.
		//This is used to determine if the number of blocks broken is too high.
		//If it is, it mines a 3x3 area instead.
		HashSet<BlockPos> blocksToBreak = new HashSet<BlockPos>();
		boolean passedBreakLimit = false;

		while (!candidates.isEmpty())
		{
			BlockPos curr = (BlockPos) candidates.peek().object1;
			int rad = (Integer) candidates.poll().object2;
			if (!blocksToBreak.contains(curr))
			{
				blocksToBreak.add(curr);
				
				if (rad != 0)
				{
					//Iterates across all blocks in a 3x3 cube centered on this block.
					for (int i = -1; i < 2; i++)
					{
						for (int j = -1; j < 2; j++)
						{
							for (int k = -1; k < 2; k++)
							{
								if(i==0 && j==0 && k==0)
									continue;
								BlockPos newBlockPos = new BlockPos(curr.getX() + i, curr.getY() + j, curr.getZ() + k);
								IBlockState newState = worldIn.getBlockState(newBlockPos);
								Block newBlock = newState.getBlock();
								if (	equals.contains(newBlock) || newBlock.equals(block)
										&& newBlock.getItemDropped(newState, new Random(0), fortuneLevel) == drop
										&& newBlock.damageDropped(newState) == damageDrop)
								{
									candidates.add(new Pair(newBlockPos, rad - 1));
								}
							}
						}
					}
				}
			}	
			
			//If you passed the maximum blocks you can break, stop trying to add more blocks to the list.
			if (blocksToBreak.size() + 1 > stack.getMaxDamage() - stack.getItemDamage() || blocksToBreak.size() + 1 > terminus)
			{
				passedBreakLimit = true;
				break;
			}
		}
		
		//If you passed the break limit, then cut back to a 3x3x3 area
		if(passedBreakLimit)
		{
			blocksToBreak.clear();
			
			for (int i = -1; i < 2; i++)
			{
				for (int j = -1; j < 2; j++)
				{
					for (int k = -1; k < 2; k++)
					{
						BlockPos newBlockPos = new BlockPos(pos.getX() + i, pos.getY() + j, pos.getZ() + k);
						IBlockState newState = worldIn.getBlockState(newBlockPos);
						Block newBlock = newState.getBlock();
						if ( equals.contains(newBlock) || newBlock.equals(block)
								&& newBlock.getItemDropped(newState, new Random(0), fortuneLevel) == drop
								&& newBlock.damageDropped(newState) == damageDrop
								&& blocksToBreak.size()+1 < stack.getMaxDamage() - stack.getItemDamage())
						{
							blocksToBreak.add(newBlockPos);
						}
					}
				}
			}
		}
		
		//Now, break ALL of the blocks!
		for (BlockPos blockToBreak : blocksToBreak)
		{
			IBlockState state = worldIn.getBlockState(blockToBreak);
			harvestBlock(worldIn, state.getBlock(), blockToBreak, state, (EntityPlayer) playerIn, stack);
		}
		
		//We add 1 because that means the tool will always take at least 2 damage.
		//This is important because all ItemWeapons take at least 2 damage whenever it breaks a block.
		//This is because ItemWeapon extends ItemSword.
		if (isDamageable())
			stack.damageItem(blocksToBreak.size() + 1, playerIn);
		
		return true;
	}
	
	/*
	 * Utility
	 */
	
	//This returns a larger-to-smaller comparison of the paired objects, assuming they are integers.
	private class PairedIntComparator implements Comparator<Pair>
	{
		@Override
		public int compare(Pair x, Pair y)
		{
			if (x == null || y == null || x.object2 == null || y.object2 == null)
				return 0;
			
			return (Integer) y.object2 - (Integer) x.object2;
		}
	}
	
	/**
	 * Determines if the farmining tool will consider one block equivalent to another.
	 * Only tests if the equivalencies list considers the blocks to be the same.
	 * @param a The block whose nature is the test of equivalence
	 * @param b The block whose equivalency is being tested
	 * @return Returns true if <code>b</code> is forced to be equivalent to <code>a</code>
	 */
	public boolean isEquivalent(Block a, Block b)
	{
		HashSet e = farMineEquivalencies.get(a);
		return e != null && e.contains(b);
	}
	
	private boolean harvestBlock(World world, Block block, BlockPos pos, IBlockState state, EntityLivingBase playerIn, ItemStack stack)
	{
		EntityPlayer player = (EntityPlayer) playerIn;
		
		TileEntity te = world.getTileEntity(pos);
		if(block.removedByPlayer(state, world, pos, player, true))
		{
			block.onBlockDestroyedByPlayer(world, pos, state);
			block.harvestBlock(world, player, pos, state, te, stack);
			return true;
		}
		return false;
	}
	
	/*
	 * Getters, setters, and other public access methods
	 */
	
	/**
	 * Gets the tool's radius. This limits how far out blocks can be mined.
	 * @return Returns the tool's maximum mining range. Will never be less than 0.
	 */
	public int getRadius()
	{
		return radius;
	}
	
	/**
	 * For some blocks, like obsidian, it might not be appropriate to farmine, even with a farmining tool equipped.
	 * Farmining can be disabled for these blocks by adding them to a special list.
	 * This method tests a block to see if it is on this list.
	 * @param block The block being tested
	 * @return Returns true if the block is on the forbidden list, and false if the block is not on the list.
	 */
	public boolean isBlockForbidden(Block block)
	{
		return farMineForbiddenBlocks.contains(block);
	}
	
	/**
	 * Gets the tool's terminus. This limits how many blocks can be mined in one operation.
	 * @return Returns the tool's maximum number of blocks broken in a single operation. Will never be less than 1.
	 */
	public int getTerminus()
	{
		return terminus;
	}
	
	/**
	 * Redefines the limiting values of a farmining tool: radius and terminus.
	 * @param r R is the new value for the tool's radius. Clamped between 0 and t. Setting this to 0 means the item can't farmine at all.
	 * @param t T is the new value for the tool's terminus. This will limit how many blocks can be mined. Values are clamped to a minimum of 1.
	 * @return Returns the same farmining tool, after the change has been applied. Useful for chaining same-line modifications.
	 */
	public ItemFarmine redefineLimiters(int r, int t)	
	{
		//A terminus of less than 1 means that the tool cannot mine. A terminus of 1 means it cannot farmine.
		terminus = Math.max(1, t);
		//A tool that can only mine X blocks can't mine more than X blocks away.
		radius = Math.max(0, Math.min(r, terminus));
		return this;
	}
	
	/**
	 * Each tool maps blocks to sets of blocks, in a system of association.
	 * This implementation was chosen because it provides cheap lookup.
	 * This is good, because associations are checked in large batches within a single server tick.
	 * A block is mapped to a set of all the blocks associated with it.
	 * @return Returns a deep clone of this tool's private association map. 
	 */
	public HashMap<Block, HashSet<Block>> getAssociations()
	{
		HashMap<Block, HashSet<Block>> out = new HashMap<Block, HashSet<Block>>();
		for(Block b : farMineEquivalencies.keySet())
		{
			out.put(b, (HashSet<Block>) farMineEquivalencies.get(b).clone());
		}
		
		return out;
	}
	
	/**
	 * Normally, a block is considered equal to the originally-mined one by having the same Block ID and dropping the same item, with the same damage value.
	 * In cases where this violates intuition and diminishes the gameplay experience (lit redstone ore is not redstone ore), we use associations.
	 * Two blocks that have an association are considered equal to the farmining tool; when one is mined, the other will be, too. 
	 * This method lets you add an association between two blocks so that this tool will treat them as the same. 
	 * @param a A is a block that will be made equivalent to B.
	 * @param b B is a block that will be made equivalent to A.
	 */
	public void addAssociation(Block a, Block b)
	{
		addOneWayAssociation(a, b);
		addOneWayAssociation(b, a);
	}
	
	/**
	 * Adds a one-way association between two blocks, stating that B is equal to A, but A is not equal to B.
	 * One-way associations should be reserved for special cases; please use <code>addAssociation</code> for normal equalities.
	 * For example: by default, monster eggs are the only blocks to use one-way associations;
	 * monster eggs are treated as stone, cobblestone, and stone bricks, but those blocks are not treated as monster eggs.
	 * @param a A is a block that will not be made equal to B. When a B block is mined, blocks of type A are ignored like before.
	 * @param b B is a block that will be made equal to A. When an A block is mined, blocks of type B are included as mining candidates.
	 */
	public void addOneWayAssociation(Block a, Block b)
	{
		HashSet<Block> equalToA = farMineEquivalencies.get(a);
		if(equalToA==null)
		{
			equalToA = new HashSet<Block>();
			farMineEquivalencies.put(a, equalToA);
		}
		equalToA.add(b);
	}

	/**
	 * Removes an association between two blocks. These two blocks will no longer be considered equivalent for this farmining tool.
	 * Blocks will still be equivalent if they have the same block ID and drop items with the same ID and damage values.
	 * This removal is a two-way removal. If you want to remove only one, use this method alongside <code>addOneWayAssociation</code>. 
	 * @param a A is a block that may or may not be associated with B. This method ensures it will not be, when complete.
	 * @param b B is a block that may or may not be associated with A. This method ensures it will not be, when complete.
	 */
	public void removeAssociation(Block a, Block b)
	{
		HashSet<Block> equalToA = farMineEquivalencies.get(a);
		HashSet<Block> equalToB = farMineEquivalencies.get(b);
		if(equalToA!=null)
		{
			equalToA.remove(b);
		}
		if(equalToB!=null)
		{
			equalToB.remove(a);
		}
	}
}
