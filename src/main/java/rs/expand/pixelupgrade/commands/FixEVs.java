package rs.expand.pixelupgrade.commands;

import java.math.BigDecimal;
import java.util.Optional;

import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.service.economy.account.UniqueAccount;
import org.spongepowered.api.service.economy.transaction.ResultType;
import org.spongepowered.api.service.economy.transaction.TransactionResult;
import org.spongepowered.api.text.Text;

import com.pixelmonmod.pixelmon.config.PixelmonEntityList;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.storage.NbtKeys;
import com.pixelmonmod.pixelmon.storage.PixelmonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerStorage;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import rs.expand.pixelupgrade.PixelUpgrade;
import rs.expand.pixelupgrade.configs.FixEVsConfig;
import rs.expand.pixelupgrade.configs.PixelUpgradeMainConfig;

import static rs.expand.pixelupgrade.PixelUpgrade.economyService;

public class FixEVs implements CommandExecutor
{
    // See which messages should be printed by the debug logger. Valid range is 0-3.
    // We set null on hitting an error, and let the main code block handle it from there.
    private static Integer debugLevel;
    private void getVerbosityMode()
    {
        // Does the debugVerbosityMode node exist? If so, figure out what's in it.
        if (!FixEVsConfig.getInstance().getConfig().getNode("debugVerbosityMode").isVirtual())
        {
            String modeString = FixEVsConfig.getInstance().getConfig().getNode("debugVerbosityMode").getString();

            if (modeString.matches("^[0-3]"))
                debugLevel = Integer.parseInt(modeString);
            else
                PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7cInvalid value on config variable \"debugVerbosityMode\"! Valid range: 0-3");
        }
        else
        {
            PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7cConfig variable \"debugVerbosityMode\" could not be found!");
            debugLevel = null;
        }
    }

    private static String alias;
    private void getCommandAlias()
    {
        if (!FixEVsConfig.getInstance().getConfig().getNode("commandAlias").isVirtual())
            alias = "/" + FixEVsConfig.getInstance().getConfig().getNode("commandAlias").getString();
        else
        {
            PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7cConfig variable \"commandAlias\" could not be found!");
            alias = null;
        }
    }

    // Set up a variable that we'll be using in the EV-fixing method.
    private Boolean useBritishSpelling = null;

	public CommandResult execute(CommandSource src, CommandContext args) throws CommandException
	{
	    if (src instanceof Player)
        {
            Integer commandCost = null;
            if (!FixEVsConfig.getInstance().getConfig().getNode("commandCost").isVirtual())
                commandCost = FixEVsConfig.getInstance().getConfig().getNode("commandCost").getInt();
            else
                PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7cCould not parse config variable \"commandCost\"!");

            // Grab the useBritishSpelling value from the main config.
            if (!PixelUpgradeMainConfig.getInstance().getConfig().getNode("useBritishSpelling").isVirtual())
                useBritishSpelling = PixelUpgradeMainConfig.getInstance().getConfig().getNode("useBritishSpelling").getBoolean();

            // Set up the command's debug verbosity mode and preferred alias.
            getVerbosityMode();
            getCommandAlias();

            if (commandCost == null || alias == null || debugLevel == null || debugLevel >= 4 || debugLevel < 0)
            {
                // Specific errors are already called earlier on -- this is tacked on to the end.
                src.sendMessage(Text.of("\u00A74Error: \u00A7cThis command's config is invalid! Please report to staff."));
                PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7cCheck your config. If need be, wipe and \u00A74/pixelupgrade reload\u00A7c.");
            }
            else if (useBritishSpelling == null)
            {
                src.sendMessage(Text.of("\u00A74Error: \u00A7cCould not parse main config. Please report to staff."));
                PixelUpgrade.log.info("\u00A74CheckEgg // critical: \u00A7cCouldn't get value of \"useBritishSpelling\" from the main config.");
                PixelUpgrade.log.info("\u00A74CheckEgg // critical: \u00A7cPlease check (or wipe and reload) your PixelUpgrade.conf file.");
            }
            else
            {
                printToLog(2, "Called by player \u00A73" + src.getName() + "\u00A7b. Starting!");

                Player player = (Player) src;
                boolean canContinue = true, commandConfirmed = false;
                int slot = 0;

                if (!args.<String>getOne("slot").isPresent())
                {
                    printToLog(2, "No arguments provided, aborting.");

                    checkAndAddHeader(commandCost, player);
                    src.sendMessage(Text.of("\u00A74Error: \u00A7cNo parameters found. Please provide a slot."));
                    printCorrectHelper(commandCost, player);
                    checkAndAddFooter(commandCost, player);

                    canContinue = false;
                }
                else
                {
                    String slotString = args.<String>getOne("slot").get();

                    if (slotString.matches("^[1-6]"))
                    {
                        printToLog(3, "Slot was a valid slot number. Let's move on!");
                        slot = Integer.parseInt(args.<String>getOne("slot").get());
                    }
                    else
                    {
                        printToLog(2, "Invalid slot provided. Aborting.");

                        checkAndAddHeader(commandCost, player);
                        src.sendMessage(Text.of("\u00A74Error: \u00A7cInvalid slot value. Valid values are 1-6."));
                        printCorrectHelper(commandCost, player);
                        checkAndAddFooter(commandCost, player);

                        canContinue = false;
                    }
                }

                if (args.hasAny("c"))
                    commandConfirmed = true;

                if (canContinue)
                {
                    printToLog(3, "No error encountered, input should be valid. Continuing!");
                    Optional<?> storage = PixelmonStorage.pokeBallManager.getPlayerStorage(((EntityPlayerMP) src));

                    if (!storage.isPresent())
                    {
                        printToLog(0, "\u00A74" + player.getName() + "\u00A7c does not have a Pixelmon storage, aborting. May be a bug?");
                        src.sendMessage(Text.of("\u00A74Error: \u00A7cNo Pixelmon storage found. Please contact staff!"));
                    }
                    else
                    {
                        PlayerStorage storageCompleted = (PlayerStorage) storage.get();
                        NBTTagCompound nbt = storageCompleted.partyPokemon[slot - 1];

                        if (nbt == null)
                        {
                            printToLog(2, "No NBT found in slot, probably empty. Aborting...");
                            src.sendMessage(Text.of("\u00A74Error: \u00A7cYou don't have anything in that slot!"));
                        }
                        else if (nbt.getBoolean("isEgg"))
                        {
                            printToLog(2, "Tried to fix EVs on an egg. Aborting...");
                            src.sendMessage(Text.of("\u00A74Error: \u00A7cThat's an egg! Go hatch it, first."));
                        }
                        else
                        {
                            EntityPixelmon pokemon = (EntityPixelmon) PixelmonEntityList.createEntityFromNBT(nbt, (World) player.getWorld());
                            int HPEV = pokemon.stats.EVs.HP;
                            int attackEV = pokemon.stats.EVs.Attack;
                            int defenceEV = pokemon.stats.EVs.Defence;
                            int spAttackEV = pokemon.stats.EVs.SpecialAttack;
                            int spDefenceEV = pokemon.stats.EVs.SpecialDefence;
                            int speedEV = pokemon.stats.EVs.Speed;
                            int totalEVs = HPEV + attackEV + defenceEV + spAttackEV + spDefenceEV + speedEV;
                            boolean allEVsGood = false;

                            if (HPEV < 253 && HPEV >= 0 && attackEV < 253 && attackEV >= 0 && defenceEV < 253 &&
                                    defenceEV >= 0 &&spAttackEV < 253 && spAttackEV >= 0 && spDefenceEV < 253 &&
                                    spDefenceEV >= 0 && speedEV < 253 && speedEV >= 0)
                                allEVsGood = true;

                            if (HPEV == 0 && attackEV == 0 && defenceEV == 0 && spAttackEV == 0 && spDefenceEV == 0 && speedEV == 0)
                            {
                                printToLog(2, "All EVs were at zero, no upgrades needed to be done. Abort.");
                                src.sendMessage(Text.of("\u00A7dNo EVs were found. Go faint some wild Pok\u00E9mon!"));
                                canContinue = false;
                            }
                            else if (HPEV > 255 || attackEV > 255 || defenceEV > 255 || spAttackEV > 255 || spDefenceEV > 255 || speedEV > 255)
                            {
                                printToLog(2, "Found one or more EVs above 255. Probably set by staff, so abort.");
                                src.sendMessage(Text.of("\u00A74Error: \u00A7cOne or more EVs are above the limit. Contact staff."));
                                canContinue = false;
                            }
                            else if (HPEV < 0 || attackEV < 0 || defenceEV < 0 || spAttackEV < 0 || spDefenceEV < 0 || speedEV < 0)
                            {
                                printToLog(2, "Found one or more negative EVs. Let's let staff handle this -- abort.");
                                src.sendMessage(Text.of("\u00A74Error: \u00A7cOne or more EVs are negative. Please contact staff."));
                                canContinue = false;
                            }
                            else if (totalEVs < 510 && allEVsGood)
                            {
                                printToLog(2, "No wasted stats were detected. Abort.");
                                src.sendMessage(Text.of("\u00A7dNo issues found! Your Pok\u00E9mon is coming along nicely."));
                                canContinue = false;
                            }
                            else if (totalEVs == 510 && allEVsGood)
                            {
                                printToLog(2, "EV total of 510 hit, but no overleveled EVs found. Abort.");
                                src.sendMessage(Text.of("\u00A7dNo issues found! Not happy? Get some EV-reducing berries!"));
                                canContinue = false;
                            }
                            else if (commandCost > 0)
                            {
                                BigDecimal costToConfirm = new BigDecimal(commandCost);

                                if (commandConfirmed)
                                {
                                    Optional<UniqueAccount> optionalAccount = economyService.getOrCreateAccount(player.getUniqueId());

                                    if (optionalAccount.isPresent())
                                    {
                                        UniqueAccount uniqueAccount = optionalAccount.get();
                                        TransactionResult transactionResult = uniqueAccount.withdraw(economyService.getDefaultCurrency(), costToConfirm, Cause.source(this).build());

                                        if (transactionResult.getResult() == ResultType.SUCCESS)
                                        {
                                            printToLog(1, "Fixed EVs for slot " + slot + ", and took " + costToConfirm + " coins.");
                                            fixPlayerEVs(nbt, player, HPEV, attackEV, defenceEV, spAttackEV, spDefenceEV, speedEV);
                                        }
                                        else
                                        {
                                            BigDecimal balanceNeeded = uniqueAccount.getBalance(economyService.getDefaultCurrency()).subtract(costToConfirm).abs();
                                            printToLog(2, "Not enough coins! Cost: \u00A73" + costToConfirm + "\u00A7b, lacking: \u00A73" + balanceNeeded);

                                            src.sendMessage(Text.of("\u00A74Error: \u00A7cYou need \u00A74" + balanceNeeded + "\u00A7c more coins to do this."));
                                            canContinue = false;
                                        }
                                    }
                                    else
                                    {
                                        printToLog(0, "\u00A74" + src.getName() + "\u00A7c does not have an economy account, aborting. May be a bug?");
                                        src.sendMessage(Text.of("\u00A74Error: \u00A7cNo economy account found. Please contact staff!"));
                                        canContinue = false;
                                    }
                                }
                                else
                                {
                                    printToLog(2, "Got cost but no confirmation; end of the line.");

                                    src.sendMessage(Text.of("\u00A76Warning: \u00A7eFixing EVs will cost \u00A76" + costToConfirm + "\u00A7e coins."));
                                    src.sendMessage(Text.of("\u00A72Ready? Type: \u00A7a" + alias + " " + slot + " -c"));

                                    canContinue = false;
                                }
                            }
                            else
                            {
                                printToLog(1, "Fixed EVs for slot " + slot + ". Config price is 0, taking nothing.");
                                fixPlayerEVs(nbt, player, HPEV, attackEV, defenceEV, spAttackEV, spDefenceEV, speedEV);
                            }

                            if (canContinue)
                            {
                                printToLog(2, "Succesfully optimized a Pok\u00E9mon!");

                                if (nbt.getString("Nickname").equals(""))
                                    src.sendMessage(Text.of("\u00A76" + nbt.getString("Name") + "\u00A7e has been checked and optimized!"));
                                else
                                    src.sendMessage(Text.of("\u00A7eYour \u00A76" + nbt.getString("Nickname") + "\u00A7e has been checked and optimized!"));
                            }
                        }
                    }
                }
            }
        }
	    else
            printToLog(0, "This command cannot run from the console or command blocks.");

        return CommandResult.success();
	}

	private void fixPlayerEVs(NBTTagCompound nbt, Player player, int HPEV, int attackEV, int defenceEV, int spAttackEV, int spDefenceEV, int speedEV)
    {
        if (HPEV > 252)
        {
            player.sendMessage(Text.of("\u00A7aStat \u00A72HP \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_HP, 252);
        }
        if (attackEV > 252)
        {
            player.sendMessage(Text.of("\u00A7aStat \u00A72Attack \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_ATTACK, 252);
        }
        if (defenceEV > 252)
        {
            if (useBritishSpelling)
                player.sendMessage(Text.of("\u00A7aStat \u00A72Defence \u00A7ais above 252 and has been fixed!"));
            else
                player.sendMessage(Text.of("\u00A7aStat \u00A72Defense \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_DEFENCE, 252);
        }
        if (spAttackEV > 252)
        {
            player.sendMessage(Text.of("\u00A7aStat \u00A72Special Attack \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_SPECIAL_ATTACK, 252);
        }
        if (spDefenceEV > 252)
        {
            if (useBritishSpelling)
                player.sendMessage(Text.of("\u00A7aStat \u00A72Special Defence \u00A7ais above 252 and has been fixed!"));
            else
                player.sendMessage(Text.of("\u00A7aStat \u00A72Special Defense \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_SPECIAL_DEFENCE, 252);
        }
        if (speedEV > 252)
        {
            player.sendMessage(Text.of("\u00A7aStat \u00A72Speed \u00A7ais above 252 and has been fixed!"));
            nbt.setInteger(NbtKeys.EV_SPEED, 252);
        }
    }

    private void checkAndAddHeader(int cost, Player player)
    {
        if (cost > 0)
        {
            player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
        }
    }

    private void checkAndAddFooter(int cost, Player player)
    {
        if (cost > 0)
        {
            player.sendMessage(Text.of(""));
            player.sendMessage(Text.of("\u00A76Warning: \u00A7eAdd the -c flag only if you're sure!"));
            player.sendMessage(Text.of("\u00A7eConfirming will cost you \u00A76" + cost + "\u00A7e coins."));
            player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
        }
    }

    private void printCorrectHelper(int cost, Player player)
    {
        if (cost != 0)
            player.sendMessage(Text.of("\u00A74Usage: \u00A7c" + alias + " <slot, 1-6> {-c to confirm}"));
        else
            player.sendMessage(Text.of("\u00A74Usage: \u00A7c" + alias + " <slot, 1-6>"));
    }

    private void printToLog(int debugNum, String inputString)
    {
        if (debugNum <= debugLevel)
        {
            if (debugNum == 0)
                PixelUpgrade.log.info("\u00A74FixEVs // critical: \u00A7c" + inputString);
            else if (debugNum == 1)
                PixelUpgrade.log.info("\u00A76FixEVs // important: \u00A7e" + inputString);
            else if (debugNum == 2)
                PixelUpgrade.log.info("\u00A73FixEVs // start/end: \u00A7b" + inputString);
            else
                PixelUpgrade.log.info("\u00A72FixEVs // debug: \u00A7a" + inputString);
        }
    }
}
