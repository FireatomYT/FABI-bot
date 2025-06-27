package dev.fireatom.FABI;

import ch.qos.logback.classic.ClassicConstants;
import dev.fireatom.FABI.objects.ExitCodes;
import dev.fireatom.FABI.utils.ConsoleColor;
import org.apache.commons.cli.*;

import java.io.IOException;

public class Main {
  public static void main(String[] args) throws IOException {
    Options options = new Options()
      .addOption("h", "help", false, "Displays this help menu.")
      .addOption("v", "version", false, "Displays the current version of the application.")
      .addOption("sc", "shard-count", true, "Sets the amount of shards the bot should start up.")
      .addOption("s", "shards", true, "Sets the shard IDs that should be started up, the shard IDs should be formatted by the lowest shard ID to start up, and the highest shard ID to start up, separated by a dash.\nExample: \"--shards=4-9\" would start up shard 4, 5, 6, 7, 8, and 9.")
      .addOption("nocolor", "no-colors", false, "Disables colors for commands in the terminal.")
      .addOption("d", "debug", false, "Enables debugging mode, this will log extra information to the terminal.");

    DefaultParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();

    try {
      CommandLine cmd = parser.parse(options, args);

      Settings settings = new Settings(cmd, args);
      ConsoleColor.setSettings(settings);
      if (!settings.useColors()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback_nocolor.xml" + (
          settings.useDebugging() ? "_debug" : ""
        ) + ".xml");
      } else if (settings.useDebugging()) {
        System.setProperty(ClassicConstants.CONFIG_FILE_PROPERTY, "logback_debug.xml");
      }

      if (cmd.hasOption("help")) {
        formatter.printHelp("Help menu", options);
        System.exit(ExitCodes.NORMAL.code);
      } else if (cmd.hasOption("version")) {
        System.out.println(AppInfo.getVersionInfo());
        System.exit(ExitCodes.NORMAL.code);
      }

      App.instance = new App(settings);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      formatter.printHelp("", options);

      System.exit(ExitCodes.NORMAL.code);
    }
  }
}
