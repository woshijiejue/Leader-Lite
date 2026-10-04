package leader.util;

import leader.module.modules.render.BetterFPS;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Render-only snapshot cache. Combat/placement queries must not use this cache. */
final class RenderEntityCache {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static World world;
    private static Entity camera;
    private static int tick = -1, count = -1;
    private static long nextRefresh;
    private static final Map<Class<?>, List<?>> snapshots = new HashMap<>();

    private RenderEntityCache() { }

    static List<Entity> entities() { return entities(Entity.class); }

    static List<EntityPlayer> players() { return entities(EntityPlayer.class); }

    static List<Entity> projectiles() { return query(Entity.class, true); }

    @SuppressWarnings("unchecked")
    static <T extends Entity> List<T> entities(Class<T> type) {
        return query(type, false);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> List<T> query(Class<T> type, boolean projectiles) {
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) {
            snapshots.clear(); world = null; camera = null;
            return Collections.emptyList();
        }
        boolean cache = BetterFPS.cachedRenderLists();
        long now = System.nanoTime();
        int currentTick = mc.thePlayer == null ? -1 : mc.thePlayer.ticksExisted;
        int currentCount = mc.theWorld.loadedEntityList.size();
        if (!cache || world != mc.theWorld || camera != mc.getRenderViewEntity() || count != currentCount
                || now >= nextRefresh || (!BetterFPS.aggressive() && tick != currentTick)) {
            snapshots.clear();
            world = mc.theWorld; camera = mc.getRenderViewEntity(); count = currentCount; tick = currentTick;
            nextRefresh = now + (BetterFPS.aggressive() ? 50000000L : 25000000L);
        }
        Class<?> key = projectiles ? ProjectileGroup.class : type;
        List<T> cached = (List<T>) snapshots.get(key);
        if (cache && cached != null) return cached;
        List<?> source = type == EntityPlayer.class ? mc.theWorld.playerEntities : mc.theWorld.loadedEntityList;
        List<Entry<T>> sorted = new ArrayList<>();
        for (Object value : source) {
            if (type.isInstance(value) && (!projectiles || isProjectile((Entity) value))) sorted.add(new Entry<>(type.cast(value)));
        }
        sorted.sort((a, b) -> {
            int distance = Double.compare(b.distance, a.distance);
            return distance != 0 ? distance : a.id.compareTo(b.id);
        });
        List<T> result = new ArrayList<>(sorted.size());
        for (Entry<T> entry : sorted) result.add(entry.entity);
        List<T> view = Collections.unmodifiableList(result);
        if (cache) snapshots.put(key, view);
        return view;
    }

    private static boolean isProjectile(Entity e) {
        return e instanceof EntityFireball || e instanceof EntityEnderPearl || e instanceof EntityArrow
                || e instanceof EntityEgg || e instanceof EntitySnowball;
    }

    private static final class ProjectileGroup { }

    private static final class Entry<T extends Entity> {
        final T entity;
        final double distance;
        final String id;
        Entry(T entity) {
            this.entity = entity;
            distance = mc.getRenderManager().getDistanceToCamera(entity.posX, entity.posY, entity.posZ);
            id = entity.getUniqueID().toString();
        }
    }
}
