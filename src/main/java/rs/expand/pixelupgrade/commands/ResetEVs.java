package rs.expand.pixelupgrade.commands;

import com.pixelmonmod.pixelmon.storage.NbtKeys;
import com.pixelmonmod.pixelmon.storage.PixelmonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerStorage;

import java.math.BigDecimal;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;

import rs.expand.pixelupgrade.configs.ResetEVsConfig;
import rs.expand.pixelupgrade.PixelUpgrade;

import static rs.expand.pixelupgrade.PixelUpgrade.debugLevel;
import static rs.expand.pixelupgrade.PixelUpgrade.economyService;

public class ResetEVs implements CommandExecutor
{
    // Not sure how this works yet, but nicked it from TotalEconomy.
    // Will try to figure this out later, just glad to have this working for now.
    private PixelUpgrade pixelUpgrade;
    public ResetEVs(PixelUpgrade pixelUpgrade) { this.pixelUpgrade = pixelUpgrade; }

    private static String alias = null;
    private void getCommandAlias()
    {
        if (!ResetEVsConfig.getInstance().getConfig().getNode("commandAlias").isVirtual())
            alias = "/" + ResetEVsConfig.getInstance().getConfig().getNode("commandAlias").getString();
        else
            PixelUpgrade.log.info("§4ResetEVs // critical: §cConfig variable \"commandAlias\" could not be found!");
    }

	@SuppressWarnings("NullableProblems")
    public CommandResult execute(CommandSource src, CommandContext args)
	{
        if (src instanceof Player)
        {
            Integer commandCost = null;
            if (!ResetEVsConfig.getInstance().getConfig().getNode("commandCost").isVirtual())
                commandCost = ResetEVsConfig.getInstance().getConfig().getNode("commandCost").getInt();
            else
                PixelUpgrade.log.info("§4ResetEVs // critical: §cCould not parse config variable \"commandCost\"!");

            // Set up the command's preferred alias.
            getCommandAlias();

            if (commandCost == null || alias == null)
            {
                // Specific errors are already called earlier on -- this is tacked on to the end.
                src.sendMessage(Text.of("§4Error: §cThis command's config is invalid! Please report to staff."));
                PixelUpgrade.log.info("§4ResetEVs // critical: §cCheck your config. If need be, wipe and §4/pureload§c.");
            }
            else
            {
                printToLog(1, "Called by player §3" + src.getName() + "§b. Starting!");

                Player player = (Player) src;
                boolean canContinue = true, commandConfirmed = false;
                int slot = 0;

                if (!args.<String>getOne("slot").isPresent())
                {
                    printToLog(1, "No arguments provided. Exit.");

                    checkAndAddHeader(commandCost, player);
                    src.sendMessage(Text.of("§4Error: §cNo parameters found. Please provide a slot."));
                    printCorrectHelper(commandCost, player);
                    checkAndAddFooter(commandCost, player);

                    canContinue = false;
                }
                else
                {
                    String slotString = args.<String>getOne("slot").get();

                    if (slotString.matches("^[1-6]"))
                    {
                        printToLog(2, "Slot was a valid slot number. Let's move on!");
                        slot = Integer.parseInt(args.<String>getOne("slot").get());
                    }
                    else
                    {
                        printToLog(1, "Invalid slot provided. Exit.");

                        checkAndAddHeader(commandCost, player);
                        src.sendMessage(Text.of("§4Error: §cInvalid slot value. Valid values are 1-6."));
                        printCorrectHelper(commandCost, player);
                        checkAndAddFooter(commandCost, player);

                        canContinue = false;
                    }
                }

                if (args.hasAny("c"))
                    commandConfirmed = true;

                if (canContinue)
                {
                    printToLog(2, "No error encountered, input should be valid. Continuing!");
                    Optional<?> storage = PixelmonStorage.pokeBallManager.getPlayerStorage(((EntityPlayerMP) src));

                    if (!storage.isPresent())
                    {
                        printToLog(0, "§4" + player.getName() + "§c does not have a Pixelmon storage, aborting. May be a bug?");
                        src.sendMessage(Text.of("§4Error: §cNo Pixelmon storage found. Please contact staff!"));
                    }
                    else
                    {
                        PlayerStorage storageCompleted = (PlayerStorage) storage.get();
                        NBTTagCompound nbt = storageCompleted.partyPokemon[slot - 1];

                        if (nbt == null)
                        {
                            printToLog(1, "No NBT found in slot, probably empty. Exit.");
                            src.sendMessage(Text.of("§4Error: §cYou don't have anything in that slot!"));
                        }
                        else if (nbt.getBoolean("isEgg"))
                        {
                            printToLog(1, "Tried to reset EVs on an egg. Exit.");
                            src.sendMessage(Text.of("§4Error: §cThat's an egg! Go hatch it, first."));
                        }
                        else if (commandConfirmed)
                        {
                            printToLog(2, "Command was confirmed, checking balances.");

                            if (commandCost > 0)
                            {
                                BigDecimal costToConfirm = new BigDecimal(commandCost);
                                Optional<UniqueAccount> optionalAccount = economyService.getOrCreateAccount(player.getUniqueId());

                                if (optionalAccount.isPresent())
                                {
                                    UniqueAccount uniqueAccount = optionalAccount.get();
                                    TransactionResult transactionResult = uniqueAccount.withdraw(economyService.getDefaultCurrency(), costToConfirm, Cause.of(EventContext.empty(), pixelUpgrade.getPluginContainer()));

                                    if (transactionResult.getResult() == ResultType.SUCCESS)
                                    {
                                        resetPlayerEVs(nbt, player);
                                        printToLog(1, "Reset EVs for slot " + slot + ", and took " + costToConfirm + " coins.");
                                    }
                                    else
                                    {
                                        BigDecimal balanceNeeded = uniqueAccount.getBalance(economyService.getDefaultCurrency()).subtract(costToConfirm).abs();
                                        printToLog(1, "Not enough coins! Cost: §3" + costToConfirm + "§b, lacking: §3" + balanceNeeded);

                                        src.sendMessage(Text.of("§4Error: §cYou need §4" + balanceNeeded + "§c more coins to do this."));
                                    }
                                }
                                else
                                {
                                    printToLog(0, "§4" + src.getName() + "§c does not have an economy account, aborting. May be a bug?");
                                    src.sendMessage(Text.of("§4Error: §cNo economy account found. Please contact staff!"));
                                }
                            }
                            else
                            {
                                resetPlayerEVs(nbt, player);
                                printToLog(1, "Reset EVs for slot " + slot + ". Config price is 0, taking nothing.");
                            }
                        }
                        else
                        {
                            printToLog(1, "No confirmation provided, printing warning and aborting.");

                            src.sendMessage(Text.of("§5-----------------------------------------------------"));
                            src.sendMessage(Text.of("§6Warning: §eYou are about to reset this Pokémon's EVs to zero!"));
                            if (commandCost > 0)
                                src.sendMessage(Text.of("§eResetting will cost §6" + commandCost + "§e coins!"));
                            src.sendMessage(Text.of("§2Ready? Type: §a" + alias + " " + slot + " -c"));
                            src.sendMessage(Text.of("§5-----------------------------------------------------"));
                        }
                    }
                }
            }
        }
        else
            PixelUpgrade.log.info("§cThis command cannot run from the console or command blocks.");

        return CommandResult.success();
	}

	private void resetPlayerEVs(NBTTagCompound nbt, Player player)
    {
        int EVHP = nbt.getInteger(NbtKeys.EV_HP);
        int EVATT = nbt.getInteger(NbtKeys.EV_ATTACK);
        int EVDEF = nbt.getInteger(NbtKeys.EV_DEFENCE);
        int EVSPATT = nbt.getInteger(NbtKeys.EV_SPECIAL_ATTACK);
        int EVSPDEF = nbt.getInteger(NbtKeys.EV_SPECIAL_DEFENCE);
        int EVSPD = nbt.getInteger(NbtKeys.EV_SPEED);

        printToLog(1, "Command has been confirmed, printing old EVs...");
        printToLog(1, "HP: " + EVHP + " | ATK: " + EVATT + " | DEF: " + EVDEF + " | SPATK: " + EVSPATT + " | SPDEF: " + EVSPDEF + " | SPD: " + EVSPD);

        nbt.setInteger(NbtKeys.EV_HP, 0);
        nbt.setInteger(NbtKeys.EV_ATTACK, 0);
        nbt.setInteger(NbtKeys.EV_DEFENCE, 0);
        nbt.setInteger(NbtKeys.EV_SPECIAL_ATTACK, 0);
        nbt.setInteger(NbtKeys.EV_SPECIAL_DEFENCE, 0);
        nbt.setInteger(NbtKeys.EV_SPEED, 0);

        if (nbt.getString("Nickname").equals(""))
            player.sendMessage(Text.of("§2" + nbt.getString("Name") + "§a had its EVs wiped!"));
        else
            player.sendMessage(Text.of("§aYour §2" + nbt.getString("Nickname") + "§a had its EVs wiped!"));
    }

    private void checkAndAddHeader(int cost, Player player)
    {
        if (cost > 0)
            player.sendMessage(Text.of("§5-----------------------------------------------------"));
    }

    private void checkAndAddFooter(int cost, Player player)
    {
        if (cost > 0)
        {
            player.sendMessage(Text.of(""));
            player.sendMessage(Text.of("§6Warning: §eAdd the -c flag only if you're sure!"));
            player.sendMessage(Text.of("§eConfirming will cost you §6" + cost + "§e coins."));
            player.sendMessage(Text.of("§5-----------------------------------------------------"));
        }
    }

    private void printCorrectHelper(int cost, Player player)
    {
        if (cost != 0)
            player.sendMessage(Text.of("§4Usage: §c" + alias + " <slot, 1-6> {-c to confirm}"));
        else
            player.sendMessage(Text.of("§4Usage: §c" + alias + " <slot, 1-6>"));
    }

    private void printToLog(int debugNum, String inputString)
    {
        if (debugNum <= debugLevel)
        {
            if (debugNum == 0)
                PixelUpgrade.log.info("§4ResetEVs // critical: §c" + inputString);
            else if (debugNum == 1)
                PixelUpgrade.log.info("§3ResetEVs // notice: §b" + inputString);
            else
                PixelUpgrade.log.info("§2ResetEVs // debug: §a" + inputString);
        }
    }
}