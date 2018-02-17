package simplepets.brainsynder.nms.pathfinders.v1_12_R1;

import net.minecraft.server.v1_12_R1.PathfinderGoal;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import simple.brainsynder.math.MathUtils;
import simplepets.brainsynder.api.entity.IEntityPet;
import simplepets.brainsynder.api.event.pet.PetMoveEvent;
import simplepets.brainsynder.nms.entities.v1_12_R1.EntityPet;
import simplepets.brainsynder.player.PetOwner;

import java.util.Arrays;
import java.util.List;

public class PathFinderGoalWalkToPlayer extends PathfinderGoal {

    public IEntityPet pet;
    protected double speed;
    public PetOwner owner;
    private boolean isFirst;
    public Location location;
    private double teleportDistance = 10.0;
    private double stopDistance = 3.0;
    private List<Double> ints = Arrays.asList(1.9, -1.9);

    PathFinderGoalWalkToPlayer(IEntityPet entitycreature, Player p, double speed) {
        this.pet = entitycreature;
        this.speed = speed;
        isFirst = true;
        owner = PetOwner.getPetOwner(p);
        if (owner.getPet().getVisableEntity().isBig()) {
            ints = Arrays.asList(2.9, -2.9);
            stopDistance = 7.0;
            teleportDistance = 20.0;
        }
    }

    @Override
    public boolean a() {
        if (pet != null) {
            if (owner.getPlayer().isOnline()) {
                if (!owner.getPlayer().isInsideVehicle() && owner.hasPet()) {
                    Location start = owner.getPlayer().getLocation();
                    Entity petEntity = pet.getEntity();
                    if (petEntity.getWorld().getName().equals(start.getWorld().getName())) {
                        if ((petEntity.getLocation().distance(start) >= teleportDistance)) {
                            petEntity.teleport(start);
                            return false;
                        }
                    } else {
                        petEntity.teleport(start);
                        return false;
                    }
                    int x = MathUtils.random(ints.size());
                    int z = MathUtils.random(ints.size());

                    if (isFirst) {
                        location = new Location(start.getWorld(), start.getX() + x, start.getY(), start.getZ() + z);
                        isFirst = false;
                        this.c();
                        return true;
                    }
                    if ((pet.getEntity().getLocation().distance(start) >= stopDistance)) {
                        location = new Location(start.getWorld(), start.getX() + x, start.getY(), start.getZ() + z);
                    }
                    this.c();
                    return location != null;
                }
            }
        }
        return false;
    }

    @Override
    public boolean b() { // Executed when the mob isn't working with its path
        return !((EntityPet) pet).getNavigation().o();
    }

    @Override
    public void c() {
        if (owner.hasPet()) {
            if (owner.getPet().getEntity() != null) {
                if (pet == null) {
                    if (pet.getEntity() != null) pet.getEntity().remove();
                    return;
                }

                if (pet.getOwner() == null) {
                    if (pet.getEntity() != null) pet.getEntity().remove();
                    return;
                }

                try {
                    PetMoveEvent event = new PetMoveEvent(pet, PetMoveEvent.Cause.FOLLOW);
                    Bukkit.getServer().getPluginManager().callEvent(event);
                }catch (Throwable ignored) {}
            }
          //  PathEntity path = ((EntityPet) pet).getNavigation().a(location.getX(), location.getY(), location.getZ());
            ((EntityPet) pet).getNavigation().a(location.getX(), location.getY(), location.getZ(), speed);
        }
    }
}

