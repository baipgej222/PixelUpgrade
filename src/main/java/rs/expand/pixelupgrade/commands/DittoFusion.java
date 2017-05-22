package rs.expand.pixelupgrade.commands;

import com.pixelmonmod.pixelmon.config.PixelmonEntityList;
import com.pixelmonmod.pixelmon.entities.pixelmon.EntityPixelmon;
import com.pixelmonmod.pixelmon.storage.NbtKeys;
import com.pixelmonmod.pixelmon.storage.PixelmonStorage;
import com.pixelmonmod.pixelmon.storage.PlayerStorage;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

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

import rs.expand.pixelupgrade.PixelUpgrade;

import java.math.BigDecimal;
import java.util.Optional;

import static rs.expand.pixelupgrade.PixelUpgrade.economyService;

public class DittoFusion implements CommandExecutor
{
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException
    {
        Integer slot1 = 0, slot2 = 0;
        Boolean commandConfirmed = false, canContinue = true;
        Player player = (Player) src;

        PixelUpgrade.log.info("\u00A7bDittoFusion: Called by player " + player.getName() + ", starting command.");

        if (!args.<String>getOne("target slot").isPresent())
        {
            player.sendMessage(Text.of("\u00A74Error: \u00A7cNo slots were provided. Please provide two valid slots."));
            player.sendMessage(Text.of("\u00A74Usage: \u00A7c/fuse <target slot> <sacrifice slot> (-c to confirm)"));

            canContinue = false;
        }
        else
        {
            String slotString = args.<String>getOne("target slot").get();

            if (slotString.matches("^[1-6]"))
                slot1 = Integer.parseInt(args.<String>getOne("target slot").get());
            else
            {
                player.sendMessage(Text.of("\u00A74Error: \u00A7cInvalid value on target slot. Valid values are 1-6."));
                player.sendMessage(Text.of("\u00A74Usage: \u00A7c/fuse <target slot> <sacrifice slot> (-c to confirm)"));

                canContinue = false;
            }
        }

        if (!args.<String>getOne("sacrifice slot").isPresent() && canContinue)
        {
            player.sendMessage(Text.of("\u00A74Error: \u00A7cNo sacrifice slot provided. Please provide two valid slots."));
            player.sendMessage(Text.of("\u00A74Usage: \u00A7c/fuse <target slot> <sacrifice slot> (-c to confirm)"));

            canContinue = false;
        }
        else if (canContinue)
        {
            String slotString = args.<String>getOne("sacrifice slot").get();

            if (slotString.matches("^[1-6]"))
            {
                slot2 = Integer.parseInt(args.<String>getOne("sacrifice slot").get());

                if (slot2 == slot1)
                {
                    player.sendMessage(Text.of("\u00A74Error: \u00A7cYou can't fuse a Pok\u00E9mon with itself."));
                    canContinue = false;
                }
            }
            else
            {
                player.sendMessage(Text.of("\u00A74Error: \u00A7cInvalid value on sacrifice slot. Valid values are 1-6."));
                player.sendMessage(Text.of("\u00A74Usage: \u00A7c/fuse <target slot> <sacrifice slot> (-c to confirm)"));

                canContinue = false;
            }
        }

        if (args.hasAny("c"))
            commandConfirmed = true;

        if (canContinue)
        {
            Optional<?> storage = PixelmonStorage.pokeBallManager.getPlayerStorage(((EntityPlayerMP) player));
            PlayerStorage storageCompleted = (PlayerStorage) storage.get();
            NBTTagCompound nbt1 = storageCompleted.partyPokemon[slot1 - 1];
            NBTTagCompound nbt2 = storageCompleted.partyPokemon[slot2 - 1];

            if (nbt1 == null && nbt2 != null)
                player.sendMessage(Text.of("\u00A74Error: \u00A7cThe target Pok\u00E9mon does not seem to exist."));
            else if (nbt1 != null && nbt2 == null)
                player.sendMessage(Text.of("\u00A74Error: \u00A7cThe sacrifice Pok\u00E9mon does not seem to exist."));
            else if (nbt1 == null)
                player.sendMessage(Text.of("\u00A74Error: \u00A7cBoth the target and sacrifice do not seem to exist."));
            else
            {
                Optional<UniqueAccount> optionalAccount = economyService.getOrCreateAccount(player.getUniqueId());

                if (optionalAccount.isPresent())
                {
                    if (!nbt1.getString("Name").equals("Ditto") && nbt2.getString("Name").equals("Ditto"))
                        player.sendMessage(Text.of("\u00A74Error: \u00A7cYour target Pok\u00E9mon is not a Ditto."));
                    else if (nbt1.getString("Name").equals("Ditto") && !nbt2.getString("Name").equals("Ditto"))
                        player.sendMessage(Text.of("\u00A74Error: \u00A7cSorry, but the sacrifice needs to be a Ditto."));
                    else if (!nbt1.getString("Name").equals("Ditto") && !nbt2.getString("Name").equals("Ditto"))
                        player.sendMessage(Text.of("\u00A74Error: \u00A7cThis command only works on Dittos."));
                    else
                    {
                        EntityPixelmon targetPokemon = (EntityPixelmon) PixelmonEntityList.createEntityFromNBT(nbt1, (World) player.getWorld());
                        EntityPixelmon sacrificePokemon = (EntityPixelmon) PixelmonEntityList.createEntityFromNBT(nbt2, (World) player.getWorld());
                        Integer targetFuseCount = targetPokemon.getEntityData().getInteger("fuseCount"), sacrificeFuseCount = sacrificePokemon.getEntityData().getInteger("fuseCount");

                        if (targetFuseCount == 10 && nbt1.getInteger(NbtKeys.IS_SHINY) == 1)
                        {
                            player.sendMessage(Text.of("\u00A74Error: \u00A7cYour target shiny Ditto cannot grow any further."));
                            player.sendMessage(Text.of("\u00A76Tip: \u00A7eYou could still sacrifice \u00A7othis\u00A7r\u00A7e Ditto... You monster."));
                        }
                        else if (targetFuseCount == 5 && nbt1.getInteger(NbtKeys.IS_SHINY) != 1)
                        {
                            player.sendMessage(Text.of("\u00A74Error: \u00A7cYour target Ditto cannot grow any further."));
                            player.sendMessage(Text.of("\u00A76Tip: \u00A7eYou could still sacrifice \u00A7othis\u00A7r\u00A7e Ditto... You monster."));
                        }
                        else
                        {
                            UniqueAccount uniqueAccount = optionalAccount.get();
                            Integer HPUpgradeCount, ATKUpgradeCount, DEFUpgradeCount, SPATKUpgradeCount, SPDEFUpgradeCount, SPDUpgradeCount;

                            Integer targetHP = nbt1.getInteger(NbtKeys.IV_HP);
                            Integer targetATK = nbt1.getInteger(NbtKeys.IV_ATTACK);
                            Integer targetDEF = nbt1.getInteger(NbtKeys.IV_DEFENCE);
                            Integer targetSPATK = nbt1.getInteger(NbtKeys.IV_SP_ATT);
                            Integer targetSPDEF = nbt1.getInteger(NbtKeys.IV_SP_DEF);
                            Integer targetSPD = nbt1.getInteger(NbtKeys.IV_SPEED);

                            Integer sacrificeHP = nbt2.getInteger(NbtKeys.IV_HP);
                            Integer sacrificeATK = nbt2.getInteger(NbtKeys.IV_ATTACK);
                            Integer sacrificeDEF = nbt2.getInteger(NbtKeys.IV_DEFENCE);
                            Integer sacrificeSPATK = nbt2.getInteger(NbtKeys.IV_SP_ATT);
                            Integer sacrificeSPDEF = nbt2.getInteger(NbtKeys.IV_SP_DEF);
                            Integer sacrificeSPD = nbt2.getInteger(NbtKeys.IV_SPEED);

                            switch (sacrificeHP / 10)
                            {
                                case 0:
                                    HPUpgradeCount = 0;
                                    break;
                                case 1:
                                    HPUpgradeCount = 1;
                                    break;
                                case 2:
                                    HPUpgradeCount = 2;
                                    break;
                                default:
                                    HPUpgradeCount = 3;
                                    break;
                            }

                            if (targetHP >= 31)
                                HPUpgradeCount = 0;
                            else if (HPUpgradeCount + targetHP >= 31)
                                HPUpgradeCount = 31 - targetHP;

                            switch (sacrificeATK / 10)
                            {
                                case 0:
                                    ATKUpgradeCount = 0;
                                    break;
                                case 1:
                                    ATKUpgradeCount = 1;
                                    break;
                                case 2:
                                    ATKUpgradeCount = 2;
                                    break;
                                default:
                                    ATKUpgradeCount = 3;
                                    break;
                            }

                            if (targetATK >= 31)
                                ATKUpgradeCount = 0;
                            else if (ATKUpgradeCount + targetATK >= 31)
                                ATKUpgradeCount = 31 - targetATK;

                            switch (sacrificeDEF / 10)
                            {
                                case 0:
                                    DEFUpgradeCount = 0;
                                    break;
                                case 1:
                                    DEFUpgradeCount = 1;
                                    break;
                                case 2:
                                    DEFUpgradeCount = 2;
                                    break;
                                default:
                                    DEFUpgradeCount = 3;
                                    break;
                            }

                            if (targetDEF >= 31)
                                DEFUpgradeCount = 0;
                            else if (DEFUpgradeCount + targetDEF >= 31)
                                DEFUpgradeCount = 31 - targetDEF;

                            switch (sacrificeSPATK / 10)
                            {
                                case 0:
                                    SPATKUpgradeCount = 0;
                                    break;
                                case 1:
                                    SPATKUpgradeCount = 1;
                                    break;
                                case 2:
                                    SPATKUpgradeCount = 2;
                                    break;
                                default:
                                    SPATKUpgradeCount = 3;
                                    break;
                            }

                            if (targetSPATK >= 31)
                                SPATKUpgradeCount = 0;
                            else if (SPATKUpgradeCount + targetSPATK >= 31)
                                SPATKUpgradeCount = 31 - targetSPATK;

                            switch (sacrificeSPDEF / 10)
                            {
                                case 0:
                                    SPDEFUpgradeCount = 0;
                                    break;
                                case 1:
                                    SPDEFUpgradeCount = 1;
                                    break;
                                case 2:
                                    SPDEFUpgradeCount = 2;
                                    break;
                                default:
                                    SPDEFUpgradeCount = 3;
                                    break;
                            }

                            if (targetSPDEF >= 31)
                                SPDEFUpgradeCount = 0;
                            else if (SPDEFUpgradeCount + targetSPDEF >= 31)
                                SPDEFUpgradeCount = 31 - targetSPDEF;

                            switch (sacrificeSPD / 10)
                            {
                                case 0:
                                    SPDUpgradeCount = 0;
                                    break;
                                case 1:
                                    SPDUpgradeCount = 1;
                                    break;
                                case 2:
                                    SPDUpgradeCount = 2;
                                    break;
                                default:
                                    SPDUpgradeCount = 3;
                                    break;
                            }

                            if (targetSPD >= 31)
                                SPDUpgradeCount = 0;
                            else if (SPDUpgradeCount + targetSPD >= 31)
                                SPDUpgradeCount = 31 - targetSPD;

                            Integer totalUpgradeCount = HPUpgradeCount + ATKUpgradeCount + DEFUpgradeCount + SPATKUpgradeCount + SPDEFUpgradeCount + SPDUpgradeCount;
                            BigDecimal costToConfirm = BigDecimal.valueOf(totalUpgradeCount * 100.0);

                            if (sacrificeFuseCount > 0)
                            {
                                BigDecimal sacrificeFuseCountMultiplier = new BigDecimal(3);
                                costToConfirm = costToConfirm.multiply(sacrificeFuseCountMultiplier);
                            }

                            /*player.sendMessage(Text.of("\u00A74Debug: \u00A7cTarget: " + targetHP + " " + targetATK + " " + targetDEF + " " + targetSPATK + " " + targetSPDEF + " " + targetSPD));
                            player.sendMessage(Text.of("\u00A74Debug: \u00A7cSacrifice: " + sacrificeHP + " " + sacrificeATK + " " + sacrificeDEF + " " + sacrificeSPATK + " " + sacrificeSPDEF + " " + sacrificeSPD));
                            player.sendMessage(Text.of("\u00A74Debug: \u00A7cCount: " + HPUpgradeCount + " " + ATKUpgradeCount + " " + DEFUpgradeCount + " " + SPATKUpgradeCount + " " + SPDEFUpgradeCount + " " + SPDUpgradeCount));
                            player.sendMessage(Text.of("\u00A74Debug: \u00A7cUpgrade count: " + totalUpgradeCount + ", Confirmation cost:" + costToConfirm));*/

                            if (totalUpgradeCount == 0)
                                player.sendMessage(Text.of("\u00A74Error: \u00A7cYour sacrificial Ditto is too weak to make a difference."));
                            else if (commandConfirmed)
                            {
                                PixelUpgrade.log.info("\u00A7aDittoFusion debug: Entering final stage, with confirmation. Current cash: " + uniqueAccount.getBalance(economyService.getDefaultCurrency()) + ".");

                                TransactionResult transactionResult = uniqueAccount.withdraw(economyService.getDefaultCurrency(), costToConfirm, Cause.source(this).build());
                                if (transactionResult.getResult() == ResultType.SUCCESS)
                                {
                                    player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
                                    player.sendMessage(Text.of("\u00A7aThe Ditto in slot \u00A72" + slot2 + "\u00A7a was eaten, taking \u00A72" + costToConfirm + " coins \u00A7awith it."));
                                    player.sendMessage(Text.of(""));

                                    if (HPUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eHP has been upgraded: \u00A77" + targetHP + " \u00A7f-> \u00A7a" + (targetHP + HPUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_HP, nbt1.getInteger(NbtKeys.IV_HP) + HPUpgradeCount);
                                    }
                                    if (ATKUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eAttack has been upgraded: \u00A77" + targetATK + " \u00A7f-> \u00A7a" + (targetATK + ATKUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_ATTACK, nbt1.getInteger(NbtKeys.IV_ATTACK) + ATKUpgradeCount);
                                    }
                                    if (DEFUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eDefence has been upgraded: \u00A77" + targetDEF + " \u00A7f-> \u00A7a" + (targetDEF + DEFUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_DEFENCE, nbt1.getInteger(NbtKeys.IV_DEFENCE) + DEFUpgradeCount);
                                    }
                                    if (SPATKUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eSpecial Attack has been upgraded: \u00A77" + targetSPATK + " \u00A7f-> \u00A7a" + (targetSPATK + SPATKUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_SP_ATT, nbt1.getInteger(NbtKeys.IV_SP_ATT) + SPATKUpgradeCount);
                                    }
                                    if (SPDEFUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eSpecial Defence has been upgraded: \u00A77" + targetSPDEF + " \u00A7f-> \u00A7a" + (targetSPDEF + SPDEFUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_SP_DEF, nbt1.getInteger(NbtKeys.IV_SP_DEF) + SPDEFUpgradeCount);
                                    }
                                    if (SPDUpgradeCount != 0)
                                    {
                                        player.sendMessage(Text.of("\u00A7eSpeed has been upgraded: \u00A77" + targetSPD + " \u00A7f-> \u00A7a" + (targetSPD + SPDUpgradeCount)));
                                        nbt1.setInteger(NbtKeys.IV_SPEED, nbt1.getInteger(NbtKeys.IV_SPEED) + SPDUpgradeCount);
                                    }

                                    if (sacrificeFuseCount > 0)
                                    {
                                        player.sendMessage(Text.of(""));
                                        player.sendMessage(Text.of("\u00A7bBecause your sacrifice had upgrades on it, you paid triple."));
                                        player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
                                    }
                                    else
                                        player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));

                                    targetPokemon.getEntityData().setInteger("fuseCount", targetFuseCount + 1);
                                    storageCompleted.changePokemonAndAssignID(slot2 - 1, null);

                                    PixelUpgrade.log.info("\u00A7aDittoFusion debug: Transaction successful. Took: " + costToConfirm + " and a Ditto.");
                                    PixelUpgrade.log.info("\u00A7aDittoFusion debug: Exiting final stage. Current cash: " + uniqueAccount.getBalance(economyService.getDefaultCurrency()) + ".");
                                }
                                else
                                {
                                    BigDecimal balanceNeeded = uniqueAccount.getBalance(economyService.getDefaultCurrency()).subtract(costToConfirm).abs();
                                    player.sendMessage(Text.of("\u00A74Error: \u00A7cYou need \u00A74" + balanceNeeded + "\u00A7c more coins to do this."));
                                    PixelUpgrade.log.info("\u00A7aDittoFusion debug: Hit the failed/no funds check, exiting final stage. Needed: " + balanceNeeded + ".");
                                }
                            }
                            else
                            {
                                player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
                                player.sendMessage(Text.of("\u00A7bYou are about to upgrade the Ditto in slot \u00A73" + slot1 + "\u00A7b."));
                                player.sendMessage(Text.of("\u00A7bThe other Ditto in slot \u00A73" + slot2 + "\u00A7b will be \u00A7ldeleted\u00A7r\u00A7b!"));
                                player.sendMessage(Text.of(""));

                                if (HPUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eHP will be upgraded: \u00A77" + targetHP + " \u00A7f-> \u00A7a" + (targetHP + HPUpgradeCount)));
                                if (ATKUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eAttack will be upgraded: \u00A77" + targetATK + " \u00A7f-> \u00A7a" + (targetATK + ATKUpgradeCount)));
                                if (DEFUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eDefence will be upgraded: \u00A77" + targetDEF + " \u00A7f-> \u00A7a" + (targetDEF + DEFUpgradeCount)));
                                if (SPATKUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eSpecial Attack will be upgraded: \u00A77" + targetSPATK + " \u00A7f-> \u00A7a" + (targetSPATK + SPATKUpgradeCount)));
                                if (SPDEFUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eSpecial Defence will be upgraded: \u00A77" + targetSPDEF + " \u00A7f-> \u00A7a" + (targetSPDEF + SPDEFUpgradeCount)));
                                if (SPDUpgradeCount != 0)
                                    player.sendMessage(Text.of("\u00A7eSpeed will be upgraded: \u00A77" + targetSPD + " \u00A7f-> \u00A7a" + (targetSPD + SPDUpgradeCount)));

                                player.sendMessage(Text.of(""));
                                player.sendMessage(Text.of("\u00A7bThis upgrade will cost you \u00A73" + costToConfirm + " coins \u00A7bupon confirmation!"));
                                player.sendMessage(Text.of("\u00A7aReady? Use: \u00A72/fuse " + slot1 + " " + slot2 + " -c"));

                                if (sacrificeFuseCount > 0 || nbt2.getInteger(NbtKeys.IS_SHINY) == 1)
                                    player.sendMessage(Text.of(""));
                                if (sacrificeFuseCount > 0)
                                    player.sendMessage(Text.of("\u00A75Note: \u00A7dYour sacrifice has previous upgrades. Cost was tripled."));
                                if (nbt2.getInteger(NbtKeys.IS_SHINY) == 1)
                                    player.sendMessage(Text.of("\u00A74Warning: \u00A7cYour sacrifice is shiny. This will not be transferred!"));

                                player.sendMessage(Text.of("\u00A75-----------------------------------------------------"));
                            }
                        }
                    }
                }
                else
                {
                    player.sendMessage(Text.of("\u00A74Error: \u00A7cNo economy account found. Please contact staff!"));

                    PixelUpgrade.log.info("\u00A74DittoFusion debug:" + player.getName() + "\u00A7c does not have an economy account, aborting. May be a bug?");
                }
            }
        }

        PixelUpgrade.log.info("\u00A7bDittoFusion debug: Command ended.");
        return CommandResult.success();
    }
}