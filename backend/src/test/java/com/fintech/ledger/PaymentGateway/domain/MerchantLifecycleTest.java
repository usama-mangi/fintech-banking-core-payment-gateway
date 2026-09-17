package com.fintech.ledger.PaymentGateway.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the merchant lifecycle transition table owned by
 * {@link Merchant#activate}, {@link Merchant#suspend},
 * {@link Merchant#reactivate} and {@link Merchant#close}.
 */
class MerchantLifecycleTest {

	private Merchant merchantIn(MerchantStatus status) {
		Merchant merchant = new Merchant("Lifecycle Co", "lifecycle@test", "sk_lifecycle");
		merchant.setStatus(status);
		return merchant;
	}

	@Test
	void activateIsLegalOnlyFromPending() {
		Merchant pending = merchantIn(MerchantStatus.PENDING);
		pending.activate();
		assertThat(pending.getStatus()).isEqualTo(MerchantStatus.ACTIVE);

		for (MerchantStatus status : MerchantStatus.values()) {
			if (status == MerchantStatus.PENDING) {
				continue;
			}
			Merchant merchant = merchantIn(status);
			assertThatThrownBy(merchant::activate)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("cannot activate");
		}
	}

	@Test
	void suspendIsLegalOnlyFromActive() {
		Merchant active = merchantIn(MerchantStatus.ACTIVE);
		active.suspend();
		assertThat(active.getStatus()).isEqualTo(MerchantStatus.SUSPENDED);

		for (MerchantStatus status : MerchantStatus.values()) {
			if (status == MerchantStatus.ACTIVE) {
				continue;
			}
			Merchant merchant = merchantIn(status);
			assertThatThrownBy(merchant::suspend)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("cannot suspend");
		}
	}

	@Test
	void reactivateIsLegalOnlyFromSuspended() {
		Merchant suspended = merchantIn(MerchantStatus.SUSPENDED);
		suspended.reactivate();
		assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.ACTIVE);

		for (MerchantStatus status : MerchantStatus.values()) {
			if (status == MerchantStatus.SUSPENDED) {
				continue;
			}
			Merchant merchant = merchantIn(status);
			assertThatThrownBy(merchant::reactivate)
					.isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("cannot reactivate");
		}
	}

	@Test
	void closeIsLegalFromEveryNonTerminalStatusAndClosedIsTerminal() {
		for (MerchantStatus status : MerchantStatus.values()) {
			Merchant merchant = merchantIn(status);
			if (status == MerchantStatus.CLOSED) {
				assertThatThrownBy(merchant::close)
						.isInstanceOf(IllegalStateException.class)
						.hasMessageContaining("cannot close");
			}
			else {
				merchant.close();
				assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
			}
		}
	}

	@Test
	void suspendedMerchantCanCloseButClosedCannotSuspend() {
		Merchant suspended = merchantIn(MerchantStatus.SUSPENDED);
		suspended.close();
		assertThat(suspended.getStatus()).isEqualTo(MerchantStatus.CLOSED);
		assertThatThrownBy(suspended::suspend)
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void fullCycleActiveSuspendedActiveClosedStaysTerminal() {
		Merchant merchant = merchantIn(MerchantStatus.ACTIVE);
		merchant.suspend();
		merchant.reactivate();
		merchant.suspend();
		merchant.close();
		assertThat(merchant.getStatus()).isEqualTo(MerchantStatus.CLOSED);
		assertThatThrownBy(merchant::reactivate).isInstanceOf(IllegalStateException.class);
	}
}
