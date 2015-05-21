package com.mraof.minestuck.world.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.mraof.minestuck.client.ClientProxy;
import com.mraof.minestuck.network.skaianet.SkaianetHandler;
import com.mraof.minestuck.util.MinestuckPlayerData;
import com.mraof.minestuck.world.MinestuckDimensionHandler;
import com.mraof.minestuck.world.lands.LandAspectRegistry.AspectCombination;
import com.mraof.minestuck.tileentity.TileEntityTransportalizer;

public class MinestuckSaveHandler 
{
	
	public static final int MAJOR_VERSION = 2;	//The major version between updates. Only save files with the same major version is compatible
	public static final int MINOR_VERSION = 0;	//The minor version. Used when newer versions can handle old saves but not vice versa
	
	@SubscribeEvent
	public void onWorldSave(WorldEvent.Save event)
	{
		if(event.world.provider.getDimensionId() != 0)	//Only save one time each world-save instead of one per dimension each world-save.
			return;

		File dataFile = event.world.getSaveHandler().getMapFileFromName("MinestuckData");
		if (dataFile != null)
		{
			NBTTagCompound nbt = new NBTTagCompound();
			
			nbt.setInteger("majorVersion", MAJOR_VERSION);
			nbt.setInteger("minorVersion", MINOR_VERSION);
			
			MinestuckDimensionHandler.saveData(nbt);
			
			TileEntityTransportalizer.saveTransportalizers(nbt);
			SkaianetHandler.saveData(nbt);
			MinestuckPlayerData.writeToNBT(nbt);
			
			try {
				CompressedStreamTools.writeCompressed(nbt, new FileOutputStream(dataFile));
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	public static void onWorldLoad(ISaveHandler saveHandler)
	{
		File dataFile = saveHandler.getMapFileFromName("MinestuckData");
		if(dataFile != null && dataFile.exists())
		{
			NBTTagCompound nbt = null;
			try
			{
				nbt = CompressedStreamTools.readCompressed(new FileInputStream(dataFile));
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			if(nbt != null)
			{
				
				int major, minor;
				if(nbt.hasKey("majorVersion"))
				{
					major = nbt.getInteger("majorVersion");
					minor = nbt.getInteger("minorVersion");
				} else
				{
					minor = 0;
					if(nbt.hasKey("dimensionData") || nbt.hasKey("landList") && nbt.getTagList("landList", 10).tagCount() == 0)
						major = 1;
					else major = 0;
				}
				
				if(MAJOR_VERSION > major)
				{
					if(MinecraftServer.getServer().isDedicatedServer())
						FMLLog.warning("[Minestuck] This save file is outdated and might cause issues.");
					else if(!ClientProxy.openYesNoGui("", ""))
						throw new IllegalStateException("Outdated save should not be loaded");
				}
				else if(MAJOR_VERSION < major || MINOR_VERSION < minor)
				{
					if(MinecraftServer.getServer().isDedicatedServer() || ClientProxy.openYesNoGui("", ""))
						FMLLog.warning("[Minestuck] This minestuck version is outdated and might cause issues.");
					else throw new IllegalStateException("Save file comes from a newer version");
				}
				
				MinestuckDimensionHandler.loadData(nbt);
				
				SkaianetHandler.loadData(nbt.getCompoundTag("skaianet"));
				
				MinestuckPlayerData.readFromNBT(nbt);

				TileEntityTransportalizer.loadTransportalizers(nbt.getCompoundTag("transportalizers"));
				
				return;
			}
		}
		
		SkaianetHandler.loadData(null);
		MinestuckPlayerData.readFromNBT(null);
	}
	
}
