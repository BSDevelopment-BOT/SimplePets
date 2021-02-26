package simplepets.brainsynder.sql;

import com.google.common.collect.Maps;
import lib.brainsynder.files.StorageFile;
import lib.brainsynder.json.Json;
import lib.brainsynder.json.JsonArray;
import lib.brainsynder.json.JsonValue;
import lib.brainsynder.nbt.JsonToNBT;
import lib.brainsynder.nbt.StorageTagCompound;
import lib.brainsynder.nbt.StorageTagList;
import lib.brainsynder.nbt.StorageTagString;
import lib.brainsynder.nbt.other.NBTException;
import lib.brainsynder.utils.Base64Wrapper;
import org.bukkit.scheduler.BukkitRunnable;
import simplepets.brainsynder.PetCore;
import simplepets.brainsynder.api.user.PetUser;
import simplepets.brainsynder.impl.PetOwner;
import simplepets.brainsynder.utils.Debug;
import simplepets.brainsynder.utils.DebugLevel;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerSQL extends SQLManager {
    private static PlayerSQL instance;
    private final Map<UUID, StorageTagCompound> dataCache = Maps.newHashMap();

    public PlayerSQL () {
        super(false);
        instance = this;
    }

    public static PlayerSQL getInstance() {
        return instance;
    }

    @Override
    public void createTable() {
        CompletableFuture.runAsync(() -> {
            try {
                String table = "CREATE TABLE IF NOT EXISTS "+tablePrefix + "_players (" +
                        "`uuid` VARCHAR(265) NOT NULL," +
                        "`name` VARCHAR(265) NOT NULL," +
                        "`UnlockedPets` "+getStupidTextThing()+" NOT NULL," +
                        "`PetName` "+getStupidTextThing()+" NOT NULL," +
                        "`NeedsRespawn` "+getStupidTextThing()+" NOT NULL," +
                        "`SavedPets` "+getStupidTextThing()+" NOT NULL" +
                        ")";
                PreparedStatement createTable = getConnection().prepareStatement(
                        table
                );

                createTable.executeUpdate();
                createTable.close();
            } catch (SQLException exception) {
                exception.printStackTrace();
            }
            transferOldData();

            Map<UUID, StorageTagCompound> cache = new HashMap<>();
            try {
                PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM `" + tablePrefix + "_players`");
                ResultSet results = statement.executeQuery();
                while (results.next()) {
                    String uuid = results.getString("uuid");
                    try {
                        StorageTagCompound compound = new StorageTagCompound();

                        // Loads the pets the player purchased
                        try {
                            compound.setTag("owned_pets", JsonToNBT.getTagFromJson(Base64Wrapper.decodeString(results.getString("UnlockedPets"))));
                        } catch (NBTException e) {
                            Debug.debug(DebugLevel.ERROR, "Failed to load 'UnlockedPets' for uuid: "+uuid, true);
                            Debug.debug(DebugLevel.ERROR, "Result: "+results.getString("UnlockedPets"), true);
                        }

                        // Loads pet names
                        String rawName = results.getString("PetName");
                        if (Base64Wrapper.isEncoded(rawName)) {
                            rawName = Base64Wrapper.decodeString(rawName);
                            try {
                                compound.setTag("pet_names", JsonToNBT.parse(rawName).toList());
                            } catch (NBTException e) {
                                Debug.debug(DebugLevel.ERROR, "Failed to read name data: "+rawName, true);
                                // Old pet name save... not supported in the new system
                            }
                        }

                        String spawnedPets = results.getString("NeedsRespawn");
                        if (Base64Wrapper.isEncoded(spawnedPets)) {
                            spawnedPets = Base64Wrapper.decodeString(spawnedPets);
                            StorageTagList pets = new StorageTagList();
                            try {
                                JsonToNBT parser = JsonToNBT.parse(spawnedPets);

                                if (spawnedPets.startsWith("[")) {
                                    // New system
                                    parser.toList().getList().forEach(storageBase -> {
                                        StorageTagCompound tag = (StorageTagCompound) storageBase;
                                        if (!tag.hasKey("type")) {
                                            if (tag.hasKey("data")) {
                                                tag.setString("type", tag.getCompoundTag("data").getString("PetType"));
                                                pets.appendTag(tag);
                                            }
                                            // Ignore the other values because it is not formatted correctly
                                        }else{
                                            pets.appendTag(storageBase);
                                        }
                                    });
                                    compound.setTag("spawned_pets", pets);
                                }else{
                                    // Old system of saving 1 pet
                                    StorageTagCompound tag = parser.toCompound();
                                    compound.setTag("spawned_pets", pets.appendTag(new StorageTagCompound().setString("type", tag.getString("PetType")).setTag("data", tag)));
                                }
                            } catch (NBTException e) {
                                // Old pet name save... not supported in the new system
                            }
                        }

                        cache.put(UUID.fromString(uuid), compound);
                        // Cache the data...
                    } catch (NullPointerException | IllegalArgumentException ex) {
                        // Failed...
                    }
                }
                results.close();
                statement.close();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        dataCache.putAll(cache);
                    }
                }.runTask(PetCore.getInstance());
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });
    }

    public StorageTagCompound getCache (UUID uuid) {
        return dataCache.getOrDefault(uuid, new StorageTagCompound());
    }

    @Override
    public void transferOldData() {
        // TODO: Need to transfer the old data from the files
        File folder = new File(PetCore.getInstance().getDataFolder() + File.separator+"PlayerData");
        if (!folder.exists()) return;
        if (folder.listFiles() == null) return;
        if (folder.listFiles().length == 0) return;
        Arrays.asList(folder.listFiles()).forEach(file -> {
            String uuid = file.getName().replace(".stc", "");
            StorageFile storage = new StorageFile(file);
            StorageTagCompound compound = new StorageTagCompound();
            compound.setUniqueId("uuid", UUID.fromString(uuid));
            compound.setString("name", storage.getString("username"));

            String respawn = storage.getString("NeedsRespawn");
            if ((respawn != null) && (!respawn.equalsIgnoreCase("null"))) {
                if (Base64Wrapper.isEncoded(respawn)) {
                    // They have a pet to respawn
                    try {
                        StorageTagCompound compound1 = new StorageTagCompound();
                        StorageTagCompound tag = JsonToNBT.getTagFromJson(Base64Wrapper.decodeString(respawn));
                        compound1.setTag("data", tag);
                        compound1.setString("type", tag.getString("PetType"));
                        compound.setTag("spawned_pets", new StorageTagList().appendTag(compound1));
                    } catch (NBTException e) {
                        e.printStackTrace();
                    }
                }
            }

            StorageTagList ownedPets = new StorageTagList();
            if (storage.getTag("PurchasedPets") instanceof StorageTagList) {
                // Was saved as StorageTagList
                ownedPets = (StorageTagList) storage.getTag("PurchasedPets");
            } else {
                // Was saved in the old format
                String raw = storage.getString("PurchasedPets");
                String decoded = Base64Wrapper.decodeString(raw);
                JsonArray array = (JsonArray) Json.parse(decoded);
                for (JsonValue value : array.values()) {
                    String name = value.asString();
                    ownedPets.appendTag(new StorageTagString(name));
                }
            }
            compound.setTag("owned_pets", ownedPets);
            compound.setTag("pet_names", new StorageTagList());

            {
                // Saves the Inventory data to the new sql
                String data = storage.getString("ItemStorage");
                if (Base64Wrapper.isEncoded(data)) {
                    try {
                        InventorySQL.getInstance().uploadData(UUID.fromString(uuid), JsonToNBT.getTagFromJson(Base64Wrapper.decodeString(data)));
                    } catch (NBTException ignored) {}
                }
            }
            // Delete the file after the data transfer
            file.delete();
        });
    }

    public void fetchData (UUID uuid, SQLCallback<StorageTagCompound> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM " + tablePrefix + "_players WHERE uuid = ?");
                statement.setString(1, uuid.toString());
                ResultSet results = statement.executeQuery();
                if (results.next()) {
                    try {
                        StorageTagCompound compound = new StorageTagCompound();

                        // Loads the pets the player purchased
                        try {
                            compound.setTag("owned_pets", JsonToNBT.getTagFromJson(Base64Wrapper.decodeString(results.getString("UnlockedPets"))));
                        } catch (NBTException e) {
                            Debug.debug(DebugLevel.ERROR, "Failed to load 'UnlockedPets' for uuid: "+uuid.toString());
                            Debug.debug(DebugLevel.ERROR, "Result: "+results.getString("UnlockedPets"));
                        }

                        // Loads pet names
                        String rawName = results.getString("PetName");
                        if (Base64Wrapper.isEncoded(rawName)) {
                            rawName = Base64Wrapper.decodeString(rawName);
                            try {
                                compound.setTag("pet_names", JsonToNBT.parse(rawName).toList());
                            } catch (NBTException e) {
                                // Old pet name save... not supported in the new system
                            }
                        }

                        String spawnedPets = results.getString("NeedsRespawn");
                        if (Base64Wrapper.isEncoded(spawnedPets)) {
                            spawnedPets = Base64Wrapper.decodeString(spawnedPets);
                            StorageTagList pets = new StorageTagList();
                            try {
                                JsonToNBT parser = JsonToNBT.parse(spawnedPets);

                                if (spawnedPets.startsWith("[")) {
                                    // New system
                                    parser.toList().getList().forEach(storageBase -> {
                                        StorageTagCompound tag = (StorageTagCompound) storageBase;
                                        if (!tag.hasKey("type")) {
                                            if (tag.hasKey("data")) {
                                                tag.setString("type", tag.getCompoundTag("data").getString("PetType"));
                                                pets.appendTag(tag);
                                            }
                                            // Ignore the other values because it is not formatted correctly
                                        }else{
                                            pets.appendTag(storageBase);
                                        }
                                    });
                                    compound.setTag("spawned_pets", pets);
                                }else{
                                    // Old system of saving 1 pet
                                    StorageTagCompound tag = parser.toCompound();
                                    compound.setTag("spawned_pets", pets.appendTag(new StorageTagCompound().setString("type", tag.getString("PetType")).setTag("data", tag)));
                                }
                            } catch (NBTException e) {
                                // Old pet name save... not supported in the new system
                            }
                        }

                        callback.onSuccess(compound);
                        return;
                    } catch (NullPointerException | IllegalArgumentException ex) {
                        callback.onFail();
                    }
                }
                results.close();
                statement.close();
                callback.onFail();
            } catch (SQLException exception) {
                exception.printStackTrace();
                callback.onFail();
            }
        });
    }

    public void uploadData (PetUser user) {
        isPlayerInDatabase(user.getPlayer().getUniqueId(), data -> {
            if (data) {
                updateData(user, data1 -> {});
            }else{
                insertData(user, data1 -> {});
            }
        });
    }


    public void updateData(PetUser user, SQLCallback<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                /*
                                "`uuid` VARCHAR(265) NOT NULL,\n" +
                                "`name` VARCHAR(265) NOT NULL,\n" +
                                "`UnlockedPets` MEDIUMTEXT NOT NULL DEFAULT '',\n" +
                                "`PetName` LONGTEXT NOT NULL DEFAULT '',\n" +
                                "`NeedsRespawn` LONGTEXT NOT NULL DEFAULT '',\n" +
                                "`SavedPets` LONGTEXT NOT NULL DEFAULT ''\n" +
                 */
                PreparedStatement statement = getConnection().prepareStatement("UPDATE `" + tablePrefix + "_players` SET " +
                        "name=?, UnlockedPets=?, PetName=?, NeedsRespawn=?, SavedPets=? WHERE uuid = ?");
                statement.setString(1, user.getPlayer().getName());
                StorageTagCompound compound = ((PetOwner)user).toCompound();

                statement.setString(2, Base64Wrapper.encodeString(compound.getTag("owned_pets").toString()));
                statement.setString(3, Base64Wrapper.encodeString(compound.getTag("pet_names").toString()));
                statement.setString(4, Base64Wrapper.encodeString(compound.getTag("spawned_pets").toString()));
                statement.setString(5, Base64Wrapper.encodeString(compound.getTag("saved_pets").toString()));
                statement.setString(6, user.getPlayer().getUniqueId().toString());
                statement.executeUpdate();
                statement.close();
                if (callback != null) {
                    callback.onSuccess(true);
                }
            } catch (SQLException exception) {
                callback.onFail();
                exception.printStackTrace();
            }
        });
    }

    public void insertData(PetUser user, SQLCallback<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement statement = getConnection().prepareStatement(
                        "INSERT INTO `" + tablePrefix + "_players` (`uuid`, `name`, `UnlockedPets`, `PetName`, `NeedsRespawn`, `SavedPets`) VALUES (?, ?, ?, ?, ?, ?)");

                statement.setString(1, user.getPlayer().getUniqueId().toString());
                statement.setString(2, user.getPlayer().getName());

                StorageTagCompound compound = ((PetOwner)user).toCompound();
                statement.setString(3, Base64Wrapper.encodeString(compound.getTag("owned_pets").toString()));
                statement.setString(4, Base64Wrapper.encodeString(compound.getTag("pet_names").toString()));
                statement.setString(5, Base64Wrapper.encodeString(compound.getTag("spawned_pets").toString()));
                statement.setString(6, Base64Wrapper.encodeString(compound.getTag("saved_pets").toString()));
                statement.executeUpdate();
                statement.close();
                callback.onSuccess(true);
            } catch (SQLException exception) {
                callback.onFail();
                exception.printStackTrace();
            }
        });
    }

    public void isPlayerInDatabase(UUID uuid, SQLCallback<Boolean> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                PreparedStatement statement = getConnection().prepareStatement("SELECT * FROM `" + tablePrefix + "_players` WHERE uuid = ?");
                statement.setString(1, uuid.toString());
                ResultSet results = statement.executeQuery();
                boolean next = results.next();

                callback.onSuccess(next);

                results.close();
                statement.close();
            } catch (SQLException exception) {
                callback.onFail();
                exception.printStackTrace();
            }
        });
    }
}
