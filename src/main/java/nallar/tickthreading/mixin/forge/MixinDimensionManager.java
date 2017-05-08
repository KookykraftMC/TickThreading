package nallar.tickthreading.mixin.forge;

import me.nallar.mixin.Add;
import me.nallar.mixin.Mixin;
import me.nallar.mixin.OverrideStatic;
import nallar.tickthreading.log.Log;
import nallar.tickthreading.reporting.LeakDetector;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import java.util.*;

@Mixin
public abstract class MixinDimensionManager extends DimensionManager {
	@OverrideStatic
	public static void unloadWorlds(@SuppressWarnings({"UseOfObsoleteCollectionType", "unused"}) Hashtable<Integer, long[]> worldTickTimes) {
		if (unloadQueue.isEmpty()) {
			return;
		}
		//noinspection SynchronizationOnStaticField
		//noinspection SynchronizeOnNonFinalField
		synchronized (unloadQueue) {
			/*
			TODO: new config system isn't in yet
			if (!TickThreading.instance.allowWorldUnloading) {
				unloadQueue.clear();
				return;
			}
			*/
			for (int id : unloadQueue) {
				unloadWorldImmediately(worlds.get(id));
			}
			unloadQueue.clear();
			Log.checkWorlds();
			weakWorldMap.clear(); // We do our own leak checking.
		}
	}

	@Add
	protected static boolean unloadWorldImmediately(WorldServer w) {
		if (w == null || !worlds.containsValue(w) || !w.getPersistentChunks().isEmpty() || !w.playerEntities.isEmpty()) {
			return false;
		}

		try {
			w.saveAllChunks(true, null);
		} catch (net.minecraft.world.MinecraftException ex) {
			Log.error("Failed to save world " + w.getName() + " while unloading it.");
		}
		try {
			MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(w));
		} catch (Throwable t) {
			Log.error("A mod failed to handle unloading the world " + w.getName(), t);
		}
		setWorld(w.getDimensionId(), null, w.getMinecraftServer());
		try {
			w.flush();
		} catch (Throwable t) {
			Log.error("Failed to flush changes when unloading world", t);
		}
		LeakDetector.scheduleLeakCheck(w, w.getName(), true);
		return true;
	}
}
