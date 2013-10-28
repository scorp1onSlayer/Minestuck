package com.mraof.minestuck.network;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.mraof.minestuck.Minestuck;
import com.mraof.minestuck.entity.EntityDecoy;
import com.mraof.minestuck.util.EditHandler;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetworkManager;
import net.minecraft.server.MinecraftServer;

import cpw.mods.fml.common.network.Player;

public class SburbEditPacket extends MinestuckPacket {
	
	String username;
	String target;
	boolean mode;	//Not really necessary, but securer that way
	
	public SburbEditPacket() {
		super(Type.SBURB_EDIT);
	}

	@Override
	public byte[] generatePacket(Object... data) {
		return data.length == 0?new byte[0]:	//Client pressing the exit button
			data.length == 1?((String)data[0]).getBytes():	//Server telling client to activate/deactivate edit mode
				(data[0].toString()+'\n'+data[1].toString()).getBytes();	//Client requesting to enter edit mode
	}

	@Override
	public MinestuckPacket consumePacket(byte[] data) {
		try{
			ByteArrayDataInput input = ByteStreams.newDataInput(data);
			username = input.readLine();
			target = input.readLine();
		} catch(IllegalStateException e){}
		
		if(target == null){
			target = username;
			username = null;
		}
		
		return this;
	}

	@Override
	public void execute(INetworkManager network, MinestuckPacketHandler minestuckPacketHandler, Player player, String userName) {
		if(((EntityPlayer)player).worldObj.isRemote) {
			EditHandler.onClientPackage(target);
		} else {
			EntityPlayerMP playerMP = (EntityPlayerMP)player;
			if(username == null)
				EditHandler.onPlayerExit(playerMP);
			if(!Minestuck.privateComputers || playerMP.username.equals(this.username))
				EditHandler.newServerEditor(playerMP, username, target);
		}
	}

}
