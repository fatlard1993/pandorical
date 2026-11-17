package justfatlard.pandorical.loan;

import justfatlard.pandorical.Pandorical;
import justfatlard.pandorical.api.LoanApi;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Loans implements LoanApi {
	public static final Loans INSTANCE = new Loans();

	private Loans() {}

	private final List<Lender> lenders = new CopyOnWriteArrayList<>();
	private final Set<Lender> failed = ConcurrentHashMap.newKeySet();

	@Override
	public void register(Lender lender) {
		lenders.add(Objects.requireNonNull(lender, "lender"));
	}

	@Override
	public @Nullable Component onLoan(ServerPlayer player) {
		for (Lender lender : lenders) {
			Component why;
			try {
				why = lender.lent(player);
			} catch (RuntimeException e) {
				if (failed.add(lender)) Pandorical.LOGGER.error("A loan lender failed on {}; it is skipped wherever it fails", player.getName().getString(), e);
				continue;
			}
			if (why != null) return why;
		}
		return null;
	}
}
