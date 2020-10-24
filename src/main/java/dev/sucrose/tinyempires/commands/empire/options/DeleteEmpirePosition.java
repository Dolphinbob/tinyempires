package dev.sucrose.tinyempires.commands.empire.options;

import dev.sucrose.tinyempires.models.Empire;
import dev.sucrose.tinyempires.models.EmpireCommandOption;
import dev.sucrose.tinyempires.models.Permission;
import dev.sucrose.tinyempires.models.TEPlayer;
import dev.sucrose.tinyempires.utils.ErrorUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class DeleteEmpirePosition implements EmpireCommandOption {

    @Override
    public void execute(Player sender, String[] args) {
        // /e deleteposition <name>
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

        if (tePlayer.getPosition().hasPermission(Permission.POSITIONS)) {
            sender.sendMessage(ErrorUtils.generatePermissionError(Permission.POSITIONS));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "/e deleteposition <name>");
            return;
        }

        String position = args[0];
        if (empire.getPosition(position) == null) {
            sender.sendMessage(ChatColor.RED + String.format(
                "'%s' is not a position in the empire (%s)",
                position,
                String.join(", ", empire.getPositionMap().keySet())
            ));
            return;
        }

        StringBuilder membersWithPosition = new StringBuilder();
        int index = 0;
        for (TEPlayer player : empire.getMembers()) {
            if (player.getPositionName().equals(position))
                membersWithPosition.append(player.getName()).append(index < empire.getMembers().size() - 1 ? ", " :
                    "");
            index++;
        }

        empire.removePosition(position);
        empire.broadcast(ChatColor.GREEN, String.format(
            "%s deleted the %s position; everyone with it (%s) is now unassigned",
            sender.getName(),
            position,
            membersWithPosition.toString().length() == 0
                ? "No-one"
                : membersWithPosition.toString()
        ));
    }

}
