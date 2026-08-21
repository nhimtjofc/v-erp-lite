package base.l2.settlement.dispute.domain;

import base.l0.system.money.Money;

import java.util.UUID;

/**
 * How much of a lost dispute one counterparty carries.
 *
 * <p>{@code ownerType} is passed through rather than assumed so the debit lands on the same
 * sub-ledger the original capture credited. Getting it wrong does not fail — it opens a second
 * account for the same party and splits their balance in two, which is worse.
 *
 * @param creditLimitMinor how far this party may go into debit. A lost dispute is precisely the
 *                         event that pushes a counterparty from "we owe them" to "they owe us", so
 *                         a zero limit here means the posting is refused by the balance guard and
 *                         the loss has to be written off instead. That is a policy decision, and
 *                         making it explicit at the call site is the point.
 */
public record DisputeShare(String ownerType, UUID partyId, Money amount, long creditLimitMinor) {
}

