/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.common.utils;

import com.google.protobuf.ByteString;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.crypto.Sha256Hash;
import org.tron.protos.Protocol.Transaction;

public class TransactionUtils {

  private static final Logger logger = LoggerFactory.getLogger("Transaction");

  /**
   * Obtain a data bytes after removing the id and SHA-256(data)
   *
   * @param transaction {@link Transaction} transaction
   * @return byte[] the hash of the transaction's data bytes which have no id
   */
  public static byte[] getHash(Transaction transaction) {
    Transaction.Builder tmp = transaction.toBuilder();
    //tmp.clearId();

    return Sha256Hash.hash(tmp.build().toByteArray());
  }

  public static byte[] getOwner(Transaction.Contract contract) {
    ByteString owner;
    try {
      switch (contract.getType()) {
        case AccountCreateContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.AccountCreateContract.class).getOwnerAddress();
          break;
        case TransferContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.TransferContract.class)
              .getOwnerAddress();
          break;
        case TransferAssetContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.TransferAssetContract.class).getOwnerAddress();
          break;
        case VoteAssetContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.VoteAssetContract.class)
              .getOwnerAddress();
          break;
        case VoteWitnessContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.VoteWitnessContract.class)
              .getOwnerAddress();
          break;
        case WitnessCreateContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.WitnessCreateContract.class).getOwnerAddress();
          break;
        case AssetIssueContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.AssetIssueContract.class)
              .getOwnerAddress();
          break;
        case ParticipateAssetIssueContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.ParticipateAssetIssueContract.class)
              .getOwnerAddress();
          break;
        case CreateSmartContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.CreateSmartContract.class)
              .getOwnerAddress();
          break;
        case TriggerSmartContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.TriggerSmartContract.class).getOwnerAddress();
          break;
        case FreezeBalanceContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.FreezeBalanceContract.class).getOwnerAddress();
          break;
        case UnfreezeBalanceContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.UnfreezeBalanceContract.class).getOwnerAddress();
          break;
        case UnfreezeAssetContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.UnfreezeAssetContract.class).getOwnerAddress();
          break;
        case WithdrawBalanceContract:
          owner = contract.getParameter()
              .unpack(org.tron.protos.Contract.WithdrawBalanceContract.class).getOwnerAddress();
          break;
        case UpdateAssetContract:
          owner = contract.getParameter().unpack(org.tron.protos.Contract.UpdateAssetContract.class)
              .getOwnerAddress();
          break;

        default:
          return null;
      }
      return owner.toByteArray();
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }

  public static String getBase64FromByteString(ByteString sign) {
    byte[] r = sign.substring(0, 32).toByteArray();
    byte[] s = sign.substring(32, 64).toByteArray();
    byte v = sign.byteAt(64);
    if (v < 27) {
      v += 27; //revId -> v
    }
    ECDSASignature signature = ECDSASignature.fromComponents(r, s, v);
    return signature.toBase64();
  }

  /*
   * 1. check hash
   * 2. check double spent
   * 3. check sign
   * 4. check balance
   */
  public static boolean validTransaction(Transaction signedTransaction) {
    assert (signedTransaction.getSignatureCount() ==
        signedTransaction.getRawData().getContractCount());
    List<Transaction.Contract> listContract = signedTransaction.getRawData().getContractList();
    byte[] hash = Sha256Hash.hash(signedTransaction.getRawData().toByteArray());
    int count = signedTransaction.getSignatureCount();
    if (count == 0) {
      return false;
    }
    for (int i = 0; i < count; ++i) {
      try {
        Transaction.Contract contract = listContract.get(i);
        byte[] owner = getOwner(contract);
        byte[] address = ECKey
            .signatureToAddress(hash, getBase64FromByteString(signedTransaction.getSignature(i)));
        if (!Arrays.equals(owner, address)) {
          return false;
        }
      } catch (SignatureException e) {
        e.printStackTrace();
        return false;
      }
    }
    return true;
  }

  public static Transaction sign(Transaction transaction, ECKey myKey, byte[] chainId, boolean isMainChain) {
    Transaction.Builder transactionBuilderSigned = transaction.toBuilder();
    byte[] hash = Sha256Hash.hash(transaction.getRawData().toByteArray());

    byte[] newHash;
    if(isMainChain) {
      newHash = hash;
    } else {
      byte[] hashWithChainId = Arrays.copyOf(hash, hash.length + chainId.length);
      System.arraycopy(chainId, 0, hashWithChainId, hash.length, chainId.length);
      newHash = Sha256Hash.hash(hashWithChainId);
    }

    ECDSASignature signature = myKey.sign(newHash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    transactionBuilderSigned.addSignature(bsSign);
    transaction = transactionBuilderSigned.build();
    return transaction;
  }

  public static Transaction setTimestamp(Transaction transaction) {
    long currentTime = System.currentTimeMillis();//*1000000 + System.nanoTime()%1000000;
    Transaction.Builder builder = transaction.toBuilder();
    org.tron.protos.Protocol.Transaction.raw.Builder rowBuilder = transaction.getRawData()
        .toBuilder();
    rowBuilder.setTimestamp(currentTime);
    builder.setRawData(rowBuilder.build());
    return builder.build();
  }

  public static Transaction setExpirationTime(Transaction transaction) {
    if (transaction.getSignatureCount() == 0) {
      long expirationTime = System.currentTimeMillis() + 6 * 60 * 60 * 1000;
      Transaction.Builder builder = transaction.toBuilder();
      org.tron.protos.Protocol.Transaction.raw.Builder rowBuilder = transaction.getRawData()
          .toBuilder();
      rowBuilder.setExpiration(expirationTime);
      builder.setRawData(rowBuilder.build());
      return builder.build();
    }
    return transaction;
  }

  public static Transaction setPermissionId(Transaction transaction) {
    if (transaction.getSignatureCount() != 0
        || transaction.getRawData().getContract(0).getPermissionId() != 0) {
      return transaction;
    }
    int permission_id = 0;
//    if (permission_id < 0) {
//      throw new CancelException("User cancelled");
//    }
    if (permission_id != 0) {
      Transaction.raw.Builder raw = transaction.getRawData().toBuilder();
      Transaction.Contract.Builder contract = raw.getContract(0).toBuilder()
          .setPermissionId(permission_id);
      raw.clearContract();
      raw.addContract(contract);
      transaction = transaction.toBuilder().setRawData(raw).build();
    }
    return transaction;
  }

//  private static int inputPermissionId() {
//    Scanner in = new Scanner(System.in);
//    while (true) {
//      String input = in.nextLine().trim();
//      String str = input.split("\\s+")[0];
//      if ("y".equalsIgnoreCase(str)) {
//        return 0;
//      }
//      try {
//        return Integer.parseInt(str);
//      } catch (Exception e) {
//        return -1;
//      }
//    }
//  }
}
