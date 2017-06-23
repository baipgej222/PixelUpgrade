package rs.expand.pixelupgrade.configs;

import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

import rs.expand.pixelupgrade.PixelUpgrade;

import java.nio.file.*;
import java.util.Objects;

public class CheckTypesConfig
{
    private CommentedConfigurationNode config;
    private static CheckTypesConfig instance = new CheckTypesConfig();
    public static CheckTypesConfig getInstance()
    {   return instance;    }

    private String separator = FileSystems.getDefault().getSeparator();
    private String path = "config" + separator + "PixelUpgrade" + separator;

    // Called during initial setup, either when the server is booting up or when /pureload has been executed.
    public String loadOrCreateConfig(Path checkPath, ConfigurationLoader<CommentedConfigurationNode> configLoader)
    {
        if (Files.notExists(checkPath))
        {
            try
            {
                PixelUpgrade.log.info("\u00A7eNo \"/checktypes\" configuration file found, creating...");
                Path targetLocation = Paths.get(path, "CheckTypes.conf");
                // Fetching files from the .jar is tough! But this will survive Github, at least.
                Files.copy(getClass().getResourceAsStream("/assets/CheckTypes.conf"), targetLocation);
                config = configLoader.load();
            }
            catch (Exception F)
            {
                PixelUpgrade.log.info("\u00A74Error during initial setup of config for command \"/checktypes\"!");
                PixelUpgrade.log.info("\u00A7cPlease report this, along with any useful info you may have (operating system?). Stack trace follows:");
                F.printStackTrace();
            }

            return "type";
        }
        else
        {
            try
            {
                config = configLoader.load();
                String alias = getConfig().getNode("commandAlias").getString();

                if (!Objects.equals(alias, null))
                {
                    PixelUpgrade.log.info("\u00A7aLoaded existing config for command \"/checktypes\", alias \"" + alias + "\"");
                    return alias;
                }
                else
                {
                    PixelUpgrade.log.info("\u00A7cCheckTypes: Could not read command variable \u00A74\"commandAlias\"\u00A7c, setting defaults.");
                    PixelUpgrade.log.info("\u00A7cCheckTypes: Check this command's config, or wipe it and \u00A74/pureload\u00A7c.");
                    return "type";
                }
            }
            catch (Exception F)
            {
                PixelUpgrade.log.info("\u00A7cError during config loading for command \"/checktypes\"!");
                PixelUpgrade.log.info("\u00A7cPlease make sure this config is formatted correctly. Stack trace follows:");
                F.printStackTrace();
                return "type";
            }
        }
    }

    public CommentedConfigurationNode getConfig()
    {   return config;  }
}