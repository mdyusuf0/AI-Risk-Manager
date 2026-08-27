import { Transaction, GroundTruthItem } from '../types/sentinel';

export class SimulationService {
  static generateScenario(
    presetName: string,
    options?: {
      numAccounts?: number;
      numSharedDevices?: number;
      numSharedIps?: number;
      amountRange?: [number, number];
      includeGroundTruth?: boolean;
    }
  ): { transactions: Transaction[]; groundTruth: GroundTruthItem[] } {
    const numAccs = options?.numAccounts || 10;
    const minAmt = options?.amountRange?.[0] || 10;
    const maxAmt = options?.amountRange?.[1] || 1000;
    const includeGt = options?.includeGroundTruth ?? true;

    const txns: Transaction[] = [];
    const gt: GroundTruthItem[] = [];

    const pseudoRandom = (seed: number) => {
      const x = Math.sin(seed++) * 10000;
      return x - Math.floor(x);
    };

    for (let i = 0; i < numAccs; i++) {
      const accId = `acc_sim_${i + 1}`;
      const amount = Number((minAmt + pseudoRandom(i * 13) * (maxAmt - minAmt)).toFixed(2));
      const txnId = `txn_sim_${i + 1}_${Math.random().toString(36).substring(2, 7)}`;

      let deviceId: string | null = `dev_clean_${i + 1}`;
      let ip: string | null = `192.168.1.${10 + i}`;
      let bankRef: string | null = `bank_acc_${1000 + i}`;
      let isFraud = false;

      if (presetName === 'Shared Device Ring') {
        if (i < 5) {
          deviceId = 'dev_shared_ring_alpha';
          isFraud = true;
        }
      } else if (presetName === 'Multi-Signal Ring') {
        if (i < 4) {
          deviceId = 'dev_multi_signal';
          bankRef = 'bank_ref_shared_multi';
          isFraud = true;
        }
      } else if (presetName === 'IP-Only Cluster') {
        if (i < 6) {
          ip = '10.0.0.1'; // Public shared IP (e.g. coffee shop)
          isFraud = false;
        }
      } else if (presetName === 'Mixed Scenario') {
        if (i < 3) {
          deviceId = 'dev_mixed_ring';
          bankRef = 'bank_mixed_ring';
          isFraud = true;
        } else if (i < 7) {
          ip = '172.16.0.1';
        }
      } else if (presetName === 'Custom Sandbox') {
        const sharedDevices = options?.numSharedDevices || 0;
        const sharedIps = options?.numSharedIps || 0;
        
        if (sharedDevices > 0 && i < sharedDevices * 2) {
          deviceId = `dev_shared_custom_${i % sharedDevices}`;
          isFraud = true;
        }
        if (sharedIps > 0 && i < sharedIps * 2) {
          ip = `10.10.0.${i % sharedIps}`;
        }
      }

      txns.push({
        id: txnId,
        account_id: accId,
        amount,
        device_id: deviceId,
        ip,
        bank_ref: bankRef,
        timestamp: new Date(Date.now() - i * 3600 * 1000).toISOString(),
        is_fraud: isFraud,
      });

      if (includeGt && isFraud) {
        gt.push({ account_id: accId, is_fraud: true });
      }
    }

    return { transactions: txns, groundTruth: gt };
  }
}
