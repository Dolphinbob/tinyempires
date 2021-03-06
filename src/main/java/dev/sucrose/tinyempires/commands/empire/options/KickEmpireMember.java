package dev.sucrose.tinyempires.commands.empire.options;

import dev.sucrose.tinyempires.discord.DiscordBot;
import dev.sucrose.tinyempires.models.CommandOption;
import dev.sucrose.tinyempires.models.Empire;
import dev.sucrose.tinyempires.models.Permission;
import dev.sucrose.tinyempires.models.TEPlayer;
import dev.sucrose.tinyempires.utils.ErrorUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class KickEmpireMember implements CommandOption {

    @Override
    public void execute(Player sender, String[] args) {
        // /e remove <name>
        final UUID senderUUID = sender.getUniqueId();
        final TEPlayer tePlayer = TEPlayer.getTEPlayer(senderUUID);
        if (tePlayer == null) {
            sender.sendMessage(ChatColor.RED + ErrorUtils.YOU_DO_NOT_EXIST_IN_THE_DATABASE);
            return;
        }

        final Empire empire = tePlayer.getEmpire();
        if (empire == null) {
            sender.sendMessage(ChatColor.RED + ErrorUtils.YOU_MUST_BE_IN_AN_EMPIRE);
            return;
        }

        if (!tePlayer.hasPermission(Permission.INVITES)) {
            sender.sendMessage(ErrorUtils.generatePermissionError(Permission.INVITES));
            return;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + getUsage());
            return;
        }

        final String player = args[0];
        final TEPlayer tePlayerToRemove = TEPlayer.getTEPlayer(player);
        if (tePlayerToRemove == null) {
            sender.sendMessage(ChatColor.RED + String.format(
                "'%s' is not an existing player",
                player
            ));
            return;
        }

        if (tePlayerToRemove.getEmpire() == null
                || tePlayerToRemove.getEmpire().getId() != empire.getId()) {
            sender.sendMessage(ChatColor.RED + String.format(
                "%s is not currently in the empire",
                player
            ));
            return;
        }

        if (tePlayerToRemove.isOwner()) {
            sender.sendMessage(ChatColor.RED + String.format(
                "%s is the owner of the empire and you cannot remove them",
                ChatColor.BOLD + player + ChatColor.RED
            ));
            return;
        }

        DiscordBot.removeEmpireDiscordRoleFromUser(tePlayerToRemove, empire);
        empire.removeMember(tePlayerToRemove);
        tePlayerToRemove.leaveEmpire();
        tePlayerToRemove.updatePlayerScoreboard();

        empire.broadcast(ChatColor.GREEN, String.format(
            "%s removed %s from the empire",
            sender.getName(),
            player
        ));

        final Player kickedPlayer = Bukkit.getPlayer(tePlayerToRemove.getPlayerUUID());
        if (kickedPlayer != null)
            kickedPlayer.sendMessage(ChatColor.YELLOW + String.format(
                "You have been kicked from %s by %s!",
                "" + empire.getChatColor() + ChatColor.BOLD + empire.getName() + ChatColor.RED,
                ChatColor.BOLD + sender.getName() + ChatColor.YELLOW
            ));
    }

    @Override
    public String getDescription() {
        return "Kick member from empire";
    }

    @Override
    public Permission getPermissionRequired() {
        return Permission.INVITES;
    }

    @Override
    public String getUsage() {
        return "/e kick <name>";
    }

}
