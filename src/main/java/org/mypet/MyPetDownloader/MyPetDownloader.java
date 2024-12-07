package org.mypet.MyPetDownloader;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyPetDownloader extends JavaPlugin {
    public class Download {
        String version;
        int build;
        String downloadURL;

        public Download(String version, int build, String downloadURL) {
            this.version = version;
            this.build = build;
            this.downloadURL = downloadURL;
        }

        public String getVersion() {
            return version;
        }

        public int getBuild() {
            return build;
        }

        public String getDownloadURL() { return downloadURL; }

        @Override
        public String toString() {
            return version + "-B" + build;
        }
    }

    private static final Pattern PACKAGE_VERSION_MATCHER = Pattern.compile(".*\\.(v\\d+_\\d+_R\\d+)(?:.+)?");
    private static final Pattern MINECRAFT_VERSION_MATCHER = Pattern.compile("\\(MC: (\\d\\.\\d+(?:\\.\\d+)?)");
    private static final Pattern VERSION_MATCHER = Pattern.compile("\\d\\.\\d+(?:\\.\\d+)?");

    private Map<String, Integer> compareCache = new HashMap<>();
    private String minecraftVersion = "0.0.0";
    protected static String internalVersion;

    protected static Download latest = null;
    protected File pluginFile = null;
    protected Thread thread;
    protected String plugin;

    public void onLoad() {
        JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MyPet");
        if(plugin != null && !plugin.isEnabled())
            return;


        Matcher regexMatcher = PACKAGE_VERSION_MATCHER.matcher(Bukkit.getServer().getClass().getCanonicalName());
        if (regexMatcher.find()) {
            internalVersion = regexMatcher.group(1);
        }
        regexMatcher = MINECRAFT_VERSION_MATCHER.matcher(Bukkit.getVersion());
        if (regexMatcher.find()) {
            minecraftVersion = regexMatcher.group(1);
        }

        // Paper 1.20.6+ works differently - yay
        if (internalVersion == null) {
            internalVersion = GetBukkitVersionFromMinecraftVersion();
        }

        Runnable generalDownloadRunner = () -> {
            Optional<Download> download = check();
            if(download.isPresent()) {
                latest = download.get();
                download();
            }
        };

        generalDownloadRunner.run();

        if(pluginFile != null) {
            try {
                Bukkit.getPluginManager().loadPlugin(pluginFile);
                //If it's pure spigot or paper before 1.19.3 we need to call onLoad manually
                if(Bukkit.getServer().getVersion().toUpperCase().contains("SPIGOT") ||
                    compareWithMinecraftVersion("1.19.3") < 0) {
                    Bukkit.getPluginManager().getPlugin("MyPet").onLoad();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String GetBukkitVersionFromMinecraftVersion() {
        HashMap<String, String> versionMap = new HashMap<>();
        BufferedReader donation = null;
        int timeout = 2000;
        try {
            URL url = new URL("https://raw.githubusercontent.com/MyPetORG/MyPet/versionmatcher/versionmatcher.csv");
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setConnectTimeout(timeout);
            huc.setReadTimeout(timeout);
            huc.setRequestMethod("GET");
            huc.connect();
            donation = new BufferedReader(new InputStreamReader(huc.getInputStream()));

            String line;
            while ((line = donation.readLine()) != null) {
                String[] parts = line.split(",");
                versionMap.put(parts[0], parts[1]);
            }
        } catch(Exception ignored) {
        }

        String bukkitVersion = null;
        if (versionMap.containsKey(minecraftVersion))
            bukkitVersion = versionMap.get(minecraftVersion);
        return bukkitVersion;
    }

    private Optional<Download> check() {
        try {
            String url = "https://api.github.com/repos/MyPetORG/MyPet/releases";

            // no data will be saved on the server
            String content = Util.readUrlContent(url);
            JsonArray resultArr = new Gson().fromJson(content, JsonArray.class);

            for (int i = 0; i<resultArr.size(); i++) {
                JsonObject release = (JsonObject) resultArr.get(i);
                String rawVersion = release.get("name").getAsString();

                String[] split = rawVersion.split("-");

                int build = Integer.parseInt(split[split.length-1].substring(1));

                if(!release.get("body").getAsString().contains(internalVersion)) { //MC-Version not supported
                    Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "This version of Minecraft is not supported by the newest builds of MyPet!");
                    return Optional.empty();
                }

                String version = "";
                for(int j = 0; j<split.length-1;j++) {
                    version+=split[j];
                    if(j<split.length-2) {
                        version+="-";
                    }
                }

                String downloadURL = release.get("assets").getAsJsonArray().get(0).getAsJsonObject().get("browser_download_url").getAsString();
                return Optional.of(new Download(version, build, downloadURL));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private void download() {
        //Download
        pluginFile = new File(this.getFile().getParentFile().getAbsolutePath(),"MyPet-"+latest+".jar");

        String finalUrl = latest.getDownloadURL();
        Runnable downloadRunner = () -> {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Start MyPet download: " + ChatColor.RESET + latest);

            try {
                URL website = new URL(finalUrl);
                HttpURLConnection httpConn = (HttpURLConnection) website.openConnection();
                int responseCode = httpConn.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    httpConn.disconnect();
                    return;
                }
                InputStream inputStream = httpConn.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(pluginFile);
                Hasher hasher = Hashing.sha256().newHasher();

                int bytesRead;
                byte[] buffer = new byte[4096];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    hasher.putBytes(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();
            } catch (IOException e) {
                return;
            }
        };
        downloadRunner.run();
    }

    private int compareWithMinecraftVersion(String version) {
        if (VERSION_MATCHER.matcher(version).find()) {
            if (compareCache.containsKey(minecraftVersion + "-::-" + version)) {
                return compareCache.get(minecraftVersion + "-::-" + version);
            }
            int compare = Util.versionCompare(minecraftVersion, version);
            compareCache.put(minecraftVersion + "-::-" + version, compare);
            return compare;
        }
        throw new IllegalArgumentException("\"version\" must be a valid Minecraft version. \"" + version + "\" given.");
    }
}
