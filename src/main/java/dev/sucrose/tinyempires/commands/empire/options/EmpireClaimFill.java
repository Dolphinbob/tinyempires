package dev.sucrose.tinyempires.commands.empire.options;

import dev.sucrose.tinyempires.models.*;
import dev.sucrose.tinyempires.utils.BoundUtils;
import dev.sucrose.tinyempires.utils.DrawEmpire;
import dev.sucrose.tinyempires.utils.ErrorUtils;
import dev.sucrose.tinyempires.utils.StringUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

public class EmpireClaimFill implements CommandOption {

    private static final int CLAIM_FILL_LIMIT = 1000;

    private enum FloodFillStatus {
        // chunks successfully calculated and valid
        SUCCESS,
        // more than CLAIM_FILL_LIMIT chunks tried to claim
        OVER_LIMIT,
        // encountered claim fill perimeter owned by foreign empire
        UNOWNED_BORDER,
        // claim fill contains special chunk
        UNCLAIMABLE_CHUNK
    }

    private static class ChunkFloodFillResult {

        private final List<Chunk> chunks;
        private final FloodFillStatus status;

        public ChunkFloodFillResult(Set<String> chunks, FloodFillStatus status) {
            if (chunks == null) {
                this.chunks = null;
            } else {
                this.chunks = new ArrayList<>();
                for (final String c : chunks)
                    this.chunks.add(stringToChunk(c));
            }
            this.status = status;
        }

        public FloodFillStatus getStatus() {
            return status;
        }

        public List<Chunk> getChunks() {
            return chunks;
        }

    }

    private static final World world = Bukkit.getWorld("world");
    private static Chunk stringToChunk(String chunk) {
        final String[] words = chunk.split(" ");
        if (world == null)
            throw new NullPointerException("Could not fetch world '" + words[0] + "'");

        return world.getChunkAt(
            Integer.parseInt(words[1]),
            Integer.parseInt(words[2])
        );
    }

    private static final int MAX_WORLD_X =  10752;
    private static final int MIN_WORLD_X = -10752;
    private static final int MAX_WORLD_Z =  5376;
    private static final int MIN_WORLD_Z = -5376;
    public static boolean chunkCoordsInOverworld(int x, int z) {
        final int worldX = x * 16;
        final int worldZ = z * 16;
        return worldX <= MAX_WORLD_X
            && worldX >= MIN_WORLD_X
            && worldZ <= MAX_WORLD_Z
            && worldZ >= MIN_WORLD_Z;
    }

    /**
     * Calculates chunks to be claimed in claim-fill and status
     * @param empire Empire trying to claim-fill
     * @param chunk Chunks to be claimed
     * @return Chunks to be claimed + flood-fill status if empire does not encircle area trying to claim or if
     * chunks go over chunk maximum
     */

    private ChunkFloodFillResult floodFill(Empire empire, Chunk chunk) {
        // iterative flood-fill
        final Stack<String> stack = new Stack<>();
        final Set<String> chunks = new HashSet<>();
        stack.add(TEChunk.serialize(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));

        int i = 0;
        long startTimeTotal, endTimeTotal;
        double duration;
        while (!stack.empty()) {
            startTimeTotal = System.nanoTime();
            if (chunks.size() > CLAIM_FILL_LIMIT)
                return new ChunkFloodFillResult(null, FloodFillStatus.OVER_LIMIT);

            // pop stack
            final String cSerialized = stack.pop();

            final String[] words = cSerialized.split(" ");
            final String world = words[0];
            final int x = Integer.parseInt(words[1]);
            final int z = Integer.parseInt(words[2]);

            if (BoundUtils.isChunkInBoundsOfSpecialTerritory(chunk))
                return new ChunkFloodFillResult(null, FloodFillStatus.UNCLAIMABLE_CHUNK);

            // if over-world check map limits
            if (world.equals("world")
                    && !chunkCoordsInOverworld(x, z))
                continue;

            final TEChunk teChunk = TEChunk.getChunk(world, x, z);
            if (teChunk != null) {
                if (teChunk.getEmpire().getId().equals(empire.getId()))
                    continue;
                // not entire perimeter is owned by claiming empire
                return new ChunkFloodFillResult(chunks, FloodFillStatus.UNOWNED_BORDER);
            }

            final String rightChunk = TEChunk.serialize(world, x + 1, z);
            if (!chunks.contains(rightChunk))
                stack.add(rightChunk);

            final String leftChunk = TEChunk.serialize(world, x - 1, z);
            if (!chunks.contains(leftChunk))
                stack.add(leftChunk);

            final String upChunk = TEChunk.serialize(world, x, z - 1);
            if (!chunks.contains(upChunk))
                stack.add(upChunk);

            final String downChunk = TEChunk.serialize(world, x, z + 1);
            if (!chunks.contains(downChunk))
                stack.add(downChunk);

            chunks.add(cSerialized);
        }
        return new ChunkFloodFillResult(chunks, FloodFillStatus.SUCCESS);
    }

    @Override
    public void execute(Player sender, String[] args) {
        // /e claim
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

        if (!tePlayer.hasPermission(Permission.CHUNKS)) {
            sender.sendMessage(ErrorUtils.generatePermissionError(Permission.CHUNKS));
            return;
        }

        final Location location = sender.getLocation();
        final Chunk chunk = location.getChunk();
        final World world = location.getWorld();
        if (world == null)
            throw new NullPointerException("World when getting player location is undefined");

        final String worldName = world.getName();
        if (TEChunk.getChunk(worldName, chunk.getX(), chunk.getZ()) != null) {
            if (TEChunk.getChunk(chunk).getEmpire().getId().equals(empire.getId())) {
                sender.sendMessage(ChatColor.RED + "Claim-fill must be called in an unclaimed chunk");
                return;
            }
            sender.sendMessage(ChatColor.RED + "This chunk is already owned by another empire; claim-fill must be " +
                "called in an unclaim chunk");
            return;
        }

        final ChunkFloodFillResult result = floodFill(empire, chunk);
        if (result.getStatus() != FloodFillStatus.SUCCESS) {
            final String message =
                result.getStatus() == FloodFillStatus.OVER_LIMIT
                    ? String.format("Claim-fill breached limit of %d chunks", CLAIM_FILL_LIMIT)
                    : result.getStatus() == FloodFillStatus.UNCLAIMABLE_CHUNK
                        ? "Your claim-fill selection contains an un-claimable chunk"
                        : "Your empire does not fully encircle the area you're trying to claim-fill (check the map!)";
            sender.sendMessage(ChatColor.RED + message);
            return;
        }

        final double cost = result.getChunks().size() * TEChunk.CHUNK_COST;
        if (args.length > 0) {
            final String option = args[0];
            if (option.equals("confirm")) {
                if (empire.getReserve() < cost) {
                    sender.sendMessage(ChatColor.RED + String.format(
                        "Empire requires %.1f more coins to run claim fill (%d chunks, %.1f coins required)",
                        cost - empire.getReserve(),
                        result.getChunks().size(),
                        cost
                    ));
                    return;
                }
                for (final Chunk c : result.getChunks()) {
                    TEChunk.createTEChunk(worldName, c.getX(), c.getZ(), empire);
                    DrawEmpire.drawChunk(empire, worldName, c.getX(), c.getZ());
                }
                empire.takeReserveCoins(cost);
                empire.broadcast(ChatColor.GREEN, String.format(
                    "%s claim-filled %d chunks for %.1f coins (%.1f in reserve)",
                    sender.getName(),
                    result.getChunks().size(),
                    cost,
                    empire.getReserve()
                ));
                return;
            }
            sender.sendMessage(ChatColor.RED + "/e claimfill [confirm]");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + String.format(
            "Claim-fill at %d, %d in the %s would claim %d chunks for %.1f coins (%.1f in reserve)",
            chunk.getX() * 16,
            chunk.getZ() * 16,
            StringUtils.worldDirToName(chunk.getWorld().getName()),
            result.getChunks().size(),
            cost,
            empire.getReserve()
        ));
    }

    @Override
    public String getDescription() {
        return "Claim wilderness enclosed by empire territory";
    }

    @Override
    public Permission getPermissionRequired() {
        return Permission.CHUNKS;
    }

    @Override
    public String getUsage() {
        return "/e claimfill [confirm]";
    }

}
