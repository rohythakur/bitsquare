/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade;

import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.messaging.DecryptedMailListener;
import io.bitsquare.p2p.messaging.DecryptedMsgWithPubKey;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.protocol.trade.messages.TradeMessage;
import io.bitsquare.trade.protocol.trade.tasks.shared.SetupPayoutTxLockTimeReachedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public abstract class TradeProtocol {
    private static final Logger log = LoggerFactory.getLogger(TradeProtocol.class);
    private static final long TIMEOUT_SEC = 30;

    protected final ProcessModel processModel;
    private final DecryptedMailListener decryptedMailListener;
    protected Trade trade;
    private java.util.Timer timeoutTimer;

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        decryptedMailListener = (decryptedMessageWithPubKey, peerAddress) -> {
            // We check the sig only as soon we have stored the peers pubKeyRing.
            PubKeyRing tradingPeerPubKeyRing = processModel.tradingPeer.getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.signaturePubKey;
            if (tradingPeerPubKeyRing != null && signaturePubKey.equals(tradingPeerPubKeyRing.getSignaturePubKey())) {
                Message message = decryptedMessageWithPubKey.message;
                log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + peerAddress);
                if (message instanceof TradeMessage) {
                    TradeMessage tradeMessage = (TradeMessage) message;
                    nonEmptyStringOf(tradeMessage.tradeId);

                    if (tradeMessage.tradeId.equals(processModel.getId())) {
                        doHandleDecryptedMessage(tradeMessage, peerAddress);
                    }
                }
            } else {
                //TODO not clear anymore what case is handled here
                // it might be that we received a msg from the arbitrator, we don't handle that here but we don't want to log an error
                /*Optional<Arbitrator> arbitratorOptional = processModel.getArbitratorManager().getArbitratorsObservableMap().values().stream()
                        .filter(e -> e.getArbitratorAddress().equals(trade.getArbitratorAddress())).findFirst();
                PubKeyRing arbitratorPubKeyRing = null;
                if (arbitratorOptional.isPresent())
                    arbitratorPubKeyRing = arbitratorOptional.get().getPubKeyRing();

                if ((arbitratorPubKeyRing != null && !signaturePubKey.equals(arbitratorPubKeyRing.getSignaturePubKey())))
                    log.error("Signature used in seal message does not match the one stored with that trade for the trading peer or arbitrator.");*/
            }
        };
        processModel.getP2PService().addDecryptedMailListener(decryptedMailListener);
    }

    public void completed() {
        cleanup();
    }

    private void cleanup() {
        log.debug("cleanup " + this);
        stopTimeout();

        processModel.getP2PService().removeDecryptedMailListener(decryptedMailListener);

    }

    public void applyMailboxMessage(DecryptedMsgWithPubKey decryptedMsgWithPubKey, Trade trade) {
        log.debug("applyMailboxMessage " + decryptedMsgWithPubKey.message);
        if (decryptedMsgWithPubKey.signaturePubKey.equals(processModel.tradingPeer.getPubKeyRing().getSignaturePubKey()))
            doApplyMailboxMessage(decryptedMsgWithPubKey.message, trade);
        else
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
    }

    protected abstract void doApplyMailboxMessage(Message message, Trade trade);

    protected abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress);

    public void checkPayoutTxTimeLock(Trade trade) {
        this.trade = trade;

        if (trade.getState() == Trade.State.PAYOUT_TX_COMMITTED) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> {
                        log.debug("taskRunner needPayoutTxBroadcast completed");
                        processModel.onComplete();
                    },
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(SetupPayoutTxLockTimeReachedListener.class);
            taskRunner.run();
        }
    }

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached");
            trade.setErrorMessage("A timeout occurred.");
            cleanupTradable();
            cleanup();
        }, TIMEOUT_SEC);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerSuccess(String info) {
        log.debug("handleTaskRunnerSuccess " + info);
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);
        cleanupTradable();
        cleanup();
    }

    private void cleanupTradable() {
        Trade.State tradeState = trade.getState();
        log.debug("cleanupTradable tradeState=" + tradeState);
        boolean isOffererTrade = trade instanceof OffererTrade;
        if (isOffererTrade && (tradeState == Trade.State.DEPOSIT_PUBLISH_REQUESTED || tradeState == Trade.State.DEPOSIT_SEEN_IN_NETWORK))
            processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());

        boolean isTakerTrade = trade instanceof TakerTrade;

        if (isTakerTrade) {
            TradeManager tradeManager = processModel.getTradeManager();
            if (tradeState.getPhase() == Trade.Phase.PREPARATION) {
                tradeManager.removePreparedTrade(trade);
            } else if (tradeState.getPhase() == Trade.Phase.TAKER_FEE_PAID) {
                tradeManager.addTradeToFailedTrades(trade);
            }
        }
    }
}
