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

package io.bitsquare.user;

public class PopupId {

    // We don't use an enum because it would break updates if we add a new item in a new version

    public static final String TRADE_WALLET = "tradeWallet";
    public static final String SEND_PAYMENT_INFO = "sendPaymentInfo";
    public static final String PAYMENT_SENT = "paymentSent";
    public static final String PAYMENT_RECEIVED = "paymentReceived";

}
