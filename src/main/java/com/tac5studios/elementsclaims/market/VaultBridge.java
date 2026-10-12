package com.tac5studios.elementsclaims.market;

import com.tac5studios.elementsvault.Cause;
import com.tac5studios.elementsvault.Currency;
import com.tac5studios.elementsvault.EconomyAPI;
import com.tac5studios.elementsvault.EconomyService;
import com.tac5studios.elementsvault.Money;
import com.tac5studios.elementsvault.Result;
import net.minecraft.network.chat.Component;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;

/**
 * Money through Elements: Vault (provided by Elements: Economy). Only loaded after {@link Market#economy()}
 * confirmed Vault is installed, so Claims runs fine without it. Prices are whole numbers in the smallest unit
 * of the server's primary currency (same as Elements: Economy).
 */
final class VaultBridge {

    private VaultBridge() {}

    static boolean available() {
        return EconomyAPI.isAvailable();
    }

    private static Optional<Currency> currency() {
        return EconomyAPI.get().map(EconomyService::primaryCurrency);
    }

    static Component format(long amount) {
        return currency().map(c -> c.format(BigInteger.valueOf(amount))).orElse(Component.literal(String.valueOf(amount)));
    }

    static Optional<Long> parse(String text) {
        Optional<Currency> c = currency();
        if (c.isEmpty()) return Optional.empty();
        return c.get().parse(text.trim()).filter(b -> b.signum() >= 0 && b.bitLength() < 63).map(BigInteger::longValue);
    }

    /** Payer pays amount to payee (payee null = the money just leaves, e.g. abandoned land). Returns null on success or the reason. */
    static Component pay(UUID payer, UUID payee, long amount, String reason) {
        Optional<EconomyService> eco = EconomyAPI.get();
        if (eco.isEmpty()) return Component.literal("Economy unavailable");
        Money m = eco.get().primaryCurrency().of(amount);
        Cause cause = Cause.plugin("elements_claims").withReason(reason);
        Result r = payee == null ? eco.get().withdraw(payer, m, cause) : eco.get().transfer(payer, payee, m, cause);
        return r.success() ? null : r.message();
    }
}
