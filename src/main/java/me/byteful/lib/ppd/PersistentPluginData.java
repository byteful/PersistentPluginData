package me.byteful.lib.ppd;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Main class of PersistentPluginData. */
public class PersistentPluginData {
  /** Instance of GSON used for serialization/deserialization. */
  @NotNull private final Gson gson;
  /** The enum for the location at which the database file is stored. */
  @NotNull private final StorageLocation location;
  /** Instance of the JavaPlugin that initialized this instance of PPD */
  @NotNull private final JavaPlugin plugin;
  /** The name for the database file. */
  @NotNull private final String database;
  /** JsonObject instance that contains all the data. */
  private JsonObject data;

  /**
   * Main constructor for PersistentPluginData.
   *
   * @param gson instance of GSON
   * @param location location enum
   * @param plugin initializing plugin
   * @param database database file name
   * @param autoSave true if PPD should autosave
   */
  public PersistentPluginData(
      @NotNull Gson gson,
      @NotNull StorageLocation location,
      @NotNull JavaPlugin plugin,
      @NotNull String database,
      boolean autoSave) {
    this.gson = gson;
    this.location = location;
    this.plugin = plugin;
    this.database = database;
    try {
      load();
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to load JSON data from: " + getDatabaseFile().getPath(), e);
    }

    if (autoSave) {
      Bukkit.getScheduler()
          .runTaskTimerAsynchronously(
              plugin,
              () -> {
                try {
                  save();
                } catch (IOException e) {
                  throw new RuntimeException(e); // Stop auto-save to prevent console spam.
                }
              },
              0L,
              20L);
    }
  }

  /**
   * Initializes a new instance of PersistentPluginData with the provided parameters.
   *
   * @param plugin the main plugin to bind to
   * @param database the file name for the data to be stored at
   * @param location the location for the data file to be loaded/saved
   * @param autoSave true if PPD should autosave
   * @return instance of PersistentPluginData
   */
  @Contract("_, _, _, _ -> new")
  @NotNull
  public static PersistentPluginData initialize(
      @NotNull JavaPlugin plugin,
      @NotNull String database,
      @NotNull StorageLocation location,
      boolean autoSave) {
    return new PersistentPluginData(
        new GsonBuilder().disableHtmlEscaping().serializeNulls().create(),
        location,
        plugin,
        database,
        autoSave);
  }

  /**
   * PPD method to get data from JsonObject with generics.
   *
   * @param key the key for stored data
   * @param type the class type for value
   * @param <T> type
   * @return Optional with/without the data at provided key
   */
  @NotNull
  public <T> Optional<T> get(@NotNull String key, @NotNull Class<T> type) {
    if (!data.has(key)) {
      return Optional.empty();
    }

    return Optional.ofNullable(gson.fromJson(data.get(key), type));
  }

  /**
   * PPD method to get data from JsonObject with generics.
   *
   * @param key the key for stored data
   * @param typeToken the TypeToken type for value
   * @param <T> type
   * @return Optional with/without the data at provided key
   */
  @NotNull
  public <T> Optional<T> get(@NotNull String key, @NotNull TypeToken<T> typeToken) {
    if (!data.has(key)) {
      return Optional.empty();
    }

    return Optional.ofNullable(gson.fromJson(data.get(key), typeToken.getType()));
  }

  /**
   * PPD method to get data from JsonObject with generics.
   *
   * @param key the key for stored data
   * @param type the type for value
   * @param <T> type
   * @return Optional with/without the data at provided key
   */
  @NotNull
  public <T> Optional<T> get(@NotNull String key, @NotNull Type type) {
    if (!data.has(key)) {
      return Optional.empty();
    }

    return Optional.ofNullable(gson.fromJson(data.get(key), type));
  }

  /**
   * PPD method to set data into JsonObject.
   *
   * @param key the key location to store value at
   * @param value the value to store
   */
  public void set(@NotNull String key, @NotNull Object value) {
    data.add(key, gson.toJsonTree(value));
  }

  /**
   * PPD method to check if data exists at provided key.
   *
   * @param key the key to check at
   * @return true if data exists at provided key, false otherwise
   */
  public boolean exists(@NotNull String key) {
    return data.has(key);
  }

  /**
   * PPD method to delete data at provided key.
   *
   * @param key the key to delete data at
   */
  public void delete(@NotNull String key) {
    data.remove(key);
  }

  /** Clears all data in JsonObject. */
  public void clear() {
    data = new JsonObject();
  }

  @NotNull
  private File getFolder() {
    switch (location) {
      case INSIDE_MAIN_FOLDER:
        return Bukkit.getWorldContainer();
      case INSIDE_PLUGIN_FOLDER:
        return plugin.getDataFolder();
    }

    throw new RuntimeException("Failed to find respective data folder location for: " + location);
  }

  /**
   * Loads data from database file.
   *
   * @throws IOException thrown when reading file fails
   */
  public void load() throws IOException {
    data =
        gson.fromJson(
            new String(Files.readAllBytes(getDatabaseFile().toPath()), StandardCharsets.UTF_8),
            JsonObject.class);
    if (data == null) {
      data = new JsonObject();
    }
  }

  /**
   * Saves data to database file.
   *
   * @throws IOException thrown when writing to file fails
   */
  public void save() throws IOException {
    final Path path = getDatabaseFile().toPath();
    if (!Files.exists(path)) {
      Files.createFile(path);
    }

    Files.write(path, gson.toJson(data).getBytes(StandardCharsets.UTF_8));
  }

  @NotNull
  private File getDatabaseFile() {
    if (!getFolder().exists()) {
      getFolder().mkdirs();
    }

    final File file = new File(getFolder(), database + ".json");

    if (!file.exists()) {
      try {
        file.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return file;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PersistentPluginData that = (PersistentPluginData) o;
    return gson.equals(that.gson)
        && location == that.location
        && plugin.equals(that.plugin)
        && database.equals(that.database);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gson, location, plugin, database);
  }

  @Override
  public String toString() {
    return "PersistentPluginData{"
        + "gson="
        + gson
        + ", location="
        + location
        + ", plugin="
        + plugin
        + ", database='"
        + database
        + '\''
        + '}';
  }
}
