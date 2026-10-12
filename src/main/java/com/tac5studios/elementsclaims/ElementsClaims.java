package com.tac5studios.elementsclaims;

import com.tac5studios.elementsclaims.config.Features;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elements: Claims — land claiming. Dedicated servers only; players install nothing.
 *
 * Everything starts from server events (each class subscribes itself):
 *   config.ClaimsConfig     claims.toml
 *   messages.Msg            messages.toml
 *   perms.Perm              permission nodes
 *   claim.ClaimStore        claim data in the world folder (load, save on a timer, save on stop)
 *   deed.Deed               the Claim Deed item: claim, unclaim, live counter lore
 *   protect.Protection      blocks untrusted players by rank flag
 *   border.Borders          glowing claim outlines while holding the Deed
 *   commands.*              /claim, /trust, /untrust, /trustlist, /claims
 */
@Mod(value = ElementsClaims.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class ElementsClaims {

    public static final String MOD_ID = "elements_claims";
    public static final Logger LOGGER = LoggerFactory.getLogger("ElementsClaims");

    public ElementsClaims(IEventBus modEventBus, ModContainer container) {
        // config/elements_claims/features.toml (Master Control File)
        Features.register(container);
        LOGGER.info("[Claims] Elements: Claims loading.");
    }
}
